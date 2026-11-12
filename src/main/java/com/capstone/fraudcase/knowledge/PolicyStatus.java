package com.capstone.fraudcase.knowledge;

/** Lifecycle state of a policy document version. Only {@link #APPROVED} versions are retrievable. */
public enum PolicyStatus {
    DRAFT,
    APPROVED,
    RETIRED
}
