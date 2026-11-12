package com.capstone.fraudcase.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Analyst accounts, supplied only through the environment (e.g. {@code FRAUD_SECURITY_ACCOUNTS_0_USERNAME},
 * {@code FRAUD_SECURITY_ACCOUNTS_0_PASSWORD}, {@code FRAUD_SECURITY_ACCOUNTS_0_ROLES}). Passwords must be
 * bcrypt hashes with the {@code {bcrypt}} prefix; plain-text or {@code {noop}} passwords are rejected at
 * startup. With no accounts configured every API call is refused (fail closed).
 */
@ConfigurationProperties("fraud.security")
public record FraudSecurityProperties(List<Account> accounts) {

    public FraudSecurityProperties {
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
        Set<String> seen = new HashSet<>();
        for (Account account : accounts) {
            if (!seen.add(account.username())) {
                throw new IllegalArgumentException("duplicate analyst account: " + account.username());
            }
        }
    }

    public record Account(String username, String password, Set<AnalystRole> roles) {

        /** Same shape as the 64-character actor IDs stored in the audit trail. */
        static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,64}$");
        static final String BCRYPT_PREFIX = "{bcrypt}";

        public Account {
            if (username == null || !USERNAME.matcher(username).matches()) {
                throw new IllegalArgumentException("analyst username must be 3-64 letters, digits, '.', '_' or '-'");
            }
            if (password == null || !password.startsWith(BCRYPT_PREFIX) || password.length() <= BCRYPT_PREFIX.length()) {
                throw new IllegalArgumentException("password for analyst " + username + " must be a {bcrypt} hash");
            }
            if (roles == null || roles.isEmpty()) {
                throw new IllegalArgumentException("analyst " + username + " must have at least one role");
            }
            roles = Set.copyOf(EnumSet.copyOf(roles));
        }

        @Override
        public String toString() {
            return "Account[username=" + username + ", roles=" + roles + "]";
        }
    }
}
