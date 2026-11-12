package com.capstone.fraudcase.knowledge;

import java.time.LocalDate;

/** Version-level metadata of a stored policy document, used to pick the effective version at retrieval. */
public record PolicyVersionInfo(String documentId, String version, LocalDate effectiveDate,
                                PolicyStatus status) {
}
