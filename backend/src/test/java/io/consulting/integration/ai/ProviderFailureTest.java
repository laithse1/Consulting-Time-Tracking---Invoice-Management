package io.consulting.integration.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProviderFailureTest {

    private String error(String code) {
        return ProviderFailure.message(
            429,
            (
                "{\"error\":{\"code\":\"" +
                code +
                "\",\"message\":\"PRIVATE_KEY_AND_SOURCE\"}}"
            ).getBytes(StandardCharsets.UTF_8),
            new ObjectMapper()
        );
    }

    @Test
    void distinguishesQuotaFromTemporaryThrottlingWithoutLeakingRemoteMessages() {
        assertTrue(error("insufficient_quota").contains("API quota or billing"));
        assertTrue(error("credit_balance_exhausted").contains("Retrying will not"));
        assertTrue(error("rate_limit_exceeded").contains("Temporary API rate limit"));
        assertTrue(error("slow_down").contains("Wait before retrying"));
        assertTrue(error("unknown").contains("quota or rate limit"));
        assertFalse(error("insufficient_quota").contains("PRIVATE_KEY"));
        assertTrue(
            ProviderFailure.message(
                429,
                "not JSON".getBytes(StandardCharsets.UTF_8),
                new ObjectMapper()
            ).contains("quota or rate limit")
        );
    }
}
