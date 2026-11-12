package com.capstone.fraudcase.tools.synthetic;

import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.tools.EvidenceToolProvider;
import com.capstone.fraudcase.tools.ToolRequest;
import com.capstone.fraudcase.tools.model.EvidencePayload;

import java.util.Objects;
import java.util.function.Function;

/** Adapts one {@link SyntheticFixtureGenerator} method to the {@link EvidenceToolProvider} contract. */
public final class SyntheticEvidenceTool implements EvidenceToolProvider {

    private final EvidenceTool tool;
    private final String source;
    private final Function<ToolRequest, ? extends EvidencePayload> fetcher;

    public SyntheticEvidenceTool(EvidenceTool tool, String source,
                                 Function<ToolRequest, ? extends EvidencePayload> fetcher) {
        this.tool = Objects.requireNonNull(tool, "tool");
        this.source = Objects.requireNonNull(source, "source");
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
    }

    @Override
    public EvidenceTool tool() {
        return tool;
    }

    @Override
    public String source() {
        return source;
    }

    @Override
    public EvidencePayload fetch(ToolRequest request) {
        return fetcher.apply(request);
    }

    @Override
    public String toString() {
        return tool.toolId();
    }
}
