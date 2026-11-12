package com.capstone.fraudcase.persistence.entity;

import com.capstone.fraudcase.domain.ClaimType;
import com.capstone.fraudcase.domain.TransactionClaim;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

@Entity
@Immutable
@Table(name = "transaction_claim")
public class TransactionClaimEntity extends AssignedIdEntity {

    @Column(name = "case_id", length = 36, nullable = false, updatable = false)
    private String caseId;

    @Column(name = "transaction_reference", length = 64, nullable = false)
    private String transactionReference;

    @Column(name = "merchant_name", length = 120, nullable = false)
    private String merchantName;

    @Column(name = "merchant_category_code", length = 4)
    private String merchantCategoryCode;

    @Column(name = "amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "transaction_at", nullable = false)
    private Instant transactionAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "claim_type", length = 32, nullable = false)
    private ClaimType claimType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TransactionClaimEntity() {
    }

    public TransactionClaimEntity(String caseId, TransactionClaim claim) {
        super(true);
        this.caseId = Objects.requireNonNull(caseId, "caseId");
        this.transactionReference = claim.transactionReference();
        this.merchantName = claim.merchantName();
        this.merchantCategoryCode = claim.merchantCategoryCode();
        this.amount = claim.amount();
        this.currency = claim.currency().getCurrencyCode();
        this.transactionAt = toDbPrecision(claim.transactionAt());
        this.claimType = claim.claimType();
    }

    @PrePersist
    void onCreate() {
        this.createdAt = now();
    }

    public TransactionClaim toDomain() {
        return new TransactionClaim(transactionReference, merchantName, merchantCategoryCode, amount,
                Currency.getInstance(currency), transactionAt, claimType);
    }

    public String getCaseId() {
        return caseId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
