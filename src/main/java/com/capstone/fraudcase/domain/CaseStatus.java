package com.capstone.fraudcase.domain;

public enum CaseStatus {
    NEW,
    ENRICHING,
    READY_FOR_REVIEW,
    INCOMPLETE,
    UNDER_REVIEW,
    ROUTED,
    CLOSED;

    public boolean isTerminal() {
        return this == CLOSED;
    }
}
