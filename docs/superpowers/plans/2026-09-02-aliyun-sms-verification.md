# Aliyun SMS Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 接通阿里云手机验证码，并让超级管理员在管理后台配置密钥、签名和注册、登录、忘记密码三个模板，同时让用户端完成手机号注册、验证码登录和忘记密码。

**Architecture:** 在 `com.kasi.backend.sms` 中建立单例短信配置、管理 API、阿里云网关和生产验证码发送器；现有验证码服务继续负责 Redis 限流、哈希和原子消费。`kasi-admin-web` 只向超级管理员提供配置页，`kasi-user-web` 复用当前登录页面承载三条手机号认证流程，邮箱只保留密码登录。

**Tech Stack:** Java 25, Spring Boot 4, MyBatis, Redis, AES-GCM, Alibaba Cloud `dysmsapi20170525:4.1.1`, JUnit 5, Spring MockMvc, React 19, TypeScript 6, Ant Design 6, TDesign React, Vitest 4.

---

## Execution Constraints

- 从根目录 `E:/JavaProjects/kasi-project` 执行所有 Git 操作。
- 当前工作树包含大量用户未提交改动；每个提交只能按任务列出的路径逐文件暂存，禁止 `git add .`。
- 对任务开始前已经 dirty 的文件，先保存基线 diff，提交时使用 `git add -p -- <path>` 只选本任务 hunk；如果无法无歧义隔离，不提交该文件并报告，不得把用户 hunk 一并暂存。
- 不从当前 `HEAD` 新建实现 worktree；那会丢失本任务依赖的用户端当前未提交结构。只有用户先冻结并提交当前并行改动后，才可切换到独立 worktree。
- 实施前重新运行 `git status --short --branch`，并阅读三个子项目的 README/AGENTS。
- 不创建 Flyway 或兼容迁移；只更新唯一初始化 SQL 和 H2 测试 schema。
- 不提交真实 AccessKey、签名、模板 Code、手机号或本地 `.env`。
- 不增加邮箱验证码、短信发送历史、费用统计、多供应商抽象、配置历史、配置删除或测试短信端点。
- 没有真实阿里云配置时，只能声明自动化 Gate 通过；真实短信送达保持人工验收待配置。

## File Map

```text
kasi-backend/src/main/java/com/kasi/backend/common/crypto/
  CredentialCipher.java                       通用凭据加解密接口
  AesGcmCredentialCipher.java                 保持 v1 密文格式的 AES-GCM 实现

kasi-backend/src/main/java/com/kasi/backend/sms/
  controller/SmsConfigAdminController.java    超级管理员配置 API
  dto/UpdateSmsConfigDTO.java                 配置写请求
  entity/SmsConfig.java                       system_sms_config 持久化对象
  entity/SmsRuntimeConfig.java                解密后的单次发送配置
  gateway/AliyunSmsGateway.java               阿里云官方 SDK 实现
  mapper/SmsConfigMapper.java                 单表 Mapper
  service/SmsConfigService.java               配置服务接口
  service/impl/SmsConfigServiceImpl.java      加密保存、运行时解密和模板选择
  service/impl/AliyunSmsVerificationCodeSender.java 生产验证码发送器
  vo/SmsConfigVO.java                         不含密钥内容的管理响应

kasi-admin-web/src/features/sms-config/       管理端类型、API 与 API 测试
kasi-admin-web/src/pages/system/              短信配置页面、样式与页面测试
kasi-user-web/src/features/auth/              用户认证 API、类型与手机认证表单
kasi-user-web/src/pages/LoginPage.tsx          组合密码、验证码、注册和找回流程
```

### Task 1: Extract the shared AES-GCM credential cipher

**Files:**
- Create: `kasi-backend/src/main/java/com/kasi/backend/common/crypto/CredentialCipher.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/common/crypto/AesGcmCredentialCipher.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/common/config/CredentialProperties.java`
- Create: `kasi-backend/src/test/java/com/kasi/backend/common/crypto/CredentialCipherTest.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/service/impl/ProviderConnectionServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/provider/service/impl/ProviderRuntimeConnectionServiceImpl.java`
- Delete: `kasi-backend/src/main/java/com/kasi/backend/provider/service/ProviderCredentialCipher.java`
- Delete: `kasi-backend/src/main/java/com/kasi/backend/provider/service/impl/AesGcmProviderCredentialCipher.java`
- Delete: `kasi-backend/src/main/java/com/kasi/backend/provider/config/ProviderCredentialProperties.java`
- Delete: `kasi-backend/src/test/java/com/kasi/backend/provider/service/ProviderCredentialCipherTest.java`

- [ ] **Step 1: Write the failing shared-cipher test**

```java
@Test
@DisplayName("加密结果保持v1格式并可解密")
void encryptRoundTrip() {
    CredentialProperties properties = new CredentialProperties();
    properties.setMasterKey(Base64.getEncoder().encodeToString(new byte[32]));
    CredentialCipher cipher = new AesGcmCredentialCipher(properties);
    String ciphertext = cipher.encrypt("secret-value");
    assertThat(ciphertext).startsWith("v1:").doesNotContain("secret-value");
    assertThat(cipher.decrypt(ciphertext)).isEqualTo("secret-value");
}
```

- [ ] **Step 2: Run the test and confirm the expected compile failure**

```powershell
cd kasi-backend
.\mvnw.cmd -Dtest=CredentialCipherTest test
```

Expected: FAIL because `com.kasi.backend.common.crypto.CredentialCipher` does not exist.

