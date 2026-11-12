package com.capstone.fraudcase.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.capstone.fraudcase.domain.CaseStatus.CLOSED;
import static com.capstone.fraudcase.domain.CaseStatus.ENRICHING;
import static com.capstone.fraudcase.domain.CaseStatus.INCOMPLETE;
import static com.capstone.fraudcase.domain.CaseStatus.NEW;
import static com.capstone.fraudcase.domain.CaseStatus.READY_FOR_REVIEW;
import static com.capstone.fraudcase.domain.CaseStatus.ROUTED;
import static com.capstone.fraudcase.domain.CaseStatus.UNDER_REVIEW;

/**
 * Deterministic case lifecycle. Transitions are decided here, never by model output.
 *
 * <pre>
 * NEW -> ENRICHING -> READY_FOR_REVIEW | INCOMPLETE
 * READY_FOR_REVIEW | INCOMPLETE -> UNDER_REVIEW -> ROUTED | CLOSED
 * INCOMPLETE -> ENRICHING | CLOSED
 * ROUTED -> CLOSED
 * </pre>
 */
public final class CaseStateMachine {

    private static final Map<CaseStatus, Set<CaseStatus>> TRANSITIONS = buildTransitions();

    private CaseStateMachine() {
    }

    private static Map<CaseStatus, Set<CaseStatus>> buildTransitions() {
        Map<CaseStatus, Set<CaseStatus>> map = new EnumMap<>(CaseStatus.class);
        map.put(NEW, EnumSet.of(ENRICHING));
        map.put(ENRICHING, EnumSet.of(READY_FOR_REVIEW, INCOMPLETE));
        map.put(READY_FOR_REVIEW, EnumSet.of(UNDER_REVIEW));
        map.put(INCOMPLETE, EnumSet.of(UNDER_REVIEW, ENRICHING, CLOSED));
        map.put(UNDER_REVIEW, EnumSet.of(ROUTED, CLOSED));
        map.put(ROUTED, EnumSet.of(CLOSED));
        map.put(CLOSED, EnumSet.noneOf(CaseStatus.class));
        map.replaceAll((status, targets) -> Collections.unmodifiableSet(targets));
        return Collections.unmodifiableMap(map);
    }

    public static Set<CaseStatus> allowedTargets(CaseStatus from) {
        return TRANSITIONS.get(Objects.requireNonNull(from, "from"));
    }

    public static boolean canTransition(CaseStatus from, CaseStatus to) {
        return to != null && allowedTargets(from).contains(to);
    }

    public static CaseStatus requireTransition(CaseStatus from, CaseStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidCaseTransitionException(from, to);
        }
        return to;
    }
}
