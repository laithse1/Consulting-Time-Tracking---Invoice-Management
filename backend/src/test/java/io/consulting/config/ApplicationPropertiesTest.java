package io.consulting.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ApplicationPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(PropertiesConfiguration.class)
        .withPropertyValues("app.token=configuration-test-token-24-characters");

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ApplicationProperties.class)
    static class PropertiesConfiguration {}

    @Test
    void bindsRuntimeOverridesAndRedactsSecrets() {
        runner
            .withPropertyValues(
                "app.assistant.history-limit=12",
                "app.provider.request-timeout=2s",
                "app.invoice.number-prefix=ACME"
            )
            .run(context -> {
                assertNull(context.getStartupFailure());
                var properties = context.getBean(ApplicationProperties.class);
                assertEquals(12, properties.assistant().historyLimit());
                assertEquals(2, properties.provider().requestTimeout().toSeconds());
                assertEquals("ACME", properties.invoice().numberPrefix());
                assertFalse(properties.toString().contains(properties.token()));
            });
    }

    @Test
    void rejectsShortTokens() {
        runner
            .withPropertyValues("app.token=short")
            .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void rejectsUnsafeResourceLimits() {
        runner
            .withPropertyValues("app.assistant.max-actions=1000")
            .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void rejectsUnboundedProviderTimeouts() {
        runner
            .withPropertyValues("app.provider.response-timeout=1h")
            .run(context -> assertNotNull(context.getStartupFailure()));
    }
}
