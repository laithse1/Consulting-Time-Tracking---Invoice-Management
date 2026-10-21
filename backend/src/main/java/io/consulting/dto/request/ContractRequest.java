package io.consulting.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ContractRequest(
    Integer id,
    @NotNull @Positive Integer clientId,
    @NotBlank String contractor,
    @NotBlank String startDate,
    String endDate,
    @NotBlank String rate,
    Integer minimumHours,
    Integer maximumHours,
    Integer paymentDays,
    Integer disputeDays,
    Integer weekStart,
    String paymentInstructions,
    String contractorAddress
) {}
