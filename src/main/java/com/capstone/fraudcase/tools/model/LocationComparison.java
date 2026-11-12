package com.capstone.fraudcase.tools.model;

import com.capstone.fraudcase.domain.EvidenceTool;

import java.util.Map;

import static com.capstone.fraudcase.tools.model.Attributes.range;
import static com.capstone.fraudcase.tools.model.Attributes.require;

/**
 * Result of {@code compareRecentLocations}. When {@code historyAvailable} is false the comparison is incomplete:
 * no last-known location exists, so no travel anomaly can be asserted either way.
 */
public record LocationComparison(
        boolean historyAvailable,
        String lastKnownCountry,
        String transactionCountry,
        int distanceKm,
        int hoursSinceLastKnown,
        boolean impossibleTravel) implements EvidencePayload {

    public LocationComparison {
        require(transactionCountry, "transactionCountry");
        range(distanceKm, 0, 25_000, "distanceKm");
        range(hoursSinceLastKnown, 0, 24 * 365, "hoursSinceLastKnown");
        if (historyAvailable) {
            require(lastKnownCountry, "lastKnownCountry");
        } else if (lastKnownCountry != null || impossibleTravel || distanceKm != 0) {
            throw new IllegalArgumentException("without location history no comparison can be asserted");
        }
    }

    public boolean consistent() {
        return historyAvailable && !impossibleTravel;
    }

    @Override
    public EvidenceTool tool() {
        return EvidenceTool.RECENT_LOCATIONS;
    }

    @Override
    public String summary() {
        if (!historyAvailable) {
            return "No recent location history on file; transaction country %s could not be compared."
                    .formatted(transactionCountry);
        }
        return "Transaction country %s vs last known %s: %d km apart, %d h since last activity; %s."
                .formatted(transactionCountry, lastKnownCountry, distanceKm, hoursSinceLastKnown,
                        impossibleTravel ? "impossible travel detected" : "consistent with recent activity");
    }

    @Override
    public Map<String, String> attributes() {
        return Attributes.of("historyAvailable", historyAvailable, "lastKnownCountry", lastKnownCountry,
                "transactionCountry", transactionCountry, "distanceKm", distanceKm,
                "hoursSinceLastKnown", hoursSinceLastKnown, "impossibleTravel", impossibleTravel);
    }
}
