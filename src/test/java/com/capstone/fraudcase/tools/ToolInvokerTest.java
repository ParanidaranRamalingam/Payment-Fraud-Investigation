package com.capstone.fraudcase.tools;

import com.capstone.fraudcase.domain.ClaimType;
import com.capstone.fraudcase.domain.EvidenceStatus;
import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.ToolTraceStatus;
import com.capstone.fraudcase.support.SyntheticCases;
import com.capstone.fraudcase.tools.model.EvidencePayload;
import com.capstone.fraudcase.tools.model.PriorDisputes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolInvokerTest {

    private static final ToolRequest REQUEST = SyntheticCases.request("txn_000123", ClaimType.UNRECOGNISED_TRANSACTION);
    private ToolInvoker invoker;

    @AfterEach
    void close() {
        if (invoker != null) {
            invoker.close();
        }
    }

    private static EvidenceToolProvider provider(EvidenceTool tool, Function<ToolRequest, EvidencePayload> fetch) {
        return new EvidenceToolProvider() {
            @Override
            public EvidenceTool tool() {
                return tool;
            }

            @Override
            public String source() {
                return "test-source:v1";
            }

            @Override
            public EvidencePayload fetch(ToolRequest request) {
                return fetch.apply(request);
            }
        };
    }

    private ToolInvoker invokerWith(Duration timeout, EvidenceToolProvider... providers) {
        invoker = new ToolInvoker(List.of(providers), ToolProperties.withTimeout(timeout), SyntheticCases.FIXED_CLOCK);
        return invoker;
    }

    private ToolOutcome invokeDisputes(Function<ToolRequest, EvidencePayload> fetch) {
        return invokerWith(Duration.ofSeconds(2), provider(EvidenceTool.PRIOR_DISPUTES, fetch))
                .invoke(EvidenceTool.PRIOR_DISPUTES, REQUEST);
    }

    @Test
    void invoke_success_returnsAvailablePayloadWithProvenance() {
        PriorDisputes disputes = new PriorDisputes(1, 0, 1, 0);

        ToolOutcome outcome = invokeDisputes(r -> disputes);

        assertThat(outcome.traceStatus()).isEqualTo(ToolTraceStatus.SUCCESS);
        assertThat(outcome.evidenceStatus()).isEqualTo(EvidenceStatus.AVAILABLE);
        assertThat(outcome.payload()).isEqualTo(disputes);
        assertThat(outcome.errorCode()).isNull();
        assertThat(outcome.source()).isEqualTo("test-source:v1");
        assertThat(outcome.summary()).isEqualTo(disputes.summary());
        assertThat(outcome.requestSummary()).contains("txn_000123").contains("****4242");
        assertThat(outcome.startedAt()).isEqualTo(SyntheticCases.AS_OF);
    }

    @ParameterizedTest
    @CsvSource({
            "NOT_FOUND, NOT_FOUND, NOT_FOUND, NOT_FOUND",
            "TIMEOUT, TIMEOUT, TIMEOUT, TOOL_TIMEOUT",
            "UNAVAILABLE, ERROR, UNAVAILABLE, TOOL_UNAVAILABLE"})
    void invoke_structuredToolFailure_mapsToTraceAndEvidenceStatus(ToolFailure failure, ToolTraceStatus trace,
                                                                  EvidenceStatus evidence, String errorCode) {
        ToolOutcome outcome = invokeDisputes(r -> {
            throw new ToolException(failure, "simulated");
        });

        assertThat(outcome.traceStatus()).isEqualTo(trace);
        assertThat(outcome.evidenceStatus()).isEqualTo(evidence);
        assertThat(outcome.errorCode()).isEqualTo(errorCode);
        assertThat(outcome.payload()).isNull();
        assertThat(outcome.summary()).startsWith("getPriorDisputes returned no evidence").contains("No data was inferred");
    }

    @Test
    void invoke_slowTool_timesOutAndIsCancelled() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        CountDownLatch finished = new CountDownLatch(1);
        ToolInvoker slow = invokerWith(Duration.ofMillis(100), provider(EvidenceTool.DEVICE_SIGNALS, r -> {
            started.countDown();
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
            } finally {
                finished.countDown();
            }
            return null;
        }));

        ToolOutcome outcome = slow.invoke(EvidenceTool.DEVICE_SIGNALS, REQUEST);

        assertThat(outcome.traceStatus()).isEqualTo(ToolTraceStatus.TIMEOUT);
        assertThat(outcome.evidenceStatus()).isEqualTo(EvidenceStatus.TIMEOUT);
        assertThat(outcome.errorCode()).isEqualTo("TOOL_TIMEOUT");
        assertThat(outcome.summary()).contains("timed out after 100 ms");
        assertThat(started.getCount()).isZero();
        assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(interrupted).isTrue();
    }

    @Test
    void invoke_unexpectedException_returnsErrorWithoutLeakingMessage() {
        ToolOutcome outcome = invokeDisputes(r -> {
            throw new IllegalStateException("secret internal detail 4111 1111 1111 1111");
        });

        assertThat(outcome.traceStatus()).isEqualTo(ToolTraceStatus.ERROR);
        assertThat(outcome.evidenceStatus()).isEqualTo(EvidenceStatus.UNAVAILABLE);
        assertThat(outcome.errorCode()).isEqualTo("TOOL_ERROR");
        assertThat(outcome.summary()).doesNotContain("secret").doesNotContain("4111");
    }

    @Test
    void invoke_nullPayload_isContractViolation() {
        ToolOutcome outcome = invokeDisputes(r -> null);

        assertThat(outcome.errorCode()).isEqualTo("TOOL_CONTRACT_VIOLATION");
        assertThat(outcome.evidenceStatus()).isEqualTo(EvidenceStatus.UNAVAILABLE);
    }

    @Test
    void invoke_payloadOfAnotherTool_isContractViolation() {
        EvidencePayload wrong = SyntheticCases.providers().getFirst().fetch(REQUEST);

        ToolOutcome outcome = invokeDisputes(r -> wrong);

        assertThat(outcome.errorCode()).isEqualTo("TOOL_CONTRACT_VIOLATION");
        assertThat(outcome.payload()).isNull();
    }

    @Test
    void invoke_payloadWithUnmaskedNumberInSummary_isContractViolation() {
        PriorDisputes unsafe = mock(PriorDisputes.class);
        when(unsafe.tool()).thenReturn(EvidenceTool.PRIOR_DISPUTES);
        when(unsafe.summary()).thenReturn("card 4111111111111111 disputed");

        ToolOutcome outcome = invokeDisputes(r -> unsafe);

        assertThat(outcome.errorCode()).isEqualTo("TOOL_CONTRACT_VIOLATION");
    }

    @Test
    void invoke_unregisteredTool_isRejectedWithoutCallingAnyProvider() {
        EvidenceToolProvider registered = mock(EvidenceToolProvider.class);
        when(registered.tool()).thenReturn(EvidenceTool.CASE_SLA);
        when(registered.source()).thenReturn("x:v1");
        ToolInvoker only = invokerWith(Duration.ofSeconds(1), registered);

        ToolOutcome outcome = only.invoke(EvidenceTool.POLICY_RETRIEVAL, REQUEST);

        assertThat(outcome.traceStatus()).isEqualTo(ToolTraceStatus.REJECTED);
        assertThat(outcome.errorCode()).isEqualTo("TOOL_NOT_ALLOWED");
        assertThat(outcome.evidenceStatus()).isEqualTo(EvidenceStatus.UNAVAILABLE);
    }

    @Test
    void constructor_providerForNonAllowListedTool_rejected() {
        assertThatThrownBy(() -> invokerWith(Duration.ofSeconds(1),
                provider(EvidenceTool.POLICY_RETRIEVAL, r -> null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("allow-listed");
    }

    @Test
    void constructor_duplicateProvider_rejected() {
        assertThatThrownBy(() -> invokerWith(Duration.ofSeconds(1),
                provider(EvidenceTool.CASE_SLA, r -> null), provider(EvidenceTool.CASE_SLA, r -> null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("duplicate");
    }

    @Test
    void constructor_providerWithoutSource_rejected() {
        EvidenceToolProvider noSource = mock(EvidenceToolProvider.class);
        when(noSource.tool()).thenReturn(EvidenceTool.CASE_SLA);

        assertThatThrownBy(() -> invokerWith(Duration.ofSeconds(1), noSource)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invoke_nullTool_throws() {
        EvidenceToolProvider unused = mock(EvidenceToolProvider.class);
        when(unused.tool()).thenReturn(EvidenceTool.CASE_SLA);
        when(unused.source()).thenReturn("x:v1");
        ToolInvoker only = invokerWith(Duration.ofSeconds(1), unused);

        assertThatThrownBy(() -> only.invoke(null, REQUEST)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void registeredTools_matchesProviders() {
        invoker = SyntheticCases.invoker();

        assertThat(invoker.registeredTools()).containsExactlyInAnyOrderElementsOf(ToolInvoker.ALLOW_LIST);
    }

    @Test
    void properties_nonPositiveTimeout_rejected() {
        assertThatThrownBy(() -> ToolProperties.withTimeout(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "UPPER", "has space", "a-seed-that-is-far-too-long-for-the-limit"})
    void properties_invalidFixtureSeed_rejected(String seed) {
        assertThatThrownBy(() -> new ToolProperties(Duration.ofSeconds(1), seed))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void properties_defaults_matchDocumentedBaseline() {
        assertThat(ToolProperties.withTimeout(Duration.ofMillis(2500)).fixtureSeed()).isEqualTo("fraud-training-v1");
    }

    @Test
    void outcome_successWithoutPayload_cannotBeConstructed() {
        assertThatThrownBy(() -> new ToolOutcome(EvidenceTool.CASE_SLA, "s", ToolTraceStatus.SUCCESS,
                EvidenceStatus.AVAILABLE, null, null, "summary", null, SyntheticCases.AS_OF, SyntheticCases.AS_OF))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invoke_unregisteredTool_neverCallsRegisteredProvider() {
        EvidenceToolProvider sla = mock(EvidenceToolProvider.class);
        when(sla.tool()).thenReturn(EvidenceTool.CASE_SLA);
        when(sla.source()).thenReturn("x:v1");

        invokerWith(Duration.ofSeconds(1), sla).invoke(EvidenceTool.DEVICE_SIGNALS, REQUEST);

        verify(sla, never()).fetch(any());
    }
}
