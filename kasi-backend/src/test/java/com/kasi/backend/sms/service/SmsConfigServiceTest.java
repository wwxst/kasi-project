package com.kasi.backend.sms.service;

import com.kasi.backend.BaseAuthTest;
import com.kasi.backend.common.enums.VerificationScene;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.VerificationDeliveryUnavailableException;
import com.kasi.backend.sms.dto.UpdateSmsConfigDTO;
import com.kasi.backend.sms.entity.SmsConfig;
import com.kasi.backend.sms.entity.SmsRuntimeConfig;
import com.kasi.backend.sms.mapper.SmsConfigMapper;
import com.kasi.backend.sms.vo.SmsConfigVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("短信配置服务")
class SmsConfigServiceTest extends BaseAuthTest {

    @Autowired
    private SmsConfigService smsConfigService;

    @Autowired
    private SmsConfigMapper smsConfigMapper;

    @Test
    @DisplayName("未配置时返回空状态且运行时安全失败")
    void getEmptyConfigAndRejectRuntimeUse() {
        SmsConfigVO config = smsConfigService.getConfig();

        assertThat(config.isConfigured()).isFalse();
        assertThat(config.isAccessKeyIdConfigured()).isFalse();
        assertThat(config.isAccessKeySecretConfigured()).isFalse();
        assertThat(config.isEnabled()).isFalse();
        assertThatThrownBy(() -> smsConfigService.requireRuntimeConfig(VerificationScene.REGISTER))
                .isInstanceOf(VerificationDeliveryUnavailableException.class);
    }

    @Test
    @DisplayName("首次保存加密密钥且响应不包含密钥字段")
    void createEncryptsCredentialsWithoutExposure() {
        SmsConfigVO result = smsConfigService.update(1L,
                request("ak-id", "ak-secret", true));

        SmsConfig stored = smsConfigMapper.findSingleton();
        assertThat(stored.getAccessKeyIdCiphertext()).startsWith("v1:").doesNotContain("ak-id");
        assertThat(stored.getAccessKeySecretCiphertext()).startsWith("v1:").doesNotContain("ak-secret");
        assertThat(result.isConfigured()).isTrue();
        assertThat(result.isAccessKeyIdConfigured()).isTrue();
        assertThat(result.isAccessKeySecretConfigured()).isTrue();
        assertThat(result.getSignName()).isEqualTo("卡司");
        assertThat(result.getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("accessKeyId", "accessKeySecret",
                        "accessKeyIdCiphertext", "accessKeySecretCiphertext");
    }

    @Test
    @DisplayName("已有配置留空密钥时保留原密文")
    void updateRetainsBlankCredentials() {
        smsConfigService.update(1L, request("ak-id", "ak-secret", false));
        SmsConfig before = smsConfigMapper.findSingleton();

        SmsConfigVO result = smsConfigService.update(2L, request("  ", null, true));

        SmsConfig after = smsConfigMapper.findSingleton();
        assertThat(after.getAccessKeyIdCiphertext()).isEqualTo(before.getAccessKeyIdCiphertext());
        assertThat(after.getAccessKeySecretCiphertext())
                .isEqualTo(before.getAccessKeySecretCiphertext());
        assertThat(after.getUpdatedBy()).isEqualTo(2L);
        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("三个业务场景使用各自模板并解密运行时密钥")
    void runtimeConfigSelectsSceneTemplate() {
        smsConfigService.update(1L, request("ak-id", "ak-secret", true));

        SmsRuntimeConfig register = smsConfigService.requireRuntimeConfig(VerificationScene.REGISTER);
        SmsRuntimeConfig login = smsConfigService.requireRuntimeConfig(VerificationScene.LOGIN);
        SmsRuntimeConfig reset = smsConfigService.requireRuntimeConfig(VerificationScene.RESET_PASSWORD);

        assertThat(register.accessKeyId()).isEqualTo("ak-id");
        assertThat(register.accessKeySecret()).isEqualTo("ak-secret");
        assertThat(register.templateCode()).isEqualTo("SMS_100");
        assertThat(login.templateCode()).isEqualTo("SMS_101");
        assertThat(reset.templateCode()).isEqualTo("SMS_102");
    }

    @Test
    @DisplayName("首次保存缺少AccessKey时拒绝写入")
    void createRequiresCompleteCredentials() {
        assertThatThrownBy(() -> smsConfigService.update(1L,
                request(null, "ak-secret", false)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(1006);
        assertThat(smsConfigMapper.findSingleton()).isNull();
    }

    @Test
    @DisplayName("停用配置不能用于发送")
    void disabledConfigCannotBeUsed() {
        smsConfigService.update(1L, request("ak-id", "ak-secret", false));

        assertThatThrownBy(() -> smsConfigService.requireRuntimeConfig(VerificationScene.LOGIN))
                .isInstanceOf(VerificationDeliveryUnavailableException.class);
    }

    private UpdateSmsConfigDTO request(String accessKeyId, String accessKeySecret, boolean enabled) {
        UpdateSmsConfigDTO request = new UpdateSmsConfigDTO();
        request.setAccessKeyId(accessKeyId);
        request.setAccessKeySecret(accessKeySecret);
        request.setSignName(" 卡司 ");
        request.setRegisterTemplateCode(" SMS_100 ");
        request.setLoginTemplateCode(" SMS_101 ");
        request.setResetPasswordTemplateCode(" SMS_102 ");
        request.setEnabled(enabled);
        return request;
    }
}