- [ ] **Step 3: Move the cipher without changing the external property or ciphertext format**

```java
package com.kasi.backend.common.crypto;

public interface CredentialCipher {
    String encrypt(String plaintext);
    String decrypt(String ciphertext);
}
```

Move the complete existing AES-GCM body to `AesGcmCredentialCipher`, preserving `AES/GCM/NoPadding`, the 12-byte IV, 128-bit tag and `v1:` prefix. Bind `CredentialProperties` to the existing `app.provider-credentials` prefix so deployments do not need a new master-key variable. Replace both provider service dependencies with `CredentialCipher`.

- [ ] **Step 4: Run cipher and provider regression tests**

```powershell
.\mvnw.cmd --% -Dtest=CredentialCipherTest,ProviderConnectionServiceTest,ProviderRuntimeConnectionServiceTest test
```

Expected: PASS with existing provider ciphertext still decryptable.

- [ ] **Step 5: Commit only the cipher extraction**

```powershell
git add -- kasi-backend/src/main/java/com/kasi/backend/common/crypto kasi-backend/src/main/java/com/kasi/backend/common/config/CredentialProperties.java kasi-backend/src/test/java/com/kasi/backend/common/crypto/CredentialCipherTest.java kasi-backend/src/main/java/com/kasi/backend/provider/service/impl/ProviderConnectionServiceImpl.java kasi-backend/src/main/java/com/kasi/backend/provider/service/impl/ProviderRuntimeConnectionServiceImpl.java kasi-backend/src/main/java/com/kasi/backend/provider/service/ProviderCredentialCipher.java kasi-backend/src/main/java/com/kasi/backend/provider/service/impl/AesGcmProviderCredentialCipher.java kasi-backend/src/main/java/com/kasi/backend/provider/config/ProviderCredentialProperties.java kasi-backend/src/test/java/com/kasi/backend/provider/service/ProviderCredentialCipherTest.java
git diff --cached --check
git commit -m "refactor: share credential encryption"
```

### Task 2: Add SMS configuration persistence and service

