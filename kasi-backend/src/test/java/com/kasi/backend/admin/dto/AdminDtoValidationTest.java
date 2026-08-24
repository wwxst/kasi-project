package com.kasi.backend.admin.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("管理员 DTO 校验")
class AdminDtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("账号只允许 ASCII 字母和数字")
    void usernameAllowsOnlyAsciiLettersAndDigits() {
        assertThat(fields(create("admin123", "Password1!", "张三"))).isEmpty();
        assertThat(fields(create("admin_name", "Password1!", "张三"))).contains("username");
        assertThat(fields(create("管理员", "Password1!", "张三"))).contains("username");
    }

    @Test
    @DisplayName("管理员密码只允许 8 到 72 位 ASCII 可见字符")
    void passwordAllowsVisibleAsciiWithinLengthBounds() {
        assertThat(fields(create("admin1", "12345678", "张三"))).isEmpty();
        assertThat(fields(create("admin1", "Admin@123", "张三"))).isEmpty();
        assertThat(fields(create("admin1", "pass word", "张三"))).contains("password");
        assertThat(fields(create("admin1", "密码123456", "张三"))).contains("password");
        assertThat(fields(create("admin1", "a".repeat(73), "张三"))).contains("password");
    }

    @Test
    @DisplayName("真实姓名不允许空白字符")
    void realNameRejectsWhitespace() {
        assertThat(fields(create("admin1", "Password1!", "张 三"))).contains("realName");
    }

    @Test
    @DisplayName("手机号邮箱允许首尾空格并校验规范化值")
    void optionalContactFieldsValidateTrimmedValues() {
        CreateAdminDTO valid = create("admin1", "Password1!", "张三");
        valid.setMobile(" 13800138000 ");
        valid.setEmail(" Admin@Example.COM ");
        assertThat(fields(valid)).isEmpty();

        valid.setMobile("138 00138000");
        valid.setEmail("admin @example.com");
        assertThat(fields(valid)).contains("mobile", "email");
    }

    @Test
    @DisplayName("分页和状态必须在允许范围内")
    void pageAndStatusStayWithinBounds() {
        AdminPageQueryDTO query = new AdminPageQueryDTO();
        query.setPage(0);
        query.setSize(101);
        assertThat(fields(query)).contains("page", "size");

        UpdateAdminStatusDTO status = new UpdateAdminStatusDTO();
        status.setStatus(2);
        assertThat(fields(status)).contains("status");
    }

    @Test
    @DisplayName("管理员修改本人密码不要求原密码")
    void changePasswordDoesNotRequireOldPassword() {
        AdminChangePasswordDTO request = new AdminChangePasswordDTO();
        request.setNewPassword("NewPassword1!");
        request.setConfirmPassword("NewPassword1!");

        assertThat(fields(request)).isEmpty();
    }

    private CreateAdminDTO create(String username, String password, String realName) {
        CreateAdminDTO request = new CreateAdminDTO();
        request.setUsername(username);
        request.setPassword(password);
        request.setConfirmPassword(password);
        request.setRealName(realName);
        return request;
    }

    private Set<String> fields(Object value) {
        return validator.validate(value).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
