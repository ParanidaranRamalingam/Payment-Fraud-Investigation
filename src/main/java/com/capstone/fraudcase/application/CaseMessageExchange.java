package com.capstone.fraudcase.application;

import com.capstone.fraudcase.domain.CaseMessage;

/**
 * An analyst message and the assistant reply that answers it.
 *
 * @param fallbackUsed true when the assistant failed or its reply was rejected and the fixed safe reply was stored
 */
public record CaseMessageExchange(CaseMessage message, CaseMessage reply, boolean fallbackUsed) {
}
