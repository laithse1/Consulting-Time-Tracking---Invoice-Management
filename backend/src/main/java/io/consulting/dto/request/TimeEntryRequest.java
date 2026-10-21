package io.consulting.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TimeEntryRequest(
    Integer id,
    @NotNull @Positive Integer contractId,
    Integer projectId,
    String workDate,
    String startTime,
    @Min(0) @Max(86400) Integer seconds,
    @NotBlank String category,
    @NotBlank String description,
    String repository,
    String reference,
    String notes,
    @NotNull @Min(0) @Max(1) Integer billable,
    @NotNull @Min(0) @Max(1) Integer draft
) {}
