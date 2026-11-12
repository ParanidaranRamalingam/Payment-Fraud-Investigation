package com.capstone.fraudcase.tools.synthetic;

import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.domain.TransactionClaim;
import com.capstone.fraudcase.tools.ToolException;
import com.capstone.fraudcase.tools.ToolProperties;
import com.capstone.fraudcase.tools.ToolRequest;
import com.capstone.fraudcase.tools.model.DeviceSignals;
import com.capstone.fraudcase.tools.model.LocationComparison;
import com.capstone.fraudcase.tools.model.MerchantRiskProfile;
import com.capstone.fraudcase.tools.model.MerchantRiskProfile.RiskTier;
import com.capstone.fraudcase.tools.model.PriorDisputes;
import com.capstone.fraudcase.tools.model.TransactionDetails;
import com.capstone.fraudcase.tools.model.TransactionDetails.AuthenticationMethod;
import com.capstone.fraudcase.tools.model.TransactionDetails.PaymentChannel;
import com.capstone.fraudcase.tools.model.TransactionDetails.PostingStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic synthetic evidence. Every value is a pure function of the masked/synthetic identifiers in the
 * {@link ToolRequest} (never the case ID, clock or randomness), so identical identifiers always produce identical
 * evidence. Named {@link SyntheticScenario}s pin the signals needed by the test plan; failures for a scenario are
 * raised as {@link ToolException}s and never replaced by invented data. A configurable global seed
 * ({@code fraud.tools.fixture-seed}) is mixed into every value and recorded in {@link #source()}.
 */
@Component
public class SyntheticFixtureGenerator {

    public static final String SOURCE_PREFIX = "synthetic-fixture:v1:";

    private static final List<String> HOME_COUNTRIES = List.of("GB", "IE", "FR", "DE", "NL", "ES");
    private static final List<String> FAR_COUNTRIES = List.of("BR", "SG", "US", "ZA", "JP", "AU");

    private final String fixtureSeed;

    public SyntheticFixtureGenerator() {
        this(ToolProperties.withTimeout(Duration.ofMillis(2500)));
    }

    @Autowired
    public SyntheticFixtureGenerator(ToolProperties properties) {
        this.fixtureSeed = properties.fixtureSeed();
    }

    /** Versioned provenance including the global seed, e.g. {@code synthetic-fixture:v1:fraud-training-v1}. */
    public String source() {
        return SOURCE_PREFIX + fixtureSeed;
    }

    private DeterministicSeed seed(String namespace, String... parts) {
        return DeterministicSeed.of(fixtureSeed + ":" + namespace, parts);
    }

    public TransactionDetails transactionDetails(ToolRequest request) {
        SyntheticScenario scenario = scenarioFor(request, EvidenceTool.TRANSACTION_DETAILS);
        TransactionClaim claim = request.claim();
        DeterministicSeed seed = seed("transaction", claim.transactionReference(),
                request.cardReference(), claim.merchantName());
        PaymentChannel channel = switch (scenario) {
            case FRAUD, TOOL_TIMEOUT, TOOLS_UNAVAILABLE, CONTRADICTORY, MERCHANT_DISPUTE -> PaymentChannel.CARD_NOT_PRESENT;
            case DUPLICATE, GENUINE, PARTIAL_HISTORY, MISSING_TRANSACTION -> PaymentChannel.CHIP;
            case DERIVED -> seed.pick(0, List.of(PaymentChannel.CARD_NOT_PRESENT, PaymentChannel.CARD_NOT_PRESENT,
                    PaymentChannel.CHIP, PaymentChannel.CONTACTLESS));
        };
        AuthenticationMethod authentication = switch (scenario) {
            case FRAUD, TOOL_TIMEOUT, TOOLS_UNAVAILABLE -> AuthenticationMethod.NONE;
            case CONTRADICTORY, MERCHANT_DISPUTE -> AuthenticationMethod.THREE_DS;
            case DUPLICATE, GENUINE, PARTIAL_HISTORY, MISSING_TRANSACTION -> AuthenticationMethod.PIN;
            case DERIVED -> switch (channel) {
                case CHIP -> AuthenticationMethod.PIN;
                case CONTACTLESS -> AuthenticationMethod.NONE;
                case CARD_NOT_PRESENT -> seed.chance(1, 60) ? AuthenticationMethod.THREE_DS : AuthenticationMethod.NONE;
            };
        };
        int matchingPostings = scenario == SyntheticScenario.DUPLICATE
                || (scenario == SyntheticScenario.DERIVED && seed.chance(2, 5)) ? 2 : 1;
        PostingStatus postingStatus = scenario == SyntheticScenario.DERIVED && seed.chance(3, 5)
                ? PostingStatus.PENDING : PostingStatus.POSTED;
        Instant postedAt = claim.transactionAt().plus(Duration.ofMinutes(seed.between(4, 5, 240)));
        String fingerprint = "fp_" + seed("fingerprint", claim.merchantName(),
                claim.amount().toPlainString(), claim.currency().getCurrencyCode(), request.cardReference()).token(16);
        return new TransactionDetails(claim.transactionReference(), postingStatus, channel, authentication,
                claim.amount(), claim.currency().getCurrencyCode(), claim.merchantName(),
                merchantCountry(request, scenario), postedAt, fingerprint, matchingPostings);
    }

    public DeviceSignals deviceSignals(ToolRequest request) {
        SyntheticScenario scenario = scenarioFor(request, EvidenceTool.DEVICE_SIGNALS);
        DeterministicSeed seed = seed("device", request.cardReference(),
                request.claim().transactionReference());
        boolean newDevice = switch (scenario) {
            case FRAUD, TOOL_TIMEOUT, TOOLS_UNAVAILABLE, CONTRADICTORY -> true;
            case DERIVED -> seed.chance(0, 20);
            default -> false;
        };
        boolean trusted = !newDevice && (scenario != SyntheticScenario.DERIVED || seed.chance(1, 85));
        int ipRiskScore = switch (scenario) {
            case FRAUD, TOOL_TIMEOUT, TOOLS_UNAVAILABLE -> seed.between(2, 85, 99);
            case CONTRADICTORY -> seed.between(2, 72, 80);
            case DERIVED -> seed.nextInt(2, 60) + (seed.chance(3, 15) ? 40 : 0);
            default -> seed.between(2, 5, 25);
        };
        boolean emulator = scenario == SyntheticScenario.DERIVED && seed.chance(4, 3);
        Instant transactionAt = request.claim().transactionAt();
        Instant firstSeenAt = newDevice
                ? transactionAt.minus(Duration.ofMinutes(seed.between(5, 5, 30)))
                : transactionAt.minus(Duration.ofDays(seed.between(5, 90, 720)));
        return new DeviceSignals("dev_" + seed.token(12), firstSeenAt, newDevice, trusted, emulator, ipRiskScore);
    }

    public LocationComparison recentLocations(ToolRequest request) {
        SyntheticScenario scenario = scenarioFor(request, EvidenceTool.RECENT_LOCATIONS);
        DeterministicSeed seed = seed("location", request.customerReference(),
                request.cardReference(), request.claim().transactionReference());
        String transactionCountry = merchantCountry(request, scenario);
        if (scenario == SyntheticScenario.PARTIAL_HISTORY
                || (scenario == SyntheticScenario.DERIVED && seed.chance(0, 5))) {
            return new LocationComparison(false, null, transactionCountry, 0, 0, false);
        }
        String home = homeCountry(request);
        if (travelAnomaly(request, scenario)) {
            return new LocationComparison(true, home, transactionCountry, seed.between(1, 5_000, 11_000),
                    seed.between(2, 1, 4), true);
        }
        return new LocationComparison(true, home, transactionCountry, seed.between(1, 0, 40),
                seed.between(2, 6, 72), false);
    }

    public MerchantRiskProfile merchantRiskProfile(ToolRequest request) {
        SyntheticScenario scenario = scenarioFor(request, EvidenceTool.MERCHANT_RISK_PROFILE);
        TransactionClaim claim = request.claim();
        String merchantKey = claim.merchantName().toLowerCase(Locale.ROOT).strip();
        DeterministicSeed merchant = seed("merchant", merchantKey, claim.merchantCategoryCode());
        DeterministicSeed relationship = seed("relationship", request.customerReference(),
                merchantKey);
        RiskTier tier = switch (scenario) {
            case FRAUD, TOOL_TIMEOUT, TOOLS_UNAVAILABLE -> RiskTier.HIGH;
            case CONTRADICTORY, MERCHANT_DISPUTE -> RiskTier.MEDIUM;
            case DERIVED -> {
                int roll = merchant.nextInt(0, 100);
                yield roll < 60 ? RiskTier.LOW : roll < 90 ? RiskTier.MEDIUM : RiskTier.HIGH;
            }
            default -> RiskTier.LOW;
        };
        int chargebackBasisPoints = switch (tier) {
            case HIGH -> merchant.between(1, 350, 600);
            case MEDIUM -> merchant.between(1, 80, 150);
            case LOW -> merchant.between(1, 10, 50);
        };
        int priorTransactions = switch (scenario) {
            case FRAUD, TOOL_TIMEOUT, TOOLS_UNAVAILABLE -> 0;
            case CONTRADICTORY -> relationship.between(0, 3, 8);
            case MERCHANT_DISPUTE -> relationship.between(0, 2, 6);
            case DERIVED -> relationship.nextInt(0, 13);
            default -> relationship.between(0, 3, 20);
        };
        int complaints = switch (scenario) {
            case MERCHANT_DISPUTE -> merchant.between(2, 3, 7);
            case FRAUD, TOOL_TIMEOUT, TOOLS_UNAVAILABLE -> merchant.nextInt(2, 3);
            case DERIVED -> merchant.nextInt(2, 6);
            default -> 0;
        };
        return new MerchantRiskProfile(claim.merchantName(), claim.merchantCategoryCode(), tier,
                BigDecimal.valueOf(chargebackBasisPoints, 2), priorTransactions, complaints);
    }

    public PriorDisputes priorDisputes(ToolRequest request) {
        SyntheticScenario scenario = scenarioFor(request, EvidenceTool.PRIOR_DISPUTES);
        DeterministicSeed seed = seed("disputes", request.customerReference());
        return switch (scenario) {
            case CONTRADICTORY -> new PriorDisputes(3, 0, 1, 2);
            case FRAUD, TOOL_TIMEOUT, TOOLS_UNAVAILABLE -> new PriorDisputes(0, 0, 0, 0);
            case DERIVED -> {
                int confirmed = seed.nextInt(0, 2);
                int merchantDisputes = seed.nextInt(1, 3);
                int declined = seed.nextInt(2, 3);
                yield new PriorDisputes(confirmed + merchantDisputes + declined + seed.nextInt(3, 2), confirmed,
                        merchantDisputes, declined);
            }
            default -> {
                int merchantDisputes = seed.nextInt(1, 2);
                yield new PriorDisputes(merchantDisputes, 0, merchantDisputes, 0);
            }
        };
    }

    private static SyntheticScenario scenarioFor(ToolRequest request, EvidenceTool tool) {
        SyntheticScenario scenario = SyntheticScenario.forReference(request.claim().transactionReference());
        scenario.failureFor(tool).ifPresent(failure -> {
            throw new ToolException(failure, "synthetic scenario " + scenario + " injects " + failure);
        });
        return scenario;
    }

    private boolean travelAnomaly(ToolRequest request, SyntheticScenario scenario) {
        return switch (scenario) {
            case FRAUD, TOOL_TIMEOUT, CONTRADICTORY -> true;
            case DERIVED -> seed("travel", request.claim().transactionReference(),
                    request.cardReference()).chance(0, 10);
            default -> false;
        };
    }

    private String homeCountry(ToolRequest request) {
        return seed("home", request.customerReference()).pick(0, HOME_COUNTRIES);
    }

    private String merchantCountry(ToolRequest request, SyntheticScenario scenario) {
        return travelAnomaly(request, scenario)
                ? seed("far", request.claim().transactionReference()).pick(0, FAR_COUNTRIES)
                : homeCountry(request);
    }
}
