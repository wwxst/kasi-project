package com.kasi.backend;

import com.kasi.backend.admin.dto.AdminLoginDTO;
import com.kasi.backend.admin.vo.AdminLoginVO;
import com.kasi.backend.admin.vo.CurrentAdminVO;
import com.kasi.backend.auth.dto.ChangePasswordDTO;
import com.kasi.backend.user.dto.ResetPasswordDTO;
import com.kasi.backend.user.dto.SendVerificationCodeDTO;
import com.kasi.backend.user.dto.UserLoginDTO;
import com.kasi.backend.user.dto.UserRegisterDTO;
import com.kasi.backend.user.dto.VerifyVerificationCodeDTO;
import com.kasi.backend.user.vo.CurrentUserVO;
import com.kasi.backend.user.vo.UserLoginVO;
import com.kasi.backend.user.vo.VerifyCodeVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DtoVoStructureTest extends BaseAuthTest {

    @Test
    @DisplayName("请求模型使用 DTO 后缀且响应模型使用 VO 后缀")
    void requestAndResponseModelsFollowTraditionalJavaNaming() {
        List<Class<?>> dtoTypes = List.of(
                AdminLoginDTO.class,
                ChangePasswordDTO.class,
                ResetPasswordDTO.class,
                SendVerificationCodeDTO.class,
                UserLoginDTO.class,
                UserRegisterDTO.class,
                VerifyVerificationCodeDTO.class);
        List<Class<?>> voTypes = List.of(
                AdminLoginVO.class,
                CurrentAdminVO.class,
                CurrentUserVO.class,
                UserLoginVO.class,
                VerifyCodeVO.class);

        assertThat(dtoTypes).allSatisfy(type -> {
            assertThat(type.getSimpleName()).endsWith("DTO");
            assertThat(type.getPackageName()).endsWith(".dto");
        });
        assertThat(voTypes).allSatisfy(type -> {
            assertThat(type.getSimpleName()).endsWith("VO");
            assertThat(type.getPackageName()).endsWith(".vo");
        });
    }
}
