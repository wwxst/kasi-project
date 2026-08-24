package com.kasi.backend.drama.dto;

import com.kasi.backend.drama.enums.PromotionCommissionScope;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateDramaPromotionMetadataDTO {
    @NotNull
    @Size(max = 2)
    private List<@NotNull PromotionCommissionScope> commissionScopes;

    @Size(max = 2000)
    private String promotionDescription;
}
