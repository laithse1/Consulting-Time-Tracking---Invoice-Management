package io.consulting.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record InvoiceDraftRequest(
    @NotNull @Positive Integer contractId,
    @NotBlank String periodStart,
    @NotBlank String periodEnd,
    String presentation,
    String adjustment,
    String adjustmentReason,
    String adjustmentApproval,
    Integer replacementFor,
    String revisionReason,
    String notes
) {}
