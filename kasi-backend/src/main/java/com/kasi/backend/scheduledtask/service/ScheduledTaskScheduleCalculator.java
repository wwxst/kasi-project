package com.kasi.backend.scheduledtask.service;

import com.kasi.backend.scheduledtask.enums.ScheduledTaskCycleType;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;

@Component
public class ScheduledTaskScheduleCalculator {

    public LocalDateTime nextRun(ScheduledTaskCycleType cycleType,
                                 Integer intervalValue,
                                 LocalTime timeOfDay,
                                 Integer dayOfWeek,
                                 Integer dayOfMonth,
                                 Integer monthOfYear,
                                 LocalDateTime base) {
        return nextRun(cycleType, intervalValue, 0, 0, timeOfDay,
                dayOfWeek, dayOfMonth, monthOfYear, base);
    }

    public LocalDateTime nextRun(ScheduledTaskCycleType cycleType,
                                 Integer intervalValue,
                                 Integer intervalHoursPart,
                                 Integer intervalMinutesPart,
                                 LocalTime timeOfDay,
                                 Integer dayOfWeek,
                                 Integer dayOfMonth,
                                 Integer monthOfYear,
                                 LocalDateTime base) {
        if (cycleType == null || base == null) {
            throw new IllegalArgumentException("cycleType and base are required");
        }
        return switch (cycleType) {
            case INTERVAL_SECONDS -> base.plusSeconds(requirePositive(intervalValue));
            case INTERVAL_MINUTES -> base.plusMinutes(requirePositive(intervalValue));
            case INTERVAL_HOURS -> base.plusHours(requirePositive(intervalValue))
                    .plusMinutes(requirePart(intervalMinutesPart, 0, 59, "intervalMinutesPart"));
            case INTERVAL_DAYS -> base.plusDays(requirePositive(intervalValue))
                    .plusHours(requirePart(intervalHoursPart, 0, 23, "intervalHoursPart"))
                    .plusMinutes(requirePart(intervalMinutesPart, 0, 59, "intervalMinutesPart"));
            case DAILY -> nextDaily(timeOfDay, base);
            case WEEKLY -> nextWeekly(timeOfDay, dayOfWeek, base);
            case MONTHLY -> nextMonthly(timeOfDay, dayOfMonth, base);
            case YEARLY -> nextYearly(timeOfDay, monthOfYear, dayOfMonth, base);
        };
    }

    private LocalDateTime nextDaily(LocalTime time, LocalDateTime base) {
        requireTime(time);
        LocalDateTime candidate = base.toLocalDate().atTime(time);
        return candidate.isAfter(base) ? candidate : candidate.plusDays(1);
    }

    private LocalDateTime nextWeekly(LocalTime time, Integer dayOfWeek, LocalDateTime base) {
        requireTime(time);
        requireRange(dayOfWeek, 1, 7, "dayOfWeek");
        LocalDate date = base.toLocalDate();
        int daysAhead = (dayOfWeek - date.getDayOfWeek().getValue() + 7) % 7;
        LocalDateTime candidate = date.plusDays(daysAhead).atTime(time);
        return candidate.isAfter(base) ? candidate : candidate.plusDays(7);
    }

    private LocalDateTime nextMonthly(LocalTime time, Integer dayOfMonth, LocalDateTime base) {
        requireTime(time);
        requireRange(dayOfMonth, 1, 31, "dayOfMonth");
        YearMonth month = YearMonth.from(base);
        LocalDateTime candidate = month.atDay(Math.min(dayOfMonth, month.lengthOfMonth())).atTime(time);
        if (!candidate.isAfter(base)) {
            month = month.plusMonths(1);
            candidate = month.atDay(Math.min(dayOfMonth, month.lengthOfMonth())).atTime(time);
        }
        return candidate;
    }

    private LocalDateTime nextYearly(LocalTime time, Integer monthOfYear,
                                     Integer dayOfMonth, LocalDateTime base) {
        requireTime(time);
        requireRange(monthOfYear, 1, 12, "monthOfYear");
        requireRange(dayOfMonth, 1, 31, "dayOfMonth");
        int year = base.getYear();
        LocalDateTime candidate = dateForYear(year, monthOfYear, dayOfMonth, time);
        if (!candidate.isAfter(base)) {
            candidate = dateForYear(year + 1, monthOfYear, dayOfMonth, time);
        }
        return candidate;
    }

    private LocalDateTime dateForYear(int year, int month, int day, LocalTime time) {
        YearMonth yearMonth = YearMonth.of(year, month);
        return yearMonth.atDay(Math.min(day, yearMonth.lengthOfMonth())).atTime(time);
    }

    private int requirePositive(Integer value) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException("intervalValue must be positive");
        }
        return value;
    }

    private int requirePart(Integer value, int min, int max, String field) {
        int actual = value == null ? 0 : value;
        requireRange(actual, min, max, field);
        return actual;
    }

    private void requireTime(LocalTime value) {
        if (value == null) {
            throw new IllegalArgumentException("timeOfDay is required");
        }
    }

    private void requireRange(Integer value, int min, int max, String field) {
        if (value == null || value < min || value > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
        }
    }
}
