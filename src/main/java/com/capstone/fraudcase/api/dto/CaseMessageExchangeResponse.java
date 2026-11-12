package com.capstone.fraudcase.api.dto;

/** The stored analyst message and the advisory reply; the reply never implies that any action was taken. */
public record CaseMessageExchangeResponse(
        CaseMessageResponse message,
        CaseMessageResponse reply,
        boolean fallbackUsed,
        boolean humanReviewRequired) {
}
