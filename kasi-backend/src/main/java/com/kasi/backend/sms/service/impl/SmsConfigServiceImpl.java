package com.kasi.backend.sms.service.impl;

import com.kasi.backend.common.crypto.CredentialCipher;
import com.kasi.backend.common.enums.VerificationScene;
import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.common.exception.VerificationDeliveryUnavailableException;
import com.kasi.backend.sms.dto.UpdateSmsConfigDTO;
import com.kasi.backend.sms.entity.SmsConfig;
import com.kasi.backend.sms.entity.SmsRuntimeConfig;
import com.kasi.backend.sms.mapper.SmsConfigMapper;
import com.kasi.backend.sms.service.SmsConfigService;
import com.kasi.backend.sms.vo.SmsConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SmsConfigServiceImpl implements SmsConfigService {

    private static final long SINGLETON_ID = 1L;

    private final SmsConfigMapper smsConfigMapper;
    private final CredentialCipher credentialCipher;

    @Override
    @Transactional(readOnly = true)
    public SmsConfigVO getConfig() {
        return toVO(smsConfigMapper.findSingleton());
    }

    @Override
    @Transactional
    public SmsConfigVO update(Long adminId, UpdateSmsConfigDTO request) {
        SmsConfig current = smsConfigMapper.findSingleton();
        String accessKeyId = trimToNull(request.getAccessKeyId());
        String accessKeySecret = trimToNull(request.getAccessKeySecret());
        if (current == null && (accessKeyId == null || accessKeySecret == null)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "首次配置必须填写完整AccessKey");
        }

        SmsConfig config = current == null ? new SmsConfig() : current;
        config.setId(SINGLETON_ID);
        if (accessKeyId != null) {
            config.setAccessKeyIdCiphertext(credentialCipher.encrypt(accessKeyId));
        }
        if (accessKeySecret != null) {
            config.setAccessKeySecretCiphertext(credentialCipher.encrypt(accessKeySecret));
        }
        config.setSignName(request.getSignName().trim());
        config.setRegisterTemplateCode(request.getRegisterTemplateCode().trim());
        config.setLoginTemplateCode(request.getLoginTemplateCode().trim());
        config.setResetPasswordTemplateCode(request.getResetPasswordTemplateCode().trim());
        config.setSmtpHost(trimToNull(request.getSmtpHost()));
        config.setSmtpPort(request.getSmtpPort());
        config.setSmtpUsername(trimToNull(request.getSmtpUsername()));
        config.setSmtpFromAddress(trimToNull(request.getSmtpFromAddress()));
        config.setEmailEnabled(Boolean.TRUE.equals(request.getEmailEnabled()) ? 1 : 0);
        String smtpPassword = trimToNull(request.getSmtpPassword());
        if (smtpPassword != null) config.setSmtpPasswordCiphertext(credentialCipher.encrypt(smtpPassword));
        config.setEnabled(Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0);
        config.setUpdatedBy(adminId);

        if (current == null) {
            config.setCreatedBy(adminId);
            if (smsConfigMapper.insert(config) != 1) {
                throw new IllegalStateException("短信配置创建未生效");
            }
        } else if (smsConfigMapper.update(config) != 1) {
            throw new IllegalStateException("短信配置更新未生效");
        }
        return toVO(smsConfigMapper.findSingleton());
    }

    @Override
    @Transactional(readOnly = true)
    public SmsRuntimeConfig requireRuntimeConfig(VerificationScene scene) {
        SmsConfig config = smsConfigMapper.findSingleton();
        if (!isComplete(config) || config.getEnabled() != 1) {
            throw new VerificationDeliveryUnavailableException();
        }

        String templateCode = switch (scene) {
            case REGISTER -> config.getRegisterTemplateCode();
            case LOGIN -> config.getLoginTemplateCode();
            case RESET_PASSWORD -> config.getResetPasswordTemplateCode();
        };
        try {
            return new SmsRuntimeConfig(
                    credentialCipher.decrypt(config.getAccessKeyIdCiphertext()),
                    credentialCipher.decrypt(config.getAccessKeySecretCiphertext()),
                    config.getSignName(),
                    templateCode);
        } catch (RuntimeException exception) {
            throw new VerificationDeliveryUnavailableException(exception);
        }
    }

    private SmsConfigVO toVO(SmsConfig config) {
        if (config == null) {
            return SmsConfigVO.builder().build();
        }
        return SmsConfigVO.builder()
                .configured(isComplete(config))
                .accessKeyIdConfigured(hasText(config.getAccessKeyIdCiphertext()))
                .accessKeySecretConfigured(hasText(config.getAccessKeySecretCiphertext()))
                .signName(config.getSignName())
                .registerTemplateCode(config.getRegisterTemplateCode())
                .loginTemplateCode(config.getLoginTemplateCode())
                .resetPasswordTemplateCode(config.getResetPasswordTemplateCode())
                .enabled(config.getEnabled() == 1)
                .smtpHost(config.getSmtpHost()).smtpPort(config.getSmtpPort()).smtpUsername(config.getSmtpUsername()).smtpPasswordConfigured(hasText(config.getSmtpPasswordCiphertext())).smtpFromAddress(config.getSmtpFromAddress()).emailEnabled(config.getEmailEnabled() == 1)
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    private boolean isComplete(SmsConfig config) {
        return config != null
                && hasText(config.getAccessKeyIdCiphertext())
                && hasText(config.getAccessKeySecretCiphertext())
                && hasText(config.getSignName())
                && hasText(config.getRegisterTemplateCode())
                && hasText(config.getLoginTemplateCode())
                && hasText(config.getResetPasswordTemplateCode());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
