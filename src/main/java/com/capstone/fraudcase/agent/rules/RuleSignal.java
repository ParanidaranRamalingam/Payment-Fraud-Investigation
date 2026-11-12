package com.capstone.fraudcase.agent.rules;

/** Named, explainable indicators derived from typed tool evidence. IDs appear in recommendation trace refs. */
public enum RuleSignal {
    NEW_UNTRUSTED_DEVICE("F-NEW-DEVICE", "new untrusted device", true),
    IMPOSSIBLE_TRAVEL("F-LOCATION", "impossible travel from last known location", true),
    HIGH_RISK_MERCHANT("F-MERCHANT-RISK", "high-risk merchant", true),
    UNKNOWN_MERCHANT("F-MERCHANT-UNKNOWN", "no prior customer history with merchant", true),
    WEAK_AUTHENTICATION("F-WEAK-AUTH", "card-not-present without strong authentication", true),
    HIGH_IP_RISK("F-IP-RISK", "high IP risk score", true),
    EMULATOR_DETECTED("F-EMULATOR", "emulator detected on device", true),
    STRONG_AUTHENTICATION("G-STRONG-AUTH", "strong customer authentication (3DS/PIN)", false),
    TRUSTED_DEVICE("G-TRUSTED-DEVICE", "device trusted by customer", false),
    KNOWN_MERCHANT("G-KNOWN-MERCHANT", "customer has transacted with merchant before", false),
    CONSISTENT_LOCATION("G-LOCATION-CONSISTENT", "location consistent with recent activity", false),
    DECLINED_DISPUTE_HISTORY("G-DISPUTE-HISTORY", "two or more declined disputes in 12 months", false);

    private final String id;
    private final String description;
    private final boolean fraudIndicator;

    RuleSignal(String id, String description, boolean fraudIndicator) {
        this.id = id;
        this.description = description;
        this.fraudIndicator = fraudIndicator;
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public boolean fraudIndicator() {
        return fraudIndicator;
    }
}
