package com.capstone.fraudcase.e2e.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Boots the packaged Spring Boot jar on a random port as a child process and hands the
 * suite its base URL. The process is stopped when the suite finishes.
 *
 * <p>Required environment: DB_URL or DB_TEST_URL, DB_USERNAME, DB_PASSWORD
 * (sourced from ~/.config/fraud-capstone/db.env). Test accounts are injected via
 * FRAUD_SECURITY_ACCOUNTS_* so the suite never touches production credentials.</p>
 */
public final class AppLauncher {

    private static final Pattern PORT_LINE = Pattern.compile("Tomcat started on port (\\d+)");
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

    /** Throwaway bcrypt test accounts (same throwaway hashes used by the app's own test profile). */
    private static final String ANALYST_HASH = "{bcrypt}$2b$10$Td3EKktJ8NAXMiDcNNg3DevWjnw53T8S0FmdTWJ.XdfQOn640A1WW";
    private static final String REVIEWER_HASH = "{bcrypt}$2b$10$8poTtXkRF1Lx6hf3SRjJUeWLhgXxritGKdEIuQpJdPEY2K7foLm8a";

    private static Process process;
    private static int port;
    private static final java.util.List<String> log = new CopyOnWriteArrayList<>();

    private AppLauncher() {
    }

    public static synchronized int startIfNeeded() {
        if (process != null && process.isAlive()) {
            return port;
        }
        Path jar = Path.of(System.getProperty("app.jar",
                "../target/payment-fraud-investigation-0.1.0-SNAPSHOT.jar")).toAbsolutePath().normalize();
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("Application jar not found at " + jar
                    + " — run `mvn -DskipTests package` in the app module first.");
        }
        String dbUrl = env("DB_TEST_URL",
                "jdbc:mysql://localhost:3306/fraud_capstone_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        String dbUser = env("DB_USERNAME", "fraud_app");
        String dbPassword = System.getenv("DB_PASSWORD");
        if (dbPassword == null || dbPassword.isBlank()) {
            throw new IllegalStateException("DB_PASSWORD is not set — source ~/.config/fraud-capstone/db.env first.");
        }

        Map<String, String> env = new HashMap<>();
        env.put("DB_URL", dbUrl);
        env.put("DB_USERNAME", dbUser);
        env.put("DB_PASSWORD", dbPassword);
        env.put("SERVER_PORT", "0");
        env.put("FRAUD_AI_ENABLED", "false");
        env.put("FRAUD_SECURITY_ACCOUNTS_0_USERNAME", "it-analyst");
        env.put("FRAUD_SECURITY_ACCOUNTS_0_PASSWORD", ANALYST_HASH);
        env.put("FRAUD_SECURITY_ACCOUNTS_0_ROLES", "ANALYST");
        env.put("FRAUD_SECURITY_ACCOUNTS_1_USERNAME", "it-reviewer");
        env.put("FRAUD_SECURITY_ACCOUNTS_1_PASSWORD", REVIEWER_HASH);
        env.put("FRAUD_SECURITY_ACCOUNTS_1_ROLES", "REVIEWER");
        env.put("FRAUD_SECURITY_ACCOUNTS_2_USERNAME", "it-lead");
        env.put("FRAUD_SECURITY_ACCOUNTS_2_PASSWORD", ANALYST_HASH);
        env.put("FRAUD_SECURITY_ACCOUNTS_2_ROLES", "ANALYST,REVIEWER");

        ProcessBuilder pb = new ProcessBuilder("java", "-jar", jar.toString());
        pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to launch application jar", e);
        }
        Thread logReader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    log.add(line);
                }
            } catch (IOException ignored) {
            }
        });
        logReader.setDaemon(true);
        logReader.start();

        port = waitForPort();
        Runtime.getRuntime().addShutdownHook(new Thread(AppLauncher::stop));
        return port;
    }

    private static int waitForPort() {
        Instant deadline = Instant.now().plus(STARTUP_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                throw new IllegalStateException("Application exited during startup:\n" + tail());
            }
            for (String line : log) {
                Matcher m = PORT_LINE.matcher(line);
                if (m.find()) {
                    // Wait until the dispatcher servlet is ready by polling the health endpoint.
                    int p = Integer.parseInt(m.group(1));
                    waitForHealth(p, deadline);
                    return p;
                }
            }
            sleep(200);
        }
        stop();
        throw new IllegalStateException("Application did not start within " + STARTUP_TIMEOUT + ":\n" + tail());
    }

    private static void waitForHealth(int p, Instant deadline) {
        while (Instant.now().isBefore(deadline)) {
            try {
                var conn = new java.net.URI("http://localhost:" + p + "/actuator/health")
                        .toURL().openConnection();
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                if (((java.net.HttpURLConnection) conn).getResponseCode() == 200) {
                    return;
                }
            } catch (Exception ignored) {
            }
            sleep(500);
        }
        throw new IllegalStateException("Health endpoint never came up on port " + p + ":\n" + tail());
    }

    public static synchronized void stop() {
        if (process != null) {
            process.destroy();
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            process = null;
        }
    }

    public static String tail() {
        int from = Math.max(0, log.size() - 40);
        return String.join("\n", log.subList(from, log.size()));
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
