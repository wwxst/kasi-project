package com.kasi.backend.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UserPageQueryDTO {

    @Min(value = 1, message = "页码不能小于1")
    private int page = 1;

    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能超过100")
    private int size = 20;

    private String keyword;
}
