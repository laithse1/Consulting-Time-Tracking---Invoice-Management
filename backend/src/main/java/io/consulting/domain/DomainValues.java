package io.consulting.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.Objects;

/** Shared boundary conversions and exact billing arithmetic. */
public final class DomainValues {

    private DomainValues() {}

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new DomainValidationException(message);
        }
    }

    public static String text(Map<String, Object> values, String key) {
        return Objects.toString(values.get(key), "").trim();
    }

    public static int integer(Map<String, Object> values, String key) {
        try {
            return Integer.parseInt(text(values, key));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    public static BigDecimal money(Object value) {
        try {
            return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid monetary amount");
        }
    }

    public static BigDecimal hours(long seconds) {
        return BigDecimal.valueOf(seconds).divide(
            BigDecimal.valueOf(3600),
            4,
            RoundingMode.HALF_UP
        );
    }

    public static BigDecimal charge(long seconds, BigDecimal hourlyRate) {
        return BigDecimal.valueOf(seconds)
            .multiply(hourlyRate)
            .divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
    }

    public static LocalDate weekStart(LocalDate date, int firstDayOfWeek) {
        return date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.of(firstDayOfWeek)));
    }
}
