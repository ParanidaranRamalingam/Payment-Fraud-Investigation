package com.capstone.fraudcase.tools.model;

import com.capstone.fraudcase.domain.EvidenceTool;

import java.time.Instant;
import java.util.Map;

import static com.capstone.fraudcase.tools.model.Attributes.range;
import static com.capstone.fraudcase.tools.model.Attributes.require;

/** Result of {@code getDeviceSignals} for the device that initiated the transaction. */
public record DeviceSignals(
        String deviceToken,
        Instant firstSeenAt,
        boolean newDevice,
        boolean trustedByCustomer,
        boolean emulatorDetected,
        int ipRiskScore) implements EvidencePayload {

    public static final int HIGH_IP_RISK = 70;

    public DeviceSignals {
        require(deviceToken, "deviceToken");
        require(firstSeenAt, "firstSeenAt");
        range(ipRiskScore, 0, 100, "ipRiskScore");
        if (newDevice && trustedByCustomer) {
            throw new IllegalArgumentException("a new device cannot already be trusted");
        }
    }

    @Override
    public EvidenceTool tool() {
        return EvidenceTool.DEVICE_SIGNALS;
    }

    @Override
    public String summary() {
        return "Device %s first seen %s; %s, %s, emulator %s, IP risk score %d/100."
                .formatted(deviceToken, firstSeenAt, newDevice ? "new device" : "established device",
                        trustedByCustomer ? "trusted by customer" : "not trusted by customer",
                        emulatorDetected ? "detected" : "not detected", ipRiskScore);
    }

    @Override
    public Map<String, String> attributes() {
        return Attributes.of("deviceToken", deviceToken, "firstSeenAt", firstSeenAt, "newDevice", newDevice,
                "trustedByCustomer", trustedByCustomer, "emulatorDetected", emulatorDetected,
                "ipRiskScore", ipRiskScore);
    }
}
