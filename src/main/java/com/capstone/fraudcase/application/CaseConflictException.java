package com.capstone.fraudcase.application;

/** The request is well-formed but conflicts with the case's current state (e.g. closed case, stale recommendation). */
public class CaseConflictException extends RuntimeException {

    public CaseConflictException(String message) {
        super(message);
    }
}
