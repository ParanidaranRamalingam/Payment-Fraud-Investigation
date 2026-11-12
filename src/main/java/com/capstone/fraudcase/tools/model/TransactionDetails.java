package com.capstone.fraudcase.tools.model;

import com.capstone.fraudcase.domain.EvidenceTool;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static com.capstone.fraudcase.tools.model.Attributes.range;
import static com.capstone.fraudcase.tools.model.Attributes.require;

/** Result of {@code getTransactionDetails}: the synthetic ledger view of the disputed transaction. */
public record TransactionDetails(
        String transactionReference,
        PostingStatus postingStatus,
        PaymentChannel channel,
        AuthenticationMethod authentication,
        BigDecimal amount,
        String currency,
        String merchantName,
        String merchantCountry,
        Instant postedAt,
        String fingerprint,
        int matchingPostings) implements EvidencePayload {

    public enum PostingStatus { POSTED, PENDING }

    public enum PaymentChannel { CARD_NOT_PRESENT, CHIP, CONTACTLESS }

    public enum AuthenticationMethod { THREE_DS, PIN, NONE }

    public TransactionDetails {
        require(transactionReference, "transactionReference");
        require(postingStatus, "postingStatus");
        require(channel, "channel");
        require(authentication, "authentication");
        require(amount, "amount");
        require(currency, "currency");
        require(merchantName, "merchantName");
        require(merchantCountry, "merchantCountry");
        require(postedAt, "postedAt");
        require(fingerprint, "fingerprint");
        range(matchingPostings, 1, 50, "matchingPostings");
    }

    public boolean duplicatePosting() {
        return matchingPostings > 1;
    }

    public boolean strongAuthentication() {
        return authentication != AuthenticationMethod.NONE;
    }

    @Override
    public EvidenceTool tool() {
        return EvidenceTool.TRANSACTION_DETAILS;
    }

    @Override
    public String summary() {
        return "Transaction %s: %s %s at %s (%s), %s via %s with %s authentication; %d posting(s) share fingerprint %s."
                .formatted(transactionReference, amount.toPlainString(), currency, merchantName, merchantCountry,
                        postingStatus, channel, authentication, matchingPostings, fingerprint);
    }

    @Override
    public Map<String, String> attributes() {
        return Attributes.of("transactionReference", transactionReference, "postingStatus", postingStatus,
                "channel", channel, "authentication", authentication, "amount", amount.toPlainString(),
                "currency", currency, "merchantName", merchantName, "merchantCountry", merchantCountry,
                "postedAt", postedAt, "fingerprint", fingerprint, "matchingPostings", matchingPostings);
    }
}
