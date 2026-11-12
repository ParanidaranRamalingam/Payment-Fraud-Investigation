package com.capstone.fraudcase.api.dto;

import com.capstone.fraudcase.api.validation.IsoCurrency;
import com.capstone.fraudcase.api.validation.SafeText;
import com.capstone.fraudcase.domain.ClaimType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionClaimRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{6,64}$",
                message = "must be 6-64 letters, digits, '_' or '-'") @SafeText
        String transactionReference,

        @NotBlank @Size(max = 120) @SafeText
        String merchantName,

        @Pattern(regexp = "^\\d{4}$", message = "must be a 4-digit merchant category code")
        String merchantCategoryCode,

        @NotNull @Positive @Digits(integer = 17, fraction = 2)
        BigDecimal amount,

        @NotBlank @IsoCurrency
        String currency,

        @NotNull @PastOrPresent
        Instant transactionAt,

        @NotNull
        ClaimType claimType) {
}
