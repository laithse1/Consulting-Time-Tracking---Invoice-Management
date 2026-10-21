package io.consulting.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PaymentRequest(
    @NotBlank String amount,
    @NotBlank String received,
    @NotBlank String method,
    @NotBlank String reference
) {}
