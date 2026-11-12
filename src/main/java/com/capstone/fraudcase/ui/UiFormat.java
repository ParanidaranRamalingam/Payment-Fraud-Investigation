package com.capstone.fraudcase.ui;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;

/** Presentation-only formatting for templates. No business rules live here. */
@Component("uiFormat")
public class UiFormat {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT).withZone(ZoneOffset.UTC);

    public String ts(Instant instant) {
        return instant == null ? "—" : TS.format(instant);
    }

    public String date(Instant instant) {
        return instant == null ? "—" : DATE.format(instant);
    }

    public String label(Enum<?> value) {
        if (value == null) {
            return "—";
        }
        String[] words = value.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }

    public String pct(BigDecimal confidence) {
        return confidence == null ? "—" : confidence.movePointRight(2).setScale(0, RoundingMode.HALF_UP)
                .toPlainString() + "%";
    }

    public int pctValue(BigDecimal confidence) {
        return confidence == null ? 0 : confidence.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    public String similarity(double score) {
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public String money(BigDecimal amount, Currency currency) {
        if (amount == null || currency == null) {
            return "—";
        }
        return currency.getCurrencyCode() + " " + amount.setScale(currency.getDefaultFractionDigits() < 0 ? 2
                : Math.max(2, currency.getDefaultFractionDigits()), RoundingMode.HALF_UP).toPlainString();
    }

    public String ms(Long durationMs) {
        return durationMs == null ? "—" : durationMs + " ms";
    }

    /** Maps domain enums to badge tone classes; status never relies on colour alone (text is always shown). */
    public String badgeTone(Enum<?> value) {
        if (value == null) {
            return "badge--neutral";
        }
        return switch (value.name()) {
            case "CLOSED", "GENUINE_TRANSACTION", "AVAILABLE", "SUCCESS", "LOW", "READY_FOR_REVIEW", "ROUTED" ->
                    "badge--ok";
            case "INCOMPLETE", "UNDER_REVIEW", "MEDIUM", "NOT_FOUND", "SUSPICIOUS_TRANSACTION",
                    "MERCHANT_DISPUTE", "DUPLICATE_PROCESSING", "MANUAL_REVIEW", "TIMEOUT" -> "badge--warn";
            case "HIGH", "CRITICAL", "ERROR", "UNAVAILABLE", "REJECTED", "UNAUTHORISED_TRANSACTION" ->
                    "badge--danger";
            case "ENRICHING" -> "badge--info";
            default -> "badge--neutral";
        };
    }

    /** Short provenance label distinguishing claim, observed evidence, generated text, policy and review. */
    public String provenanceLabel(Enum<?> provenance) {
        if (provenance == null) {
            return "Unknown source";
        }
        return switch (provenance.name()) {
            case "CUSTOMER_STATEMENT" -> "Customer claim";
            case "TOOL_RESULT" -> "Observed evidence";
            case "ANALYST_NOTE" -> "Analyst note";
            case "POLICY_PASSAGE" -> "Policy passage";
            case "RULE_ENGINE" -> "Generated (rules)";
            case "MODEL_INTERPRETATION" -> "Generated (model)";
            default -> label(provenance);
        };
    }
}
