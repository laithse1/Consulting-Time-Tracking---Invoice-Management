package io.consulting.domain;

import java.time.LocalDate;
import java.util.Objects;

/** Inclusive service/report dates. Range length is validated by the relevant business service. */
public record WorkPeriod(LocalDate from, LocalDate to) {
    public WorkPeriod {
        Objects.requireNonNull(from, "Period start is required");
        Objects.requireNonNull(to, "Period end is required");
        DomainValues.require(!to.isBefore(from), "Period end precedes its start");
    }
}
