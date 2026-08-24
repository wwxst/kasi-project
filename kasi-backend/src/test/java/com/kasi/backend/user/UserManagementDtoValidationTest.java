package com.kasi.backend.user;

import com.kasi.backend.user.dto.CreateUserDTO;
import com.kasi.backend.user.dto.UpdateUserDTO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("推广用户管理参数")
class UserManagementDtoValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("新增用户至少需要手机号或邮箱")
    void createRequiresContact() {
        CreateUserDTO request = new CreateUserDTO();
        request.setPassword("password1");
        request.setConfirmPassword("password1");
        request.setNickname("推广用户");
        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    @DisplayName("编辑用户不能同时清空手机号和邮箱")
    void updateRequiresContact() {
        UpdateUserDTO request = new UpdateUserDTO();
        request.setNickname("推广用户");
        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    @DisplayName("保留邮箱时允许用空字符串清空手机号")
    void updateAllowsBlankOptionalMobile() {
        UpdateUserDTO request = new UpdateUserDTO();
        request.setMobile("   ");
        request.setEmail(" user@example.com ");
        request.setNickname("推广用户");
        assertThat(validator.validate(request)).isEmpty();
    }
}