**Files:**
- Modify: `kasi-backend/src/main/resources/db/kasi_promotion.sql`
- Modify: `kasi-backend/src/test/resources/test-schema.sql`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/BaseAuthTest.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/common/exception/ErrorCode.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/common/exception/GlobalExceptionHandler.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/common/exception/VerificationDeliveryUnavailableException.java`
- Create: `kasi-backend/src/test/java/com/kasi/backend/SmsConfigMigrationTest.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/sms/entity/SmsConfig.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/sms/entity/SmsRuntimeConfig.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/sms/mapper/SmsConfigMapper.java`
- Create: `kasi-backend/src/main/resources/mapper/SmsConfigMapper.xml`
- Create: `kasi-backend/src/main/java/com/kasi/backend/sms/dto/UpdateSmsConfigDTO.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/sms/vo/SmsConfigVO.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/sms/service/SmsConfigService.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/sms/service/impl/SmsConfigServiceImpl.java`
- Create: `kasi-backend/src/test/java/com/kasi/backend/sms/service/SmsConfigServiceTest.java`

- [ ] **Step 1: Write the failing schema contract**

```java
@Test
@DisplayName("初始化脚本创建单例短信配置表且不植入凭据")
void createsSmsConfigWithoutCredentials() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.h2.Driver");
    dataSource.setUrl("jdbc:h2:mem:sms_config_" + UUID.randomUUID()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
    dataSource.setUsername("sa");
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
            new ClassPathResource("db/kasi_promotion.sql"));
    populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
    populator.execute(dataSource);
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'SYSTEM_SMS_CONFIG'",
            Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_sms_config", Integer.class))
            .isZero();
}
```

- [ ] **Step 2: Run the migration test and confirm it fails**

```powershell
.\mvnw.cmd -Dtest=SmsConfigMigrationTest test
```

- [ ] **Step 3: Add the production and H2 table definitions**

```sql
CREATE TABLE `system_sms_config` (
    `id` BIGINT UNSIGNED NOT NULL COMMENT '固定单例配置ID',
    `access_key_id_ciphertext` VARCHAR(1024) NOT NULL COMMENT 'AccessKey ID AES-GCM密文',
    `access_key_secret_ciphertext` VARCHAR(1024) NOT NULL COMMENT 'AccessKey Secret AES-GCM密文',
    `sign_name` VARCHAR(64) NOT NULL COMMENT '阿里云短信签名',
    `register_template_code` VARCHAR(64) NOT NULL COMMENT '注册验证码模板Code',
    `login_template_code` VARCHAR(64) NOT NULL COMMENT '验证码登录模板Code',
    `reset_password_template_code` VARCHAR(64) NOT NULL COMMENT '忘记密码模板Code',
    `enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '0停用 1启用',
    `created_by` BIGINT UNSIGNED NOT NULL COMMENT '创建管理员逻辑关联ID',
    `updated_by` BIGINT UNSIGNED NOT NULL COMMENT '更新管理员逻辑关联ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `ck_system_sms_config_singleton` CHECK (`id` = 1),
    CONSTRAINT `ck_system_sms_config_enabled` CHECK (`enabled` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='阿里云短信当前配置';
```

Mirror the columns in `test-schema.sql` without MySQL-only `ON UPDATE`. Add `DELETE FROM system_sms_config` to `BaseAuthTest` before administrator cleanup.

- [ ] **Step 4: Write failing service tests for encryption and retain-on-blank updates**

```java
SmsConfigVO result = service.update(1L, completeRequest("ak-id", "ak-secret", true));
SmsConfig stored = mapper.findSingleton();
assertThat(stored.getAccessKeyIdCiphertext()).doesNotContain("ak-id");
assertThat(stored.getAccessKeySecretCiphertext()).doesNotContain("ak-secret");
assertThat(result.isAccessKeyIdConfigured()).isTrue();
assertThat(result.isAccessKeySecretConfigured()).isTrue();
```

Save once, then update with both AccessKey fields `null`; assert both original ciphertext values remain unchanged.

- [ ] **Step 5: Implement mapper, DTO, VO and service**

```java
@Data
public class UpdateSmsConfigDTO {
    @Size(max = 128)
    private String accessKeyId;
    @Size(max = 256)
    private String accessKeySecret;
    @NotBlank
    @Size(max = 64)
    private String signName;
    @NotBlank
    @Pattern(regexp = "SMS_[0-9]+", message = "注册模板Code格式不正确")
    private String registerTemplateCode;
    @NotBlank
    @Pattern(regexp = "SMS_[0-9]+", message = "登录模板Code格式不正确")
    private String loginTemplateCode;
    @NotBlank
    @Pattern(regexp = "SMS_[0-9]+", message = "忘记密码模板Code格式不正确")
    private String resetPasswordTemplateCode;
    @NotNull
    private Boolean enabled;
}
```

```java
@Mapper
public interface SmsConfigMapper {
    SmsConfig findSingleton();
    int insert(SmsConfig config);
    int update(SmsConfig config);
}
```

The XML `findSingleton` selects `WHERE id = 1`. `insert` writes all fields including `created_by` and `updated_by`; `update` replaces ciphertext, sign, templates, enabled and `updated_by`, and sets `updated_at = CURRENT_TIMESTAMP`.

```java
public interface SmsConfigService {
    SmsConfigVO getConfig();
    SmsConfigVO update(Long adminId, UpdateSmsConfigDTO request);
    SmsRuntimeConfig requireRuntimeConfig(VerificationScene scene);
}
```

```java
public record SmsRuntimeConfig(
        String accessKeyId,
        String accessKeySecret,
        String signName,
        String templateCode) {
}

public class VerificationDeliveryUnavailableException extends RuntimeException {
    public VerificationDeliveryUnavailableException() {
        super("验证码发送服务不可用");
    }

    public VerificationDeliveryUnavailableException(Throwable cause) {
        super("验证码发送服务不可用", cause);
    }
}
```

The service always reads and writes `id = 1`. First save requires both AccessKey values; later blank values preserve ciphertext. Add `VERIFICATION_CODE_SEND_FAILED(4005, "验证码发送失败，请稍后重试")`. `requireRuntimeConfig` throws `VerificationDeliveryUnavailableException` for missing, disabled, incomplete or undecryptable configuration and selects exactly one template for `REGISTER`, `LOGIN` or `RESET_PASSWORD`. Map that exception to HTTP 503 with response code `4005` in `GlobalExceptionHandler`.

- [ ] **Step 6: Run focused persistence and service tests**

```powershell
.\mvnw.cmd --% -Dtest=SmsConfigMigrationTest,SmsConfigServiceTest test
```

- [ ] **Step 7: Commit the configuration domain**

```powershell
git add -- kasi-backend/src/main/java/com/kasi/backend/common/exception/VerificationDeliveryUnavailableException.java kasi-backend/src/test/java/com/kasi/backend/SmsConfigMigrationTest.java kasi-backend/src/main/java/com/kasi/backend/sms kasi-backend/src/main/resources/mapper/SmsConfigMapper.xml kasi-backend/src/test/java/com/kasi/backend/sms/service/SmsConfigServiceTest.java
git add -p -- kasi-backend/src/main/resources/db/kasi_promotion.sql kasi-backend/src/test/resources/test-schema.sql kasi-backend/src/test/java/com/kasi/backend/BaseAuthTest.java kasi-backend/src/main/java/com/kasi/backend/common/exception/ErrorCode.java kasi-backend/src/main/java/com/kasi/backend/common/exception/GlobalExceptionHandler.java
git diff --cached --check
git commit -m "feat: add encrypted sms configuration"
```

### Task 3: Expose the super-admin SMS configuration API

**Files:**
- Create: `kasi-backend/src/main/java/com/kasi/backend/sms/controller/SmsConfigAdminController.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/security/config/SecurityConfig.java`
- Create: `kasi-backend/src/test/java/com/kasi/backend/sms/controller/SmsConfigAdminControllerTest.java`

- [ ] **Step 1: Write failing authorization and non-disclosure tests**

```java
mockMvc.perform(put("/api/admin/system/sms-config")
        .header("Authorization", "Bearer " + loginAsAdmin())
        .contentType("application/json").content(completeJson()))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.accessKeyIdConfigured").value(true))
    .andExpect(jsonPath("$.data.accessKeySecretConfigured").value(true))
    .andExpect(jsonPath("$.data.accessKeyId").doesNotExist())
    .andExpect(jsonPath("$.data.accessKeySecret").doesNotExist());
```

Add a second test using `loginAsAdmin("operator", ADMIN_PASSWORD)` and require HTTP 403 for both GET and PUT.

- [ ] **Step 2: Run the controller test and confirm missing-route failures**

```powershell
.\mvnw.cmd -Dtest=SmsConfigAdminControllerTest test
```

- [ ] **Step 3: Implement the controller and explicit security rule**

```java
@RestController
@RequestMapping("/api/admin/system/sms-config")
@RequiredArgsConstructor
public class SmsConfigAdminController {
    private final SmsConfigService smsConfigService;

    @GetMapping
    public ApiResponse<SmsConfigVO> getConfig() {
        return ApiResponse.success(smsConfigService.getConfig());
    }

    @PutMapping
    public ApiResponse<SmsConfigVO> update(@Valid @RequestBody UpdateSmsConfigDTO request) {
        return ApiResponse.success(smsConfigService.update(AuthContextHolder.getAdminId(), request));
    }
}
```

Add `.requestMatchers("/api/admin/system/sms-config").hasRole("SUPER_ADMIN")` before the generic `/api/admin/**` rule.

- [ ] **Step 4: Run controller and provider-security regressions**

```powershell
.\mvnw.cmd --% -Dtest=SmsConfigAdminControllerTest,ProviderAdminControllerTest test
```

- [ ] **Step 5: Commit the admin API**

```powershell
git add -- kasi-backend/src/main/java/com/kasi/backend/sms/controller/SmsConfigAdminController.java kasi-backend/src/main/java/com/kasi/backend/security/config/SecurityConfig.java kasi-backend/src/test/java/com/kasi/backend/sms/controller/SmsConfigAdminControllerTest.java
git diff --cached --check
git commit -m "feat: expose sms configuration api"
```

### Task 4: Implement Aliyun delivery and Redis rollback

**Files:**
- Modify: `kasi-backend/pom.xml`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/auth/service/VerificationCodeSender.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/auth/service/impl/ConsoleVerificationCodeSender.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/auth/service/impl/VerificationCodeServiceImpl.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/auth/service/TestVerificationCodeSender.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/auth/service/VerificationCodeServiceTest.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/sms/gateway/SmsGateway.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/sms/gateway/SmsSendCommand.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/sms/gateway/SmsSendResult.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/sms/gateway/AliyunSmsGateway.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/sms/service/impl/AliyunSmsVerificationCodeSender.java`
- Create: `kasi-backend/src/test/java/com/kasi/backend/sms/service/AliyunSmsVerificationCodeSenderTest.java`

- [ ] **Step 1: Write failing scene-routing and rollback tests**

```java
when(configService.requireRuntimeConfig(VerificationScene.REGISTER))
        .thenReturn(new SmsRuntimeConfig("id", "secret", "卡司", "SMS_100"));
when(gateway.send(any())).thenReturn(new SmsSendResult("OK", "request-1"));

sender.send("13800138000", TargetType.MOBILE, VerificationScene.REGISTER, "123456");

verify(gateway).send(new SmsSendCommand(
        "id", "secret", "13800138000", "卡司", "SMS_100", "123456"));
```

In `VerificationCodeServiceTest`, make the sender throw `VerificationDeliveryUnavailableException`, then assert code, cooldown, fail and daily keys are absent after the exception.

- [ ] **Step 2: Run focused tests and confirm signature/bean failures**

```powershell
.\mvnw.cmd --% -Dtest=AliyunSmsVerificationCodeSenderTest,VerificationCodeServiceTest test
```

- [ ] **Step 3: Add the official SDK and typed sender contract**

```xml
<dependency>
    <groupId>com.aliyun</groupId>
    <artifactId>dysmsapi20170525</artifactId>
    <version>4.1.1</version>
</dependency>
```

```java
public interface VerificationCodeSender {
    void send(String target, TargetType targetType, VerificationScene scene, String code);
}
```

```java
public record SmsSendCommand(
        String accessKeyId,
        String accessKeySecret,
        String mobile,
        String signName,
        String templateCode,
        String code) {
}

public record SmsSendResult(String code, String requestId) {
}

public interface SmsGateway {
    SmsSendResult send(SmsSendCommand command);
}
```

Update local/test senders to accept both enums. The local sender may log the code only under `local`; production code must never log it.

- [ ] **Step 4: Implement the gateway and production sender**

```java
@Component
@RequiredArgsConstructor
public class AliyunSmsGateway implements SmsGateway {
    private final ObjectMapper objectMapper;

    @Override
    public SmsSendResult send(SmsSendCommand command) {
        try {
            Config config = new Config()
                    .setAccessKeyId(command.accessKeyId())
                    .setAccessKeySecret(command.accessKeySecret())
                    .setEndpoint("dysmsapi.aliyuncs.com");
            Client client = new Client(config);
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(command.mobile())
                    .setSignName(command.signName())
                    .setTemplateCode(command.templateCode())
                    .setTemplateParam(objectMapper.writeValueAsString(Map.of("code", command.code())));
            SendSmsResponse response = client.sendSms(request);
            return new SmsSendResult(response.getBody().getCode(), response.getBody().getRequestId());
        } catch (Exception exception) {
            throw new VerificationDeliveryUnavailableException(exception);
        }
    }
}
```

```java
@Service
@Profile("!local & !test")
@RequiredArgsConstructor
public class AliyunSmsVerificationCodeSender implements VerificationCodeSender {
    private final SmsConfigService configService;
    private final SmsGateway gateway;

    @Override
    public void send(String target, TargetType targetType, VerificationScene scene, String code) {
        if (targetType != TargetType.MOBILE) throw new VerificationDeliveryUnavailableException();
        SmsRuntimeConfig config = configService.requireRuntimeConfig(scene);
        SmsSendResult result = gateway.send(new SmsSendCommand(
                config.accessKeyId(), config.accessKeySecret(), target,
                config.signName(), config.templateCode(), code));
        if (!"OK".equals(result.code())) throw new VerificationDeliveryUnavailableException();
    }
}
```

- [ ] **Step 5: Add atomic Redis rollback and verify HTTP 503 mapping**

Add a Lua script that deletes code, cooldown and fail keys, then decrements the daily key or deletes it when the count reaches zero. Execute it only when delivery throws `VerificationDeliveryUnavailableException`, then rethrow.

Extend `TestVerificationCodeSender` with a one-shot `failNextSend()` flag. The HTTP mapping test calls that method before posting the public send request, proving the controller path returns 503 without replacing the test profile bean.

```java
mockMvc.perform(post("/api/user/auth/register/code")
        .contentType("application/json")
        .content("{\"target\":\"13800138000\"}"))
    .andExpect(status().isServiceUnavailable())
    .andExpect(jsonPath("$.code").value(4005));
```

- [ ] **Step 6: Run focused delivery tests**

```powershell
.\mvnw.cmd --% -Dtest=AliyunSmsVerificationCodeSenderTest,VerificationCodeServiceTest,UserAuthControllerTest test
```

Expected: PASS without a network request.

- [ ] **Step 7: Commit the delivery integration**

```powershell
git add -- kasi-backend/src/main/java/com/kasi/backend/sms/gateway kasi-backend/src/main/java/com/kasi/backend/sms/service/impl/AliyunSmsVerificationCodeSender.java kasi-backend/src/test/java/com/kasi/backend/sms/service/AliyunSmsVerificationCodeSenderTest.java
git add -p -- kasi-backend/pom.xml kasi-backend/src/main/java/com/kasi/backend/auth/service/VerificationCodeSender.java kasi-backend/src/main/java/com/kasi/backend/auth/service/impl/ConsoleVerificationCodeSender.java kasi-backend/src/main/java/com/kasi/backend/auth/service/impl/VerificationCodeServiceImpl.java kasi-backend/src/test/java/com/kasi/backend/auth/service/TestVerificationCodeSender.java kasi-backend/src/test/java/com/kasi/backend/auth/service/VerificationCodeServiceTest.java
git diff --cached --check
git commit -m "feat: send verification codes with aliyun sms"
```

### Task 5: Add mobile-only code login and preserve password login

**Files:**
- Create: `kasi-backend/src/main/java/com/kasi/backend/common/validation/Mobile.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/common/validation/MobileValidator.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/common/enums/VerificationScene.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/user/dto/UserRegisterDTO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/user/dto/SendVerificationCodeDTO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/user/dto/VerifyVerificationCodeDTO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/user/service/UserAuthService.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/user/service/impl/UserAuthServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/user/controller/UserAuthController.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/security/config/SecurityConfig.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/user/controller/UserAuthControllerTest.java`

- [ ] **Step 1: Write failing API tests for the three mobile flows**

```java
mockMvc.perform(post("/api/user/auth/login/code")
        .contentType("application/json").content("{\"target\":\"13800138000\"}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
String code = verificationCodeSender.latestCode("13800138000");

mockMvc.perform(post("/api/user/auth/login/code/verify")
        .contentType("application/json")
        .content("{\"target\":\"13800138000\",\"code\":\"" + code + "\"}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
    .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
```

Also assert email is rejected by registration and forgot-code DTO validation while `/api/user/auth/login` still accepts the existing email account. Cover unknown login/forgot targets not reaching the sender, wrong code and disabled account.

- [ ] **Step 2: Run the controller test and confirm missing-route failures**

```powershell
.\mvnw.cmd -Dtest=UserAuthControllerTest test
```

- [ ] **Step 3: Add mobile validation and the LOGIN scene**

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MobileValidator.class)
public @interface Mobile {
    String message() default "请输入有效的手机号";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

`MobileValidator` trims the value and matches `^1[3-9]\\d{9}$`. Replace `@PhoneOrEmail` only in registration/send/verify DTOs; keep `UserLoginDTO` unchanged. Add `LOGIN` to `VerificationScene`.

- [ ] **Step 4: Implement send and verify login methods**

```java
void sendLoginCode(SendVerificationCodeDTO request);
UserLoginVO loginWithCode(VerifyVerificationCodeDTO request, String clientIp);
```

```java
@Override
public void sendLoginCode(SendVerificationCodeDTO request) {
    String mobile = request.getTarget().trim();
    PromotionUser user = promotionUserMapper.findByMobile(mobile);
    if (user == null || user.getStatus() == UserStatus.DISABLED.getCode()) {
        verificationCodeService.reserveVerificationCode(mobile, VerificationScene.LOGIN);
        return;
    }
    verificationCodeService.sendVerificationCode(mobile, VerificationScene.LOGIN);
}

@Transactional
@Override
public UserLoginVO loginWithCode(VerifyVerificationCodeDTO request, String clientIp) {
    String mobile = request.getTarget().trim();
    verificationCodeService.verifyCode(mobile, VerificationScene.LOGIN, request.getCode());
    PromotionUser user = promotionUserMapper.findByAccountForUpdate(mobile);
    if (user == null) throw new BusinessException(ErrorCode.VERIFICATION_CODE_ERROR);
    if (user.getStatus() == UserStatus.DISABLED.getCode()) {
        throw new BusinessException(ErrorCode.USER_DISABLED);
    }
    return completeLogin(user, mobile, clientIp);
}
```

Extract only session creation, JWT creation, last-login update and VO mapping into `completeLogin`; password verification remains in `login`. Simplify self-registration to mobile persistence with `registerSource = "MOBILE"`.

- [ ] **Step 5: Add controller routes and anonymous security rules**

```java
@PostMapping("/login/code")
public ApiResponse<Void> sendLoginCode(@Valid @RequestBody SendVerificationCodeDTO request) {
    userAuthService.sendLoginCode(request);
    return ApiResponse.successMessage("验证码已发送");
}

@PostMapping("/login/code/verify")
public ApiResponse<UserLoginVO> loginWithCode(
        @Valid @RequestBody VerifyVerificationCodeDTO request,
        HttpServletRequest httpRequest) {
    return ApiResponse.success("登录成功",
            userAuthService.loginWithCode(request, httpRequest.getRemoteAddr()));
}
```

Permit `/api/user/auth/login/code/**` before the protected `/api/user/**` matcher.

- [ ] **Step 6: Run complete authentication tests**

```powershell
.\mvnw.cmd --% -Dtest=UserAuthControllerTest,VerificationCodeServiceTest,PasswordResetTokenServiceRedisTest test
```

- [ ] **Step 7: Commit the backend user flows**

```powershell
git add -- kasi-backend/src/main/java/com/kasi/backend/common/validation/Mobile.java kasi-backend/src/main/java/com/kasi/backend/common/validation/MobileValidator.java
git add -p -- kasi-backend/src/main/java/com/kasi/backend/common/enums/VerificationScene.java kasi-backend/src/main/java/com/kasi/backend/user/dto/UserRegisterDTO.java kasi-backend/src/main/java/com/kasi/backend/user/dto/SendVerificationCodeDTO.java kasi-backend/src/main/java/com/kasi/backend/user/dto/VerifyVerificationCodeDTO.java kasi-backend/src/main/java/com/kasi/backend/user/service/UserAuthService.java kasi-backend/src/main/java/com/kasi/backend/user/service/impl/UserAuthServiceImpl.java kasi-backend/src/main/java/com/kasi/backend/user/controller/UserAuthController.java kasi-backend/src/main/java/com/kasi/backend/security/config/SecurityConfig.java kasi-backend/src/test/java/com/kasi/backend/user/controller/UserAuthControllerTest.java
git diff --cached --check
git commit -m "feat: add mobile verification login"
```

### Task 6: Build the super-admin SMS configuration page

**Files:**
- Create: `kasi-admin-web/src/features/sms-config/smsConfigTypes.ts`
- Create: `kasi-admin-web/src/features/sms-config/smsConfigApi.ts`
- Create: `kasi-admin-web/src/features/sms-config/smsConfigApi.test.ts`
- Create: `kasi-admin-web/src/pages/system/SmsConfigPage.tsx`
- Create: `kasi-admin-web/src/pages/system/SmsConfigPage.test.tsx`
- Create: `kasi-admin-web/src/pages/system/sms-config-page.css`
- Modify: `kasi-admin-web/src/router/AppRouter.tsx`
- Modify: `kasi-admin-web/src/layouts/AdminLayout.tsx`
- Modify: `kasi-admin-web/src/layouts/AdminLayout.test.tsx`

- [ ] **Step 1: Write failing API tests**

```ts
const result = await updateSmsConfig(request)
expect(result.accessKeyIdConfigured).toBe(true)
expect(result).not.toHaveProperty('accessKeyId')
expect(result).not.toHaveProperty('accessKeySecret')
```

Use MSW to assert `GET/PUT /api/admin/system/sms-config` and the exact request body.

- [ ] **Step 2: Run the API test and confirm the missing-module failure**

```powershell
cd ..\kasi-admin-web
pnpm test -- src/features/sms-config/smsConfigApi.test.ts
```

- [ ] **Step 3: Implement types and API functions**

```ts
export interface SmsConfig {
  configured: boolean
  accessKeyIdConfigured: boolean
  accessKeySecretConfigured: boolean
  signName: string | null
  registerTemplateCode: string | null
  loginTemplateCode: string | null
  resetPasswordTemplateCode: string | null
  enabled: boolean
  updatedAt: string | null
}

export interface UpdateSmsConfigRequest {
  accessKeyId?: string
  accessKeySecret?: string
  signName: string
  registerTemplateCode: string
  loginTemplateCode: string
  resetPasswordTemplateCode: string
  enabled: boolean
}
```

Use the existing admin `httpClient` and `unwrapApiResponse`.

- [ ] **Step 4: Write failing navigation and page tests**

Assert a super administrator sees “短信配置”, a normal administrator does not, the route redirects non-super administrators, existing credentials render only configured state, and save omits blank AccessKey fields.

```tsx
expect(screen.getByText('AccessKey ID 已配置')).toBeInTheDocument()
expect(screen.queryByDisplayValue('stored-access-key')).not.toBeInTheDocument()
await user.click(screen.getByRole('button', { name: '保存短信配置' }))
expect(updateSmsConfig).toHaveBeenCalledWith(expect.not.objectContaining({ accessKeyId: '' }))
```

- [ ] **Step 5: Implement the page and protected route**

Use `PageContainer`, vertical `Form`, `Input.Password`, `Switch`, responsive two-column CSS and a `Button` with Lucide `Save`. Load only sign/template/enabled fields. Submit trimmed values, omit blank AccessKey fields, refetch after success, then clear both password inputs.

```tsx
<Button type="primary" icon={<Save size={16} />} htmlType="submit" loading={saving}>
  保存短信配置
</Button>
```

Place `/system-config/sms` inside `SuperAdminRoute`. Build the menu item from `admin?.isSuperAdmin === 1`, not CSS visibility.

- [ ] **Step 6: Run focused admin tests**

```powershell
pnpm test -- src/features/sms-config/smsConfigApi.test.ts src/pages/system/SmsConfigPage.test.tsx src/layouts/AdminLayout.test.tsx
pnpm typecheck
```

- [ ] **Step 7: Commit the admin page**

```powershell
git add -- kasi-admin-web/src/features/sms-config kasi-admin-web/src/pages/system/SmsConfigPage.tsx kasi-admin-web/src/pages/system/SmsConfigPage.test.tsx kasi-admin-web/src/pages/system/sms-config-page.css kasi-admin-web/src/router/AppRouter.tsx kasi-admin-web/src/layouts/AdminLayout.tsx kasi-admin-web/src/layouts/AdminLayout.test.tsx
git diff --cached --check
git commit -m "feat: add sms configuration page"
```

### Task 7: Add user authentication API clients

**Files:**
- Modify: `kasi-user-web/src/features/auth/types.ts`
- Modify: `kasi-user-web/src/features/auth/authApi.ts`
- Create: `kasi-user-web/src/features/auth/authApi.test.ts`

- [ ] **Step 1: Write failing endpoint contract tests**

Mock `httpClient.post` and assert every approved path and payload.

```ts
vi.mocked(httpClient.post)
  .mockResolvedValueOnce({ data: { code: 0, message: 'ok', data: null } })
  .mockResolvedValueOnce({ data: { code: 0, message: 'ok', data: loginResult } })

await sendLoginCode('13800138000')
await loginUserWithCode('13800138000', '123456')

expect(httpClient.post).toHaveBeenNthCalledWith(1, '/api/user/auth/login/code', {
  target: '13800138000',
})
expect(httpClient.post).toHaveBeenNthCalledWith(2, '/api/user/auth/login/code/verify', {
  target: '13800138000',
  code: '123456',
})
```

- [ ] **Step 2: Run the test and confirm missing exports**

```powershell
cd ..\kasi-user-web
pnpm test -- src/features/auth/authApi.test.ts
```

- [ ] **Step 3: Implement request/result types and all public operations**

```ts
export interface RegisterRequest {
  account: string
  verificationCode: string
  password: string
  confirmPassword: string
}

export interface ResetTokenResult {
  resetToken: string
  expiresIn: number
}
```

```ts
export const sendRegisterCode = (target: string) =>
  postVoid('/api/user/auth/register/code', { target })
export const registerUser = (request: RegisterRequest) =>
  postVoid('/api/user/auth/register', request)
export const sendLoginCode = (target: string) =>
  postVoid('/api/user/auth/login/code', { target })
export const loginUserWithCode = (target: string, code: string) =>
  postResult<LoginResult>('/api/user/auth/login/code/verify', { target, code })
export const sendForgotPasswordCode = (target: string) =>
  postVoid('/api/user/auth/password/forgot/code', { target })
export const verifyForgotPasswordCode = (target: string, code: string) =>
  postResult<ResetTokenResult>('/api/user/auth/password/forgot/verify', { target, code })
export const resetPassword = (resetToken: string, newPassword: string, confirmPassword: string) =>
  postVoid('/api/user/auth/password/reset', { resetToken, newPassword, confirmPassword })
```

`postVoid` accepts `data: null` when `code === 0`; `postResult` requires non-null data. Both throw the backend business message for non-zero codes.

- [ ] **Step 4: Run API tests and typecheck**

```powershell
pnpm test -- src/features/auth/authApi.test.ts
pnpm typecheck
```

- [ ] **Step 5: Commit the user API layer**

```powershell
git add -- kasi-user-web/src/features/auth/types.ts kasi-user-web/src/features/auth/authApi.ts kasi-user-web/src/features/auth/authApi.test.ts
git diff --cached --check
git commit -m "feat: add mobile verification auth api"
```

### Task 8: Connect registration, code login and password reset UI

**Files:**
- Create: `kasi-user-web/src/features/auth/components/PhoneAuthForms.tsx`
- Modify: `kasi-user-web/src/pages/LoginPage.tsx`
- Modify: `kasi-user-web/src/pages/login.css`
- Modify: `kasi-user-web/src/App.test.tsx`

- [ ] **Step 1: Extend mocks and write failing user-flow tests**

```tsx
vi.mock('./features/auth/authApi', () => ({
  getCurrentUser: vi.fn(),
  loginUser: vi.fn(),
  sendRegisterCode: vi.fn(),
  registerUser: vi.fn(),
  sendLoginCode: vi.fn(),
  loginUserWithCode: vi.fn(),
  sendForgotPasswordCode: vi.fn(),
  verifyForgotPasswordCode: vi.fn(),
  resetPassword: vi.fn(),
}))
```

Test that a failed send leaves the button enabled and named “发送验证码”; after a resolved send it becomes disabled and named “60秒后可重发”. Add independent success tests for registration returning to password login, code login storing the returned token and navigating to `/workspace`, and forgot-password verify/reset returning to password login.

- [ ] **Step 2: Run `App.test.tsx` and confirm failures**

```powershell
pnpm test -- src/App.test.tsx
```

- [ ] **Step 3: Implement reusable phone auth forms**

`PhoneAuthForms.tsx` exports `CodeLoginForm`, `RegisterForm` and `ForgotPasswordForm`. Each owns only submission/loading/error state. A shared internal verification field starts the countdown only after `onSend` resolves.

```tsx
const sendCode = async () => {
  const normalizedMobile = mobile.trim()
  if (!/^1[3-9]\d{9}$/.test(normalizedMobile)) {
    setError('请输入有效的手机号')
    return
  }
  setSending(true)
  setError(null)
  try {
    await onSend(normalizedMobile)
    setupCountdown()
  } catch (error) {
    if (!isHandledRequestError(error)) {
      setError(error instanceof Error ? error.message : '验证码发送失败')
    }
  } finally {
    setSending(false)
  }
}
```

Registration includes password and confirmation fields and calls `onRegistered` only after `registerUser` resolves. Forgot password stores only the returned reset token between its two stages and clears it after a successful reset.

- [ ] **Step 4: Compose the forms in the existing login page**

Keep password login and QR placeholder structure unchanged. Change registration contact copy to mobile-only. Wire “使用验证码登录”, “注册新账号” and “忘记密码？” to the new forms.

```tsx
const completeLogin = (result: LoginResult) => {
  setSession(result.accessToken)
  navigate('/workspace')
}

{mode === 'register' ? (
  <RegisterForm onRegistered={() => setMode('login')} />
) : mode === 'forgot' ? (
  <ForgotPasswordForm onReset={() => setMode('login')} />
) : (
  <LoginForm onCodeLogin={completeLogin} onForgot={() => setMode('forgot')} />
)}
```

Update CSS with stable form-row dimensions and mobile wrapping at 320px; do not change the page palette, hero composition or unrelated navigation.

- [ ] **Step 5: Run focused user frontend tests**

```powershell
pnpm test -- src/App.test.tsx src/shared/api/httpClient.test.ts
pnpm typecheck
```

- [ ] **Step 6: Commit the user flows**

```powershell
git add -- kasi-user-web/src/features/auth/components/PhoneAuthForms.tsx
git add -p -- kasi-user-web/src/App.test.tsx kasi-user-web/src/pages/LoginPage.tsx kasi-user-web/src/pages/login.css
git diff --cached --check
git commit -m "feat: connect mobile verification flows"
```

### Task 9: Synchronize documentation and run all gates

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/projects/kasi-backend.md`
- Modify: `docs/projects/kasi-admin-web.md`
- Modify: `docs/projects/kasi-user-web.md`
- Modify: `kasi-backend/README.md`
- Modify: `kasi-backend/AGENTS.md`
- Modify: `kasi-admin-web/README.md`
- Modify: `kasi-user-web/README.md`
- Modify: `kasi-user-web/AGENTS.md`

- [ ] **Step 1: Update current-state documentation after focused tests pass**

Document the two management endpoints, user send/verify routes, `system_sms_config`, AES-GCM non-disclosure, `local/test/production` sender split, super-admin page, mobile-only code flows and unchanged email password login. State explicitly that real Aliyun delivery remains pending until credentials, sign and templates are configured.

- [ ] **Step 2: Run backend focused tests with Java 25**

```powershell
cd kasi-backend
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd --% -Dtest=CredentialCipherTest,SmsConfigMigrationTest,SmsConfigServiceTest,SmsConfigAdminControllerTest,AliyunSmsVerificationCodeSenderTest,VerificationCodeServiceTest,UserAuthControllerTest test
```

Expected: zero failures and zero errors, with no external network request.

- [ ] **Step 3: Run the backend full Gate**

```powershell
.\mvnw.cmd verify
```

Expected: zero failures and zero errors. Record only explicitly environment-gated SKIP results.

- [ ] **Step 4: Run both frontend Gates**

```powershell
cd ..\kasi-admin-web
pnpm check

cd ..\kasi-user-web
pnpm check
```

Expected: lint, format, tests and builds pass. If a pre-existing unrelated dirty-file failure remains, record its exact path and prove all task-focused tests/builds separately.

- [ ] **Step 5: Start both frontends and inspect desktop/mobile rendering**

Run each Vite server on an available port. Use browser screenshots at desktop and 320px width for the SMS configuration page and all three phone auth modes. Confirm no overlap, clipped text, layout shift or blank panel. Stop only the servers started for verification after inspection.

- [ ] **Step 6: Run final scope checks**

```powershell
cd ..
git diff --check
git status --short --branch
git diff --stat d6d87b4..HEAD
git diff --stat
```

Confirm the task did not modify email delivery, QR login, provider runtime behavior, unrelated drama/order code or user-owned dirty files.

- [ ] **Step 7: Commit documentation only after behavior is verified**

```powershell
git add -p -- AGENTS.md docs/projects/kasi-backend.md docs/projects/kasi-admin-web.md docs/projects/kasi-user-web.md kasi-backend/README.md kasi-backend/AGENTS.md kasi-admin-web/README.md kasi-user-web/README.md kasi-user-web/AGENTS.md
git diff --cached --check
git commit -m "docs: document aliyun sms verification"
```

## Manual Acceptance After Configuration

These steps remain unchecked until the user has created an Aliyun AccessKey, approved sign and three approved templates.

- [ ] Configure credentials, sign and templates through `/system-config/sms` as the super administrator.
- [ ] Enable SMS and send one registration code to an authorized test mobile number.
- [ ] Complete registration with the received code.
- [ ] Send a login code and verify that it creates a normal user session.
- [ ] Send a forgot-password code, verify it, reset the password, and log in with the new password.
- [ ] Confirm production logs contain no code, AccessKey or complete mobile number.
