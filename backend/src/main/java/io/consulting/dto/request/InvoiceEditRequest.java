package io.consulting.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record InvoiceEditRequest(
    @NotBlank String expectedToken,
    @NotBlank String periodStart,
    @NotBlank String periodEnd,
    @NotBlank String invoiceDate,
    @NotBlank String presentation,
    String adjustment,
    String adjustmentReason,
    String adjustmentApproval,
    String notes
) {}
