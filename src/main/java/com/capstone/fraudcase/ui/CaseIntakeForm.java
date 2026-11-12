package com.capstone.fraudcase.ui;

import com.capstone.fraudcase.api.validation.IsoCurrency;
import com.capstone.fraudcase.api.validation.MaskedReference;
import com.capstone.fraudcase.api.validation.SafeText;
import com.capstone.fraudcase.application.NewCase;
import com.capstone.fraudcase.domain.ClaimType;
import com.capstone.fraudcase.domain.TransactionClaim;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Currency;

/**
 * Intake form backing bean. Mirrors the API contract: masked/tokenized references only, positive amount,
 * ISO currency, sanitized statement and the two mandatory confirmations. IDs are never accepted.
 */
public class CaseIntakeForm {

    // Customer
    @NotBlank @MaskedReference
    private String customerReference;

    @MaskedReference
    private String cardReference;

    // Transaction
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{6,64}$",
            message = "must be 6-64 letters, digits, '_' or '-'") @SafeText
    private String transactionReference;

    @NotBlank @Size(max = 120) @SafeText
    private String merchantName;

    @Pattern(regexp = "^\\d{4}$", message = "must be a 4-digit merchant category code")
    private String merchantCategoryCode;

    @NotNull @Positive @Digits(integer = 17, fraction = 2)
    private BigDecimal amount;

    @NotBlank @IsoCurrency
    private String currency;

    @NotNull @PastOrPresent @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime transactionAt;

    // Claim
    @NotNull
    private ClaimType claimType;

    @NotBlank @Size(max = 2000) @SafeText
    private String customerStatement;

    @NotNull @AssertTrue(message = "must be confirmed: only synthetic data may be submitted")
    private Boolean syntheticDataConfirmed = false;

    @NotNull @AssertTrue(message = "must be confirmed: every case requires human review")
    private Boolean reviewRequired = false;

    public NewCase toNewCase() {
        TransactionClaim claim = new TransactionClaim(transactionReference, merchantName,
                merchantCategoryCode == null || merchantCategoryCode.isBlank() ? null : merchantCategoryCode,
                amount, Currency.getInstance(currency), transactionAt.atOffset(ZoneOffset.UTC).toInstant(),
                claimType);
        return new NewCase(customerReference, cardReference, claim, customerStatement);
    }

    public String getCustomerReference() {
        return customerReference;
    }

    public void setCustomerReference(String customerReference) {
        this.customerReference = customerReference;
    }

    public String getCardReference() {
        return cardReference;
    }

    public void setCardReference(String cardReference) {
        this.cardReference = cardReference;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public String getMerchantCategoryCode() {
        return merchantCategoryCode;
    }

    public void setMerchantCategoryCode(String merchantCategoryCode) {
        this.merchantCategoryCode = merchantCategoryCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDateTime getTransactionAt() {
        return transactionAt;
    }

    public void setTransactionAt(LocalDateTime transactionAt) {
        this.transactionAt = transactionAt;
    }

    public ClaimType getClaimType() {
        return claimType;
    }

    public void setClaimType(ClaimType claimType) {
        this.claimType = claimType;
    }

    public String getCustomerStatement() {
        return customerStatement;
    }

    public void setCustomerStatement(String customerStatement) {
        this.customerStatement = customerStatement;
    }

    public Boolean getSyntheticDataConfirmed() {
        return syntheticDataConfirmed;
    }

    public void setSyntheticDataConfirmed(Boolean syntheticDataConfirmed) {
        this.syntheticDataConfirmed = syntheticDataConfirmed;
    }

    public Boolean getReviewRequired() {
        return reviewRequired;
    }

    public void setReviewRequired(Boolean reviewRequired) {
        this.reviewRequired = reviewRequired;
    }
}
