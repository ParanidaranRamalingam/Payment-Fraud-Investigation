package com.capstone.fraudcase.agent.rules;

import com.capstone.fraudcase.agent.common.FraudCaseContext;
import com.capstone.fraudcase.domain.EvidenceTool;
import com.capstone.fraudcase.tools.model.DeviceSignals;
import com.capstone.fraudcase.tools.model.LocationComparison;
import com.capstone.fraudcase.tools.model.MerchantRiskProfile;
import com.capstone.fraudcase.tools.model.PriorDisputes;
import com.capstone.fraudcase.tools.model.TransactionDetails;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Indicators present in the available typed evidence. Missing evidence contributes no indicator in either
 * direction: absence of data is never read as a fact.
 */
record FraudSignals(Set<RuleSignal> present, boolean locationHistoryMissing) {

    static final int DECLINED_DISPUTE_THRESHOLD = 2;

    FraudSignals {
        present = present.isEmpty() ? EnumSet.noneOf(RuleSignal.class) : EnumSet.copyOf(present);
    }

    static FraudSignals evaluate(FraudCaseContext context, TransactionDetails transaction) {
        Set<RuleSignal> signals = EnumSet.noneOf(RuleSignal.class);
        if (transaction.channel() == TransactionDetails.PaymentChannel.CARD_NOT_PRESENT
                && !transaction.strongAuthentication()) {
            signals.add(RuleSignal.WEAK_AUTHENTICATION);
        }
        if (transaction.strongAuthentication()) {
            signals.add(RuleSignal.STRONG_AUTHENTICATION);
        }
        context.payload(EvidenceTool.DEVICE_SIGNALS, DeviceSignals.class).ifPresent(device -> {
            if (device.newDevice() && !device.trustedByCustomer()) {
                signals.add(RuleSignal.NEW_UNTRUSTED_DEVICE);
            }
            if (device.trustedByCustomer()) {
                signals.add(RuleSignal.TRUSTED_DEVICE);
            }
            if (device.ipRiskScore() >= DeviceSignals.HIGH_IP_RISK) {
                signals.add(RuleSignal.HIGH_IP_RISK);
            }
            if (device.emulatorDetected()) {
                signals.add(RuleSignal.EMULATOR_DETECTED);
            }
        });
        Optional<LocationComparison> location = context.payload(EvidenceTool.RECENT_LOCATIONS, LocationComparison.class);
        location.ifPresent(l -> {
            if (l.historyAvailable() && l.impossibleTravel()) {
                signals.add(RuleSignal.IMPOSSIBLE_TRAVEL);
            }
            if (l.consistent()) {
                signals.add(RuleSignal.CONSISTENT_LOCATION);
            }
        });
        context.payload(EvidenceTool.MERCHANT_RISK_PROFILE, MerchantRiskProfile.class).ifPresent(merchant -> {
            if (merchant.riskTier() == MerchantRiskProfile.RiskTier.HIGH) {
                signals.add(RuleSignal.HIGH_RISK_MERCHANT);
            }
            signals.add(merchant.recognisedByCustomer() ? RuleSignal.KNOWN_MERCHANT : RuleSignal.UNKNOWN_MERCHANT);
        });
        context.payload(EvidenceTool.PRIOR_DISPUTES, PriorDisputes.class).ifPresent(disputes -> {
            if (disputes.declinedLast12Months() >= DECLINED_DISPUTE_THRESHOLD) {
                signals.add(RuleSignal.DECLINED_DISPUTE_HISTORY);
            }
        });
        return new FraudSignals(signals, location.map(l -> !l.historyAvailable()).orElse(false));
    }

    List<RuleSignal> fraud() {
        return filter(true);
    }

    List<RuleSignal> mitigating() {
        return filter(false);
    }

    private List<RuleSignal> filter(boolean fraudIndicator) {
        List<RuleSignal> result = new ArrayList<>();
        present.stream().filter(s -> s.fraudIndicator() == fraudIndicator).forEach(result::add);
        return result;
    }
}
