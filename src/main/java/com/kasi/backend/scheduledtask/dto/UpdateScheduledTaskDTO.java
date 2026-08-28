package com.kasi.backend.scheduledtask.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.kasi.backend.scheduledtask.enums.ScheduledTaskCycleType;
import java.time.LocalTime;
import lombok.Data;

@Data
public class UpdateScheduledTaskDTO {
    private ScheduledTaskCycleType cycleType;

    /** 兼容旧客户端；新客户端应提交 intervalValue。 */
    @Min(5)
    @Max(1440)

    @Min(1)
    @Max(1440)
    private Integer intervalValue;

    @Min(0)
    @Max(23)
    private Integer intervalHoursPart;

    @Min(0)
    @Max(59)
    private Integer intervalMinutesPart;

    private LocalTime timeOfDay;

    @Min(1)
    @Max(7)
    private Integer dayOfWeek;

    @Min(1)
    @Max(31)
    private Integer dayOfMonth;

    @Min(1)
    @Max(12)
    private Integer monthOfYear;

    @NotBlank
    @Size(max = 255)
    private String description;

    @NotNull
    private Boolean enabled;

    @AssertTrue(message = "周期类型和周期值不匹配")
    public boolean isScheduleValid() {
        if (cycleType == null) {
            return false;
        }
        if (cycleType.name().startsWith("INTERVAL_")) {
            return intervalValue != null;
        }
        return timeOfDay != null && (cycleType.name().equals("DAILY")
                || (cycleType.name().equals("WEEKLY") && dayOfWeek != null)
                || (cycleType.name().equals("MONTHLY") && dayOfMonth != null)
                || (cycleType.name().equals("YEARLY") && dayOfMonth != null && monthOfYear != null));
    }
}
