package com.capstone.fraudcase.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.capstone.fraudcase.domain.CaseStatus.CLOSED;
import static com.capstone.fraudcase.domain.CaseStatus.ENRICHING;
import static com.capstone.fraudcase.domain.CaseStatus.INCOMPLETE;
import static com.capstone.fraudcase.domain.CaseStatus.NEW;
import static com.capstone.fraudcase.domain.CaseStatus.READY_FOR_REVIEW;
import static com.capstone.fraudcase.domain.CaseStatus.ROUTED;
import static com.capstone.fraudcase.domain.CaseStatus.UNDER_REVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaseStateMachineTest {

    private static final Map<CaseStatus, Set<CaseStatus>> EXPECTED = Map.of(
            NEW, Set.of(ENRICHING),
            ENRICHING, Set.of(READY_FOR_REVIEW, INCOMPLETE),
            READY_FOR_REVIEW, Set.of(UNDER_REVIEW),
            INCOMPLETE, Set.of(UNDER_REVIEW, ENRICHING, CLOSED),
            UNDER_REVIEW, Set.of(ROUTED, CLOSED),
            ROUTED, Set.of(CLOSED),
            CLOSED, Set.of());

    static Stream<Arguments> everyStatusPair() {
        return Arrays.stream(CaseStatus.values())
                .flatMap(from -> Arrays.stream(CaseStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyStatusPair")
    void canTransition_everyPair_matchesSpecifiedLifecycle(CaseStatus from, CaseStatus to) {
        assertThat(CaseStateMachine.canTransition(from, to)).isEqualTo(EXPECTED.get(from).contains(to));
    }

    @ParameterizedTest
    @EnumSource(CaseStatus.class)
    void allowedTargets_eachStatus_matchesSpecifiedLifecycle(CaseStatus from) {
        assertThat(CaseStateMachine.allowedTargets(from)).containsExactlyInAnyOrderElementsOf(EXPECTED.get(from));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"NEW,ENRICHING", "ENRICHING,INCOMPLETE", "INCOMPLETE,ENRICHING", "UNDER_REVIEW,ROUTED", "ROUTED,CLOSED"})
    void requireTransition_allowed_returnsTarget(CaseStatus from, CaseStatus to) {
        assertThat(CaseStateMachine.requireTransition(from, to)).isEqualTo(to);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"NEW,READY_FOR_REVIEW", "NEW,CLOSED", "ENRICHING,ROUTED", "READY_FOR_REVIEW,ROUTED",
            "UNDER_REVIEW,ENRICHING", "ROUTED,UNDER_REVIEW", "CLOSED,NEW", "NEW,NEW"})
    void requireTransition_forbidden_throwsWithBothStates(CaseStatus from, CaseStatus to) {
        assertThatThrownBy(() -> CaseStateMachine.requireTransition(from, to))
                .isInstanceOfSatisfying(InvalidCaseTransitionException.class, e -> {
                    assertThat(e.from()).isEqualTo(from);
                    assertThat(e.to()).isEqualTo(to);
                })
                .hasMessageContaining(from + " -> " + to);
    }

    @Test
    void canTransition_nullTarget_returnsFalse() {
        assertThat(CaseStateMachine.canTransition(NEW, null)).isFalse();
    }

    @Test
    void allowedTargets_nullSource_throwsNullPointer() {
        assertThatNullPointerException().isThrownBy(() -> CaseStateMachine.allowedTargets(null));
    }

    @Test
    void allowedTargets_returnedSet_isUnmodifiable() {
        assertThatThrownBy(() -> CaseStateMachine.allowedTargets(NEW).add(CLOSED))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void isTerminal_onlyClosed_returnsTrue() {
        assertThat(Arrays.stream(CaseStatus.values()).filter(CaseStatus::isTerminal)).containsExactly(CLOSED);
    }
}
