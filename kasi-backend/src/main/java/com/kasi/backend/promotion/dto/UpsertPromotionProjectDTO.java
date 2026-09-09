package com.kasi.backend.promotion.dto;

import com.kasi.backend.promotion.enums.PromotionProjectStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UpsertPromotionProjectDTO {
    @NotBlank
    @Size(max = 128)
    private String name;

    @NotBlank
    @Size(max = 1024)
    private String projectDocumentUrl;

    @NotNull
    private PromotionProjectStatus status = PromotionProjectStatus.ENABLED;

    @NotNull
    @Min(0)
    private Integer sortOrder;

    private MultipartFile coverFile;
}
