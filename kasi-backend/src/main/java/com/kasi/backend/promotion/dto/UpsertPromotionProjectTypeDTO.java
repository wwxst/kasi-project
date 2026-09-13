package com.kasi.backend.promotion.dto;

import com.kasi.backend.promotion.enums.PromotionProjectStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Locale;

@Data
public class UpsertPromotionProjectTypeDTO {
    @NotBlank
    @Size(max = 32)
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]*")
    private String code;

    @NotBlank
    @Size(max = 64)
    private String name;

    @NotNull
    private PromotionProjectStatus status = PromotionProjectStatus.ENABLED;

    @NotNull
    @Min(0)
    private Integer sortOrder;

    public void setCode(String code) {
        this.code = code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }
}
