package io.consulting.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Validated runtime settings. Contract rates and terms are stored in the database. */
@Validated
@ConfigurationProperties(prefix = "app")
public record ApplicationProperties(
    @NotBlank @Size(min = 24) String token,
    @DefaultValue("") String allowedOrigins,
    @DefaultValue("./config/assistant-providers.json") String providerConfig,
    @Valid @DefaultValue Assistant assistant,
    @Valid @DefaultValue Provider provider,
    @Valid @DefaultValue Invoice invoice
) {
    public record Invoice(
        @DefaultValue("LA")
        @NotBlank
        @jakarta.validation.constraints.Pattern(regexp = "[A-Z][A-Z0-9]{0,11}")
        String numberPrefix
    ) {}

    public record Assistant(
        @DefaultValue("10000") @Min(100) @Max(10000) int maxSourceCharacters,
        @DefaultValue("30") @Min(1) @Max(30) int maxActions,
        @DefaultValue("50") @Min(1) @Max(500) int historyLimit,
        @DefaultValue("30") @Min(1) @Max(100) int maxRevisions
    ) {}

    public record Provider(
        @DefaultValue("8s") Duration connectTimeout,
        @DefaultValue("35s") Duration requestTimeout,
        @DefaultValue("20s") Duration responseTimeout,
        @DefaultValue("1048576") @Min(1024) @Max(10485760) int maxResponseBytes,
        @DefaultValue("262144") @Min(1024) @Max(1048576) int maxConfigBytes
    ) {
        public Provider {
            validateTimeout(connectTimeout);
            validateTimeout(requestTimeout);
            validateTimeout(responseTimeout);
        }

        private static void validateTimeout(Duration timeout) {
            if (
                timeout == null ||
                timeout.isNegative() ||
                timeout.isZero() ||
                timeout.compareTo(Duration.ofMinutes(5)) > 0
            ) {
                throw new IllegalArgumentException(
                    "Provider timeouts must be positive and at most five minutes"
                );
            }
        }
    }

    @Override
    public String toString() {
        return "ApplicationProperties[credentials=redacted]";
    }
}
