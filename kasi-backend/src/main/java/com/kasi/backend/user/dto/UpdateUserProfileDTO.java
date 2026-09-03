package com.kasi.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserProfileDTO {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 64, message = "昵称长度不能超过64位")
    private String nickname;

    @Size(max = 64, message = "真实姓名长度不能超过64位")
    private String realName;
}
