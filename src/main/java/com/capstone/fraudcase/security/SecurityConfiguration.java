package com.capstone.fraudcase.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;

/**
 * HTTP Basic authentication for the REST API plus a session-backed form login for the analyst UI
 * ({@code /ui/**}). The authenticated username is the analyst/reviewer ID recorded on cases, reviews and
 * audit events; it is never taken from the request body. Authorization is deterministic and role-based:
 *
 * <pre>
 * GET  /api/**                     ANALYST or REVIEWER
 * POST /api/cases/{id}/reviews     REVIEWER
 * any other /api/** request        ANALYST
 * /actuator/health/**, /info       public
 * anything else                    denied
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FraudSecurityProperties.class)
public class SecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService analystAccounts(FraudSecurityProperties properties) {
        if (properties.accounts().isEmpty()) {
            log.warn("No analyst accounts configured (fraud.security.accounts); all API requests will be rejected");
        }
        List<UserDetails> users = properties.accounts().stream()
                .map(a -> User.withUsername(a.username()).password(a.password())
                        .roles(a.roles().stream().map(Enum::name).toArray(String[]::new)).build())
                .toList();
        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    ApiSecurityErrorHandler apiSecurityErrorHandler(JsonMapper jsonMapper, Clock clock) {
        return new ApiSecurityErrorHandler(jsonMapper, clock);
    }

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ApiSecurityErrorHandler errors) throws Exception {
        String analyst = AnalystRole.ANALYST.name();
        String reviewer = AnalystRole.REVIEWER.name();
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/error", "/css/**", "/js/**", "/favicon.ico", "/favicon.svg").permitAll()
                        .requestMatchers("/login", "/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/cases/*/reviews").hasRole(reviewer)
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole(analyst, reviewer)
                        .requestMatchers("/api/**").hasRole(analyst)
                        .requestMatchers(HttpMethod.POST, "/ui/cases/*/reviews").hasRole(reviewer)
                        .requestMatchers(HttpMethod.POST, "/ui/**").hasRole(analyst)
                        .requestMatchers("/", "/ui", "/ui/**").hasAnyRole(analyst, reviewer)
                        .anyRequest().denyAll())
                .httpBasic(basic -> basic.realmName(ApiSecurityErrorHandler.REALM).authenticationEntryPoint(errors))
                // UI paths send the browser to the login page; API paths keep the 401/403 JSON envelope.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> {
                            if (isUiRequest(request)) {
                                response.sendRedirect(request.getContextPath() + "/login");
                            } else {
                                errors.commence(request, response, e);
                            }
                        })
                        .accessDeniedHandler((request, response, e) -> {
                            if (isUiRequest(request)) {
                                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                            } else {
                                errors.handle(request, response, e);
                            }
                        }))
                // Sessions are only materialised for the UI form login; API clients still authenticate
                // per request via HTTP Basic.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // The JSON API stays CSRF-exempt (stateless, no ambient cookie). UI form posts DO need
                // protection; a session-backed repository gives one stable token per login session so
                // every rendered form stays valid (no token-rotation 403s on submit).
                .csrf(csrf -> csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository())
                        .ignoringRequestMatchers("/api/**"))
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/ui", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .build();
    }

    private static boolean isUiRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/") || path.startsWith("/ui");
    }
}
