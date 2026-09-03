package com.kasi.backend.promotion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreatePromotionLinkDTO {
    @NotNull
    private Long providerId;
    @NotNull
    private Long dramaId;
    @NotEmpty
    @Size(max = 4)
    private List<@Pattern(regexp = "TIKTOK|YOUTUBE|FACEBOOK|INSTAGRAM") String> mediaTypes;
    @Pattern(regexp = "LANDING|ONELINK")
    private String linkVariant;
    @NotBlank
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    private String requestKey;
    @Size(max = 128)
    private String campaignName;
}
