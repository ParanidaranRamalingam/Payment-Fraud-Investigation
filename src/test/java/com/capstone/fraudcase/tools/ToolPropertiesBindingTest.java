package com.capstone.fraudcase.tools;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ToolPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ToolConfiguration.class);

    @Test
    void binding_noProperties_usesDocumentedDefaults() {
        runner.run(context -> {
            ToolProperties properties = context.getBean(ToolProperties.class);
            assertThat(properties.timeout()).isEqualTo(Duration.ofMillis(2500));
            assertThat(properties.fixtureSeed()).isEqualTo(ToolProperties.DEFAULT_FIXTURE_SEED);
        });
    }

    @Test
    void binding_overrides_applied() {
        runner.withPropertyValues("fraud.tools.timeout=750ms", "fraud.tools.fixture-seed=demo-seed-2")
                .run(context -> {
                    ToolProperties properties = context.getBean(ToolProperties.class);
                    assertThat(properties.timeout()).isEqualTo(Duration.ofMillis(750));
                    assertThat(properties.fixtureSeed()).isEqualTo("demo-seed-2");
                });
    }

    @Test
    void binding_invalidSeed_failsStartup() {
        runner.withPropertyValues("fraud.tools.fixture-seed=Not Valid").run(context -> assertThat(context).hasFailed());
    }
}
