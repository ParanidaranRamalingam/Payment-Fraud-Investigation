package com.capstone.fraudcase.tools;

import com.capstone.fraudcase.domain.EvidenceItem;
import com.capstone.fraudcase.domain.EvidenceStatus;
import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.SensitiveDataGuard;
import com.capstone.fraudcase.domain.ToolTraceStatus;
import com.capstone.fraudcase.tools.model.EvidencePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The only path through which evidence tools run. Enforces the allow-list and a per-call time budget and turns
 * every outcome (success, not found, timeout, unavailable, unexpected error, invalid result) into a structured
 * {@link ToolOutcome}. It never throws for a tool failure and never fabricates data for a failed call.
 */
@Component
public class ToolInvoker implements AutoCloseable {

    /** Evidence tools that may be invoked. Policy retrieval is served by the knowledge layer, not this path. */
    public static final Set<EvidenceTool> ALLOW_LIST = Collections.unmodifiableSet(EnumSet.of(
            EvidenceTool.TRANSACTION_DETAILS, EvidenceTool.DEVICE_SIGNALS, EvidenceTool.RECENT_LOCATIONS,
            EvidenceTool.MERCHANT_RISK_PROFILE, EvidenceTool.PRIOR_DISPUTES, EvidenceTool.CASE_SLA));

    private static final Logger log = LoggerFactory.getLogger(ToolInvoker.class);

    private final Map<EvidenceTool, EvidenceToolProvider> providers;
    private final Duration timeout;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ToolInvoker(List<EvidenceToolProvider> providers, ToolProperties properties, Clock clock) {
        Map<EvidenceTool, EvidenceToolProvider> registry = new EnumMap<>(EvidenceTool.class);
        for (EvidenceToolProvider provider : providers) {
            EvidenceTool tool = provider.tool();
            if (!ALLOW_LIST.contains(tool)) {
                throw new IllegalStateException("tool is not allow-listed: " + tool);
            }
            if (registry.putIfAbsent(tool, provider) != null) {
                throw new IllegalStateException("duplicate provider for tool: " + tool);
            }
            if (provider.source() == null || provider.source().isBlank()
                    || provider.source().length() > EvidenceItem.MAX_SOURCE_LENGTH) {
                throw new IllegalStateException("provider for " + tool + " must declare a source of at most "
                        + EvidenceItem.MAX_SOURCE_LENGTH + " characters");
            }
        }
        this.providers = Collections.unmodifiableMap(registry);
        this.timeout = properties.timeout();
        this.clock = clock;
    }

    public Set<EvidenceTool> registeredTools() {
        return providers.keySet();
    }

    public ToolOutcome invoke(EvidenceTool tool, ToolRequest request) {
        Objects.requireNonNull(tool, "tool");
        Instant startedAt = clock.instant();
        String requestSummary = SensitiveDataGuard.redact(request.redactedSummary());
        EvidenceToolProvider provider = providers.get(tool);
        if (provider == null) {
            return failure(tool, null, ToolFailure.REJECTED, null, requestSummary, startedAt);
        }
        String source = provider.source();
        Future<EvidencePayload> future;
        try {
            future = executor.submit(() -> provider.fetch(request));
        } catch (RejectedExecutionException e) {
            return failure(tool, source, ToolFailure.UNAVAILABLE, null, requestSummary, startedAt);
        }
        try {
            EvidencePayload payload = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!honoursContract(tool, payload)) {
                log.warn("Tool {} returned a result that violates its contract", tool.toolId());
                return failure(tool, source, ToolFailure.CONTRACT_VIOLATION, null, requestSummary, startedAt);
            }
            return new ToolOutcome(tool, source, ToolTraceStatus.SUCCESS, EvidenceStatus.AVAILABLE,
                    payload, null, payload.summary(), requestSummary, startedAt, clock.instant());
        } catch (TimeoutException e) {
            future.cancel(true);
            return failure(tool, source, ToolFailure.TIMEOUT, "timed out after " + timeout.toMillis() + " ms",
                    requestSummary, startedAt);
        } catch (ExecutionException e) {
            ToolFailure failure = e.getCause() instanceof ToolException te ? te.failure() : ToolFailure.ERROR;
            if (failure == ToolFailure.ERROR) {
                log.warn("Tool {} failed unexpectedly ({})", tool.toolId(), e.getCause().getClass().getSimpleName());
            }
            return failure(tool, source, failure, null, requestSummary, startedAt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return failure(tool, source, ToolFailure.UNAVAILABLE, null, requestSummary, startedAt);
        }
    }

    private static boolean honoursContract(EvidenceTool tool, EvidencePayload payload) {
        if (payload == null || payload.tool() != tool) {
            return false;
        }
        String summary = payload.summary();
        return summary != null && !summary.isBlank() && summary.length() <= EvidenceItem.MAX_SUMMARY_LENGTH
                && SensitiveDataGuard.isSafeFreeText(summary)
                && payload.canonicalForm().length() <= EvidenceItem.MAX_PAYLOAD_LENGTH;
    }

    private ToolOutcome failure(EvidenceTool tool, String source, ToolFailure failure, String detail,
                                String requestSummary, Instant startedAt) {
        Instant completedAt = clock.instant();
        String summary = "%s returned no evidence: %s (%s). No data was inferred."
                .formatted(tool.toolId(), detail == null ? failure.description() : detail, failure.evidenceStatus());
        return new ToolOutcome(tool, source, failure.traceStatus(), failure.evidenceStatus(), null,
                failure.errorCode(), summary, requestSummary, startedAt, completedAt);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
