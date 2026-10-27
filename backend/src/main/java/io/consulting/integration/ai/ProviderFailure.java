package io.consulting.integration.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;

/** Only known error identifiers influence messages; remote error text can contain private data. */
final class ProviderFailure {

    private ProviderFailure() {}

    static String message(int status, byte[] body, ObjectMapper json) {
        String code = "",
            type = "";
        try {
            var error = json.readTree(body).path("error");
            code = error.path("code").asText("");
            type = error.path("type").asText("");
        } catch (Exception ignored) {
            // Non-JSON failures still receive a useful HTTP status message.
        }
        String explanation;
        if (status == 429) {
            if (
                type.equals("insufficient_quota") ||
                Set.of(
                    "insufficient_quota",
                    "credit_balance_exhausted",
                    "billing_hard_limit_reached",
                    "billing_not_active",
                    "organization_usage_limit_exceeded",
                    "project_usage_limit_exceeded"
                ).contains(code)
            ) {
                explanation =
                    "API quota or billing limit reached. Check the API credit balance and organization/project spending limits. ChatGPT/Codex subscription usage does not fund this API connection. Retrying will not restore exhausted quota.";
            } else if (
                Set.of("rate_limit_exceeded", "slow_down").contains(code) ||
                type.equals("rate_limit_error")
            ) {
                explanation =
                    "Temporary API rate limit reached. Wait before retrying, use a smaller request, or review the provider's request/token limits.";
            } else {
                explanation =
                    "The provider reported a quota or rate limit. Check API billing and usage limits; if quota is available, wait before retrying.";
            }
        } else if (status == 401 || status == 403) {
            explanation = "Check the server-side API credential and its project/model permissions.";
        } else {
            explanation =
                "Check the provider endpoint, model, request configuration, and service availability.";
        }
        return (
            "Provider request failed (HTTP " +
            status +
            "). " +
            explanation +
            " No actions were applied."
        );
    }
}
