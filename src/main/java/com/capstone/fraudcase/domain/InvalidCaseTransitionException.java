package com.capstone.fraudcase.domain;

public class InvalidCaseTransitionException extends RuntimeException {

    private final CaseStatus from;
    private final CaseStatus to;

    public InvalidCaseTransitionException(CaseStatus from, CaseStatus to) {
        super("Case transition " + from + " -> " + to + " is not permitted");
        this.from = from;
        this.to = to;
    }

    public CaseStatus from() {
        return from;
    }

    public CaseStatus to() {
        return to;
    }
}
