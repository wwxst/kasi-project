# User Profile SMS Password Change Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Require the current promotion user to verify a code sent to the account's bound mobile before the personal-center password form becomes available.

**Architecture:** Add authenticated send/verify endpoints that derive the mobile from the current user and use a distinct `CHANGE_PASSWORD` verification scene. Reuse the existing password reset token service as the short-lived, one-time proof, then require that token to belong to the authenticated user when `PUT /api/user/auth/password` updates the password. Keep the browser token only in `ProfilePage` component state.

**Tech Stack:** Spring Boot 4, Java 25, MyBatis, Redis/Lua, JUnit 5, MockMvc, React 19, TypeScript, TDesign React, TanStack Query, Zustand, Vitest, Testing Library.

**Authorization boundary:** This workspace has many existing uncommitted changes. Preserve them and edit only the named behavior. Do not commit or push without separate user authorization.

---

## File Map

Backend production files:

- Delete `kasi-backend/src/main/java/com/kasi/backend/auth/dto/ChangePasswordDTO.java`; it is currently user-only despite its stale shared-DTL comment.
- Create `kasi-backend/src/main/java/com/kasi/backend/user/dto/ChangeUserPasswordDTO.java` for the authenticated user's reset-token password request.
- Create `kasi-backend/src/main/java/com/kasi/backend/user/dto/VerifyChangePasswordCodeDTO.java` for the six-digit verification code.
- Modify `kasi-backend/src/main/java/com/kasi/backend/common/enums/VerificationScene.java` to add the isolated scene.
- Modify `kasi-backend/src/main/java/com/kasi/backend/common/exception/ErrorCode.java` to add the missing-mobile error.
- Modify `kasi-backend/src/main/java/com/kasi/backend/sms/service/impl/SmsConfigServiceImpl.java` to reuse the reset-password SMS template.
- Modify `kasi-backend/src/main/java/com/kasi/backend/user/controller/UserAuthController.java` for the two new endpoints and changed password DTO.
- Modify `kasi-backend/src/main/java/com/kasi/backend/user/service/UserAuthService.java` for the new service contract.
- Modify `kasi-backend/src/main/java/com/kasi/backend/user/service/impl/UserAuthServiceImpl.java` for current-user mobile lookup, code verification, token issuance, token ownership, password update, and session invalidation.

Backend tests:

- Modify `kasi-backend/src/test/java/com/kasi/backend/sms/service/SmsConfigServiceTest.java` to prove template selection.
- Modify `kasi-backend/src/test/java/com/kasi/backend/user/controller/UserAuthControllerTest.java` to prove the authenticated two-step flow and security boundaries.

Frontend production files:

- Modify `kasi-user-web/src/features/auth/types.ts` for the new request shape.
- Modify `kasi-user-web/src/features/auth/authApi.ts` for send, verify, and final change calls.
- Modify `kasi-user-web/src/pages/profile/ProfilePage.tsx` for local two-step state and the verification UI.
- Modify `kasi-user-web/src/pages/profile/ProfilePage.module.less` only for the new compact verification layout and narrow-screen behavior.

Frontend tests:

- Modify `kasi-user-web/src/features/auth/authApi.test.ts` for exact HTTP contracts.
- Modify `kasi-user-web/src/pages/profile/ProfilePage.test.tsx` for rendering, countdown, gating, token use, and logout behavior.

Current behavior documentation after tests pass:

- Modify `kasi-backend/AGENTS.md`.
- Modify `kasi-backend/README.md`.
- Modify `kasi-user-web/AGENTS.md`.
- Modify `kasi-user-web/README.md`.
- Modify `docs/projects/kasi-user-web.md`.

`kasi-backend/src/main/java/com/kasi/backend/security/config/SecurityConfig.java` requires no change: only `/password/forgot/**` and exact `/password/reset` are anonymous, while the new `/password/change/**` paths fall through to the existing `/api/user/**` USER rule.

---

### Task 1: Add the isolated verification scene and SMS template mapping

**Files:**

- Modify: `kasi-backend/src/test/java/com/kasi/backend/sms/service/SmsConfigServiceTest.java:76`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/common/enums/VerificationScene.java:6`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/sms/service/impl/SmsConfigServiceImpl.java:85`

- [ ] **Step 1: Write the failing template-selection test**

Extend `runtimeConfigSelectsSceneTemplate` without referring to a not-yet-defined enum constant at compile time:

```java
@Test
@DisplayName("四个业务场景选择对应模板且改密复用找回密码模板")
void runtimeConfigSelectsSceneTemplate() {
    smsConfigService.update(1L, request("ak-id", "ak-secret", true));

    SmsRuntimeConfig register = smsConfigService.requireRuntimeConfig(VerificationScene.REGISTER);
    SmsRuntimeConfig login = smsConfigService.requireRuntimeConfig(VerificationScene.LOGIN);
    SmsRuntimeConfig reset = smsConfigService.requireRuntimeConfig(VerificationScene.RESET_PASSWORD);
    SmsRuntimeConfig change = smsConfigService.requireRuntimeConfig(
            VerificationScene.valueOf("CHANGE_PASSWORD"));

    assertThat(register.accessKeyId()).isEqualTo("ak-id");
    assertThat(register.accessKeySecret()).isEqualTo("ak-secret");
    assertThat(register.templateCode()).isEqualTo("SMS_100");
    assertThat(login.templateCode()).isEqualTo("SMS_101");
    assertThat(reset.templateCode()).isEqualTo("SMS_102");
    assertThat(change.templateCode()).isEqualTo("SMS_102");
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
cd kasi-backend
.\mvnw.cmd -Dtest=SmsConfigServiceTest#runtimeConfigSelectsSceneTemplate test
```

Expected: FAIL because `VerificationScene.valueOf("CHANGE_PASSWORD")` throws `IllegalArgumentException`; the failure must not be caused by database, Redis, or compilation setup.

- [ ] **Step 3: Add the enum value and exhaustive switch branch**

Make the enum:

```java
public enum VerificationScene {
    REGISTER,
    LOGIN,
    RESET_PASSWORD,
    CHANGE_PASSWORD
}
```

Make the runtime template switch:

```java
String templateCode = switch (scene) {
    case REGISTER -> config.getRegisterTemplateCode();
    case LOGIN -> config.getLoginTemplateCode();
    case RESET_PASSWORD, CHANGE_PASSWORD -> config.getResetPasswordTemplateCode();
};
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the same Maven command. Expected: 1 test run, 0 failures, 0 errors.

---

### Task 2: Specify the authenticated backend password-change flow with failing HTTP tests

**Files:**

- Modify: `kasi-backend/src/test/java/com/kasi/backend/user/controller/UserAuthControllerTest.java:410`

- [ ] **Step 1: Replace the original-password tests with a failing happy-path test**

Add this helper near the end of `UserAuthControllerTest`:

```java
private String verifyChangePasswordCode(String token, String mobile) throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
                    .post("/api/user/auth/password/change/code")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

    var result = mockMvc.perform(MockMvcRequestBuilders
                    .post("/api/user/auth/password/change/verify")
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content("""
                            {"code":"%s"}
                            """.formatted(verificationCodeSender.latestCode(mobile))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.resetToken").isNotEmpty())
            .andExpect(jsonPath("$.data.expiresIn").value(600))
            .andReturn();

    return objectMapper.readTree(result.getResponse().getContentAsString())
            .get("data").get("resetToken").stringValue();
}
```

Replace `changePasswordSuccess` with:

```java
@Test
@DisplayName("手机验证码通过后修改密码成功")
void changePasswordAfterMobileVerification() throws Exception {
    String token = loginAsUser();
    String resetToken = verifyChangePasswordCode(token, "13800138000");

    mockMvc.perform(MockMvcRequestBuilders
                    .put("/api/user/auth/password")
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content("""
                            {"resetToken":"%s","newPassword":"newuserpass","confirmPassword":"newuserpass"}
                            """.formatted(resetToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

    mockMvc.perform(MockMvcRequestBuilders
                    .post("/api/user/auth/login")
                    .contentType("application/json")
                    .content("""
                            {"account":"13800138000","password":"newuserpass"}
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
}
```

Update `changePasswordInvalidatesAllExistingSessions` to obtain `resetToken` through the helper and submit it instead of `oldPassword`.

- [ ] **Step 2: Add failing boundary tests**

Add focused tests with these complete assertions:

```java
@Test
@DisplayName("改密验证码接口必须登录")
void changePasswordVerificationRequiresLogin() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.post("/api/user/auth/password/change/code"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(1002));
    mockMvc.perform(MockMvcRequestBuilders
                    .post("/api/user/auth/password/change/verify")
                    .contentType("application/json")
                    .content("{\"code\":\"123456\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(1002));
}

@Test
@DisplayName("未绑定手机号不能发送改密验证码")
void changePasswordCodeRequiresBoundMobile() throws Exception {
    String token = loginAsUser();
    jdbcTemplate.update("UPDATE promotion_user SET mobile = NULL WHERE user_no = ?", PRIMARY_USER_NO);

    mockMvc.perform(MockMvcRequestBuilders
                    .post("/api/user/auth/password/change/code")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(3017));
}

@Test
@DisplayName("原密码不能替代手机验证凭证")
void oldPasswordCannotReplaceVerificationToken() throws Exception {
    String token = loginAsUser();

    mockMvc.perform(MockMvcRequestBuilders
                    .put("/api/user/auth/password")
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content("""
                            {"oldPassword":"user123456","newPassword":"newuserpass","confirmPassword":"newuserpass"}
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(1006));
}

@Test
@DisplayName("改密凭证只能由所属用户使用且失败后恢复可用")
void changePasswordTokenCannotCrossUsers() throws Exception {
    String ownerToken = loginAsUser();
    String resetToken = verifyChangePasswordCode(ownerToken, "13800138000");
    String otherToken = loginAsUser("13900139000", USER_PASSWORD);

    String body = """
            {"resetToken":"%s","newPassword":"newuserpass","confirmPassword":"newuserpass"}
            """.formatted(resetToken);
    mockMvc.perform(MockMvcRequestBuilders
                    .put("/api/user/auth/password")
                    .header("Authorization", "Bearer " + otherToken)
                    .contentType("application/json")
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(5001));

    mockMvc.perform(MockMvcRequestBuilders
                    .put("/api/user/auth/password")
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType("application/json")
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
}

@Test
@DisplayName("验证码错误不能进入修改密码")
void invalidChangePasswordCodeDoesNotIssueToken() throws Exception {
    String token = loginAsUser();
    mockMvc.perform(MockMvcRequestBuilders
                    .post("/api/user/auth/password/change/code")
                    .header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.code").value(0));

    mockMvc.perform(MockMvcRequestBuilders
                    .post("/api/user/auth/password/change/verify")
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .content("{\"code\":\"000000\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(4001))
            .andExpect(jsonPath("$.data").doesNotExist());
}
```

- [ ] **Step 3: Run the controller test and verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest=UserAuthControllerTest test
```

Expected: existing unrelated cases compile, while the new flow fails because the endpoints and reset-token request contract do not exist. Record the exact failing assertions before production edits.

---

### Task 3: Implement the minimal backend contract

**Files:**

- Delete: `kasi-backend/src/main/java/com/kasi/backend/auth/dto/ChangePasswordDTO.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/user/dto/ChangeUserPasswordDTO.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/user/dto/VerifyChangePasswordCodeDTO.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/common/exception/ErrorCode.java:42`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/user/controller/UserAuthController.java:91`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/user/service/UserAuthService.java:32`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/user/service/impl/UserAuthServiceImpl.java:321`

- [ ] **Step 1: Replace the stale user password DTO**

Create `ChangeUserPasswordDTO.java`:

```java
package com.kasi.backend.user.dto;

import com.kasi.backend.common.validation.Utf8ByteLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangeUserPasswordDTO {

    @NotBlank(message = "重置凭证不能为空")
    private String resetToken;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, message = "新密码长度不能少于8位")
    @Utf8ByteLength
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    @Utf8ByteLength
    private String confirmPassword;
}
```

Create `VerifyChangePasswordCodeDTO.java`:

```java
package com.kasi.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyChangePasswordCodeDTO {

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "\\d{6}", message = "验证码必须为6位数字")
    private String code;
}
```

Delete the now-unused `auth/dto/ChangePasswordDTO.java` only after all imports have moved to `ChangeUserPasswordDTO`.

- [ ] **Step 2: Add the missing-mobile error**

Append after `USER_AVATAR_TOO_LARGE` without renumbering existing errors:

```java
USER_MOBILE_NOT_BOUND(3017, "当前账号未绑定手机号，请先绑定手机号"),
```

- [ ] **Step 3: Add the service interface methods**

Use these exact signatures:

```java
void sendChangePasswordCode(Long userId);

VerifyCodeVO verifyChangePasswordCode(Long userId, VerifyChangePasswordCodeDTO request);

void changePassword(Long userId, ChangeUserPasswordDTO request);
```

Remove the old `ChangePasswordDTO` import and add the two user DTO imports.

- [ ] **Step 4: Add the authenticated controller endpoints**

Place the new methods immediately before `changePassword`:

```java
@PostMapping("/password/change/code")
public ApiResponse<Void> sendChangePasswordCode() {
    userAuthService.sendChangePasswordCode(AuthContextHolder.getUserId());
    return ApiResponse.successMessage("验证码已发送");
}

@PostMapping("/password/change/verify")
public ApiResponse<VerifyCodeVO> verifyChangePasswordCode(
        @Valid @RequestBody VerifyChangePasswordCodeDTO request) {
    return ApiResponse.success("验证成功",
            userAuthService.verifyChangePasswordCode(AuthContextHolder.getUserId(), request));
}

@PutMapping("/password")
public ApiResponse<Void> changePassword(
        @Valid @RequestBody ChangeUserPasswordDTO request) {
    userAuthService.changePassword(AuthContextHolder.getUserId(), request);
    return ApiResponse.successMessage("密码修改成功");
}
```

- [ ] **Step 5: Implement current-user mobile lookup and token issuance**

Add a small focused helper and the two service methods:

```java
private PromotionUser requireUserWithMobile(Long userId) {
    PromotionUser user = promotionUserMapper.findById(userId);
    if (user == null) {
        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
    if (user.getMobile() == null || user.getMobile().isBlank()) {
        throw new BusinessException(ErrorCode.USER_MOBILE_NOT_BOUND);
    }
    return user;
}

@Override
public void sendChangePasswordCode(Long userId) {
    PromotionUser user = requireUserWithMobile(userId);
    verificationCodeService.sendVerificationCode(
            user.getMobile(), VerificationScene.CHANGE_PASSWORD);
}

@Override
public VerifyCodeVO verifyChangePasswordCode(
        Long userId, VerifyChangePasswordCodeDTO request) {
    PromotionUser user = requireUserWithMobile(userId);
    verificationCodeService.verifyCode(
            user.getMobile(), VerificationScene.CHANGE_PASSWORD, request.getCode());
    String resetToken = passwordResetTokenService.generateResetToken(userId, SubjectType.USER);
    return VerifyCodeVO.builder()
            .resetToken(resetToken)
            .expiresIn(resetTokenExpiration)
            .build();
}
```

- [ ] **Step 6: Replace original-password checking with token ownership checking**

Implement `changePassword` as:

```java
@Transactional
@Override
public void changePassword(Long userId, ChangeUserPasswordDTO request) {
    if (!request.getNewPassword().equals(request.getConfirmPassword())) {
        throw new BusinessException(ErrorCode.USER_PASSWORD_NOT_MATCH);
    }

    String encodedPassword = passwordEncoder.encode(request.getNewPassword());
    PasswordResetTokenReservation reservation =
            passwordResetTokenService.reserveToken(request.getResetToken());
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            passwordResetTokenService.completeToken(reservation);
        }

        @Override
        public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
                passwordResetTokenService.restoreReady(reservation);
            }
        }
    });

    if (reservation.subjectType() != SubjectType.USER
            || !reservation.userId().equals(userId)) {
        throw new BusinessException(ErrorCode.RESET_TOKEN_INVALID);
    }

    PromotionUser user = promotionUserMapper.findByIdForUpdate(userId);
    if (user == null) {
        throw new BusinessException(ErrorCode.RESET_TOKEN_INVALID);
    }
    if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
        throw new BusinessException(ErrorCode.USER_NEW_PASSWORD_SAME);
    }

    SessionMutation mutation = sessionService.beginMutation(SubjectType.USER, userId);
    sessionService.registerMutationCompletion(mutation);
    int updated = promotionUserMapper.updatePassword(userId, encodedPassword);
    if (updated != 1) {
        throw new IllegalStateException("用户密码更新未生效");
    }

    log.info("用户 [ID={}] 通过手机验证码修改密码成功", userId);
}
```

Do not change the public `resetPassword` implementation or the admin password flow.

- [ ] **Step 7: Run the backend focused tests and verify GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=SmsConfigServiceTest,UserAuthControllerTest test
```

Expected: both classes pass with 0 failures and 0 errors. Confirm the test report count from Maven output rather than estimating it.

---

### Task 4: Specify and implement the frontend API contract with TDD

**Files:**

- Modify: `kasi-user-web/src/features/auth/authApi.test.ts:21`
- Modify: `kasi-user-web/src/features/auth/types.ts:49`
- Modify: `kasi-user-web/src/features/auth/authApi.ts:49`

- [ ] **Step 1: Write failing API tests**

Import `sendChangePasswordCode` and `verifyChangePasswordCode`, then replace the current `changePassword` describe block with:

```typescript
describe('changePassword', () => {
  it('sends and verifies the current users bound-mobile code', async () => {
    const post = vi
      .spyOn(httpClient, 'post')
      .mockResolvedValueOnce({
        data: { code: 0, message: '验证码已发送', data: null },
      })
      .mockResolvedValueOnce({
        data: {
          code: 0,
          message: '验证成功',
          data: { resetToken: 'change-token', expiresIn: 600 },
        },
      })

    await sendChangePasswordCode()
    await expect(verifyChangePasswordCode('123456')).resolves.toEqual({
      resetToken: 'change-token',
      expiresIn: 600,
    })

    expect(post).toHaveBeenNthCalledWith(
      1,
      '/api/user/auth/password/change/code',
      {},
    )
    expect(post).toHaveBeenNthCalledWith(
      2,
      '/api/user/auth/password/change/verify',
      { code: '123456' },
    )
  })

  it('changes the password with the verified in-memory token', async () => {
    const put = vi.spyOn(httpClient, 'put').mockResolvedValue({
      data: { code: 0, message: '密码修改成功', data: null },
    })

    await changePassword({
      resetToken: 'change-token',
      newPassword: 'new-password',
      confirmPassword: 'new-password',
    })

    expect(put).toHaveBeenCalledWith('/api/user/auth/password', {
      resetToken: 'change-token',
      newPassword: 'new-password',
      confirmPassword: 'new-password',
    })
  })
})
```

- [ ] **Step 2: Run the API test and verify RED**

Run:

```powershell
cd ..\kasi-user-web
pnpm test src/features/auth/authApi.test.ts
```

Expected: FAIL because the new API functions are missing and `ChangePasswordRequest` still requires `oldPassword`.

- [ ] **Step 3: Implement the request types and API functions**

Replace the request type with:

```typescript
export interface ChangePasswordRequest {
  resetToken: string
  newPassword: string
  confirmPassword: string
}
```

Add:

```typescript
export const sendChangePasswordCode = () =>
  postVoid('/api/user/auth/password/change/code', {})

export const verifyChangePasswordCode = async (code: string) => {
  const response = await httpClient.post<ApiResponse<ResetTokenResult>>(
    '/api/user/auth/password/change/verify',
    { code },
  )
  return unwrap(response)
}
```

Keep `changePassword`'s URL and response handling unchanged; its parameter type now enforces the new payload.

- [ ] **Step 4: Run the API test and verify GREEN**

Run the same Vitest command. Expected: all tests in `authApi.test.ts` pass.

---

### Task 5: Build the two-step personal-center interaction with TDD

**Files:**

- Modify: `kasi-user-web/src/pages/profile/ProfilePage.test.tsx:6`
- Modify: `kasi-user-web/src/pages/profile/ProfilePage.tsx:1`
- Modify: `kasi-user-web/src/pages/profile/ProfilePage.module.less:170`

- [ ] **Step 1: Update mocks and write the initial-gate test**

Import and mock `sendChangePasswordCode` and `verifyChangePasswordCode`. Add:

```typescript
it('requires bound-mobile verification before showing password fields', async () => {
  const user = userEvent.setup()
  renderPage()

  await user.click(await screen.findByRole('button', { name: '安全设置' }))

  expect(screen.getByText('136****6000')).toBeTruthy()
  expect(screen.getByLabelText('验证码')).toBeTruthy()
  expect(screen.queryByLabelText('新密码')).toBeNull()
  expect(screen.queryByLabelText('确认新密码')).toBeNull()
})
```

- [ ] **Step 2: Write the failing successful two-step test**

Replace the old original-password page test with:

```typescript
it('verifies the mobile before changing the password and logging out', async () => {
  const user = userEvent.setup()
  vi.mocked(sendChangePasswordCode).mockResolvedValue(undefined)
  vi.mocked(verifyChangePasswordCode).mockResolvedValue({
    resetToken: 'change-token',
    expiresIn: 600,
  })
  vi.mocked(changePassword).mockResolvedValue(undefined)
  renderPage()

  await user.click(await screen.findByRole('button', { name: '安全设置' }))
  await user.click(screen.getByRole('button', { name: '发送验证码' }))
  await waitFor(() => expect(sendChangePasswordCode).toHaveBeenCalledOnce())
  expect(screen.getByRole('button', { name: '60秒后可重发' })).toBeTruthy()

  await user.type(screen.getByLabelText('验证码'), '123456')
  await user.click(screen.getByRole('button', { name: '验证并继续' }))
  await waitFor(() =>
    expect(verifyChangePasswordCode).toHaveBeenCalledWith('123456'),
  )

  await user.type(screen.getByLabelText('新密码'), 'new-password')
  await user.type(screen.getByLabelText('确认新密码'), 'new-password')
  await user.click(screen.getByRole('button', { name: '修改密码' }))

  await waitFor(() =>
    expect(changePassword).toHaveBeenCalledWith({
      resetToken: 'change-token',
      newPassword: 'new-password',
      confirmPassword: 'new-password',
    }),
  )
  expect(await screen.findByText('登录页')).toBeTruthy()
  expect(useAuthStore.getState().accessToken).toBeNull()
})
```

- [ ] **Step 3: Write failing error and no-mobile tests**

Add:

```typescript
it('starts the resend countdown only after a successful send', async () => {
  const user = userEvent.setup()
  vi.mocked(sendChangePasswordCode).mockRejectedValue(new Error('发送失败'))
  renderPage()

  await user.click(await screen.findByRole('button', { name: '安全设置' }))
  await user.click(screen.getByRole('button', { name: '发送验证码' }))

  await waitFor(() => expect(sendChangePasswordCode).toHaveBeenCalledOnce())
  expect(screen.getByRole('button', { name: '发送验证码' })).toBeTruthy()
  expect(screen.queryByRole('button', { name: /秒后可重发/ })).toBeNull()
})

it('does not expose password verification without a bound mobile', async () => {
  const user = userEvent.setup()
  vi.mocked(getCurrentUser).mockResolvedValue({ ...currentUser, mobile: null })
  renderPage()

  await user.click(await screen.findByRole('button', { name: '安全设置' }))

  expect(screen.getByText('请先在基本信息绑定手机号')).toBeTruthy()
  expect(screen.queryByRole('button', { name: '发送验证码' })).toBeNull()
  expect(screen.queryByRole('button', { name: '验证并继续' })).toBeNull()
})
```

- [ ] **Step 4: Run the page test and verify RED**

Run:

```powershell
pnpm test src/pages/profile/ProfilePage.test.tsx
```

Expected: the new tests fail because the current page renders `oldPassword` and has no verification step.

- [ ] **Step 5: Add local state, masking, and countdown behavior**

Add these imports and helpers without moving logic into global state:

```typescript
import {
  changePassword,
  getCurrentUser,
  sendChangePasswordCode,
  updateUserProfile,
  uploadUserAvatar,
  verifyChangePasswordCode,
} from '../../features/auth/authApi'

function maskMobile(value: string) {
  return `${value.slice(0, 3)}****${value.slice(-4)}`
}
```

Inside `ProfilePage`, add:

```typescript
const [resetToken, setResetToken] = useState<string | null>(null)
const [sendingCode, setSendingCode] = useState(false)
const [countdown, setCountdown] = useState(0)

useEffect(() => {
  if (countdown <= 0) return undefined
  const timer = window.setInterval(() => {
    setCountdown((current) => Math.max(0, current - 1))
  }, 1000)
  return () => window.clearInterval(timer)
}, [countdown])
```

Add handlers:

```typescript
const handleSendChangePasswordCode = async () => {
  setSendingCode(true)
  try {
    await sendChangePasswordCode()
    setCountdown(60)
    void MessagePlugin.success('验证码已发送')
  } catch (error) {
    if (!isHandledRequestError(error)) {
      void MessagePlugin.error(
        error instanceof Error ? error.message : '验证码发送失败，请稍后重试',
      )
    }
  } finally {
    setSendingCode(false)
  }
}

const handleVerifyChangePasswordCode = async ({
  fields,
  validateResult,
}: SubmitContext) => {
  if (validateResult !== true) return
  setSubmitting(true)
  try {
    const result = await verifyChangePasswordCode(
      String(fields?.verificationCode ?? '').trim(),
    )
    setResetToken(result.resetToken)
    formRef.current?.reset?.()
  } catch (error) {
    if (!isHandledRequestError(error)) {
      void MessagePlugin.error(
        error instanceof Error ? error.message : '验证码校验失败，请稍后重试',
      )
    }
  } finally {
    setSubmitting(false)
  }
}
```

Change the password request in `handlePasswordSubmit` to:

```typescript
if (!resetToken) return
const request = {
  resetToken,
  newPassword: String(fields?.newPassword ?? ''),
  confirmPassword: String(fields?.confirmPassword ?? ''),
}
```

- [ ] **Step 6: Replace the security panel form with two explicit states**

Keep the existing unframed `securitySection`. Its content should be structurally equivalent to:

```tsx
<h2>修改密码</h2>
{!user.mobile ? (
  <p className={Style.mobileRequired}>请先在基本信息绑定手机号</p>
) : !resetToken ? (
  <Form
    ref={formRef}
    className={Style.passwordForm}
    labelAlign="top"
    onSubmit={handleVerifyChangePasswordCode}
  >
    <p className={Style.mobileHint}>
      验证码将发送至 <strong>{maskMobile(user.mobile)}</strong>
    </p>
    <Form.FormItem
      label="验证码"
      name="verificationCode"
      rules={[
        { required: true, message: '请输入验证码' },
        {
          validator: (value) => /^\d{6}$/.test(String(value ?? '')),
          message: '请输入6位验证码',
        },
      ]}
    >
      <div className={Style.verificationRow}>
        <Input maxlength={6} placeholder="请输入验证码" />
        <Button
          type="button"
          variant="outline"
          loading={sendingCode}
          disabled={countdown > 0}
          onClick={() => void handleSendChangePasswordCode()}
        >
          {countdown === 0 ? '发送验证码' : `${countdown}秒后可重发`}
        </Button>
      </div>
    </Form.FormItem>
    <Button
      className={Style.passwordSubmit}
      theme="primary"
      type="submit"
      loading={submitting}
    >
      验证并继续
    </Button>
  </Form>
) : (
  <Form
    ref={formRef}
    className={Style.passwordForm}
    labelAlign="top"
    onSubmit={handlePasswordSubmit}
  >
    <Form.FormItem
      label="新密码"
      name="newPassword"
      rules={[
        { required: true, message: '请输入新密码' },
        { min: 8, message: '新密码长度不能少于8位' },
      ]}
    >
      <Input type="password" autocomplete="new-password" placeholder="请输入新密码" />
    </Form.FormItem>
    <Form.FormItem
      label="确认新密码"
      name="confirmPassword"
      rules={[
        { required: true, message: '请再次输入新密码' },
        {
          validator: (value) =>
            value === formRef.current?.getFieldValue('newPassword'),
          message: '两次输入的新密码不一致',
        },
      ]}
    >
      <Input
        type="password"
        autocomplete="new-password"
        placeholder="请再次输入新密码"
      />
    </Form.FormItem>
    <Button
      className={Style.passwordSubmit}
      theme="primary"
      type="submit"
      loading={submitting}
    >
      修改密码
    </Button>
  </Form>
)}
```

- [ ] **Step 7: Add only the required responsive styles**

Add:

```less
.mobileHint,
.mobileRequired {
  margin: 0 0 18px;
  color: var(--td-text-color-secondary);
  line-height: 22px;
}

.mobileHint strong {
  color: var(--td-text-color-primary);
  font-weight: 500;
}

.verificationRow {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
}

@media (max-width: 420px) {
  .verificationRow {
    grid-template-columns: minmax(0, 1fr);
  }

  .verificationRow :global(.t-button) {
    width: 100%;
  }
}
```

Do not alter the existing profile card, tabs, profile editor, or unrelated responsive rules.

- [ ] **Step 8: Run the frontend focused tests and verify GREEN**

Run:

```powershell
pnpm test src/features/auth/authApi.test.ts src/pages/profile/ProfilePage.test.tsx
```

Expected: both files pass. Record the exact test count from Vitest output.

---

### Task 6: Synchronize current documentation only after behavior passes

**Files:**

- Modify: `kasi-backend/AGENTS.md:57`
- Modify: `kasi-backend/README.md:334`
- Modify: `kasi-backend/README.md:452`
- Modify: `kasi-user-web/AGENTS.md:15`
- Modify: `kasi-user-web/README.md:39`
- Modify: `docs/projects/kasi-user-web.md:7`

- [ ] **Step 1: Update the backend current contract**

Replace only the promotion-user sentence and endpoint row with wording equivalent to:

```markdown
推广用户本人改密必须先通过当前账号绑定手机号的 `CHANGE_PASSWORD` 验证码校验，再使用一次性改密凭证提交新密码；成功后该账号全部旧 Token 失效。管理员本人改密流程不变。
```

Document these authenticated endpoints in the API table:

```markdown
| POST | `/api/user/auth/password/change/code` | 向当前账号绑定手机号发送改密验证码 | USER |
| POST | `/api/user/auth/password/change/verify` | 校验改密验证码并返回一次性改密凭证 | USER |
| PUT | `/api/user/auth/password` | 使用改密凭证修改本人密码；成功后旧 Token 失效 | USER |
```

Update the verification-code description to include the isolated `CHANGE_PASSWORD` scene and note that it reuses the reset-password SMS template. Do not claim real SMS delivery was tested.

- [ ] **Step 2: Update the user-web current contract**

In each user-web document, replace only the password behavior with:

```markdown
个人中心修改密码先向当前账号绑定手机号发送验证码，校验成功后才显示新密码表单；一次性改密凭证只保存在组件内存中。修改成功后清除本地会话并返回登录页。
```

Preserve all unrelated existing and uncommitted documentation edits.

- [ ] **Step 3: Check documentation scope**

Run:

```powershell
cd ..
git diff -- kasi-backend/AGENTS.md kasi-backend/README.md kasi-user-web/AGENTS.md kasi-user-web/README.md docs/projects/kasi-user-web.md
```

Expected: only the approved password-flow statements are newly changed by this task; existing unrelated diff remains intact.

---

### Task 7: Run canonical gates and visual verification

**Files:**

- Verify only; no new production files expected.

- [ ] **Step 1: Run the backend canonical gate**

```powershell
cd kasi-backend
.\mvnw.cmd verify
```

Expected: exit code 0, 0 failures, 0 errors. Record total tests from Surefire/Failsafe output. If the machine Java differs from `pom.xml`, set command-local Java 25 without changing repository configuration.

- [ ] **Step 2: Run the user-web canonical gate**

```powershell
cd ..\kasi-user-web
pnpm install --frozen-lockfile
pnpm check
```

Expected: both commands exit 0; lint, Prettier check, Vitest, TypeScript build, and Vite build all complete successfully. Record exact test counts.

- [ ] **Step 3: Start the user-web development server**

```powershell
pnpm dev --host 127.0.0.1
```

Use the reported free port. Do not kill an unrelated process if the default port is occupied.

- [ ] **Step 4: Verify the live two-step UI**

In the in-app browser, inspect `/workspace/profile` at a desktop viewport and at 320px width. Because the route is authenticated, use an existing local test session or the supported local login path; do not bypass authentication in production code.

Verify:

- the first state shows a masked mobile and verification controls but no password inputs;
- the send button does not resize the layout when its label becomes `60秒后可重发`;
- the second state shows only new-password inputs;
- the no-mobile message, longest validation text, buttons, and form fields do not overlap or clip at 320px;
- existing basic information and avatar behavior remain visually intact.

If no runnable backend/session or SMS configuration is available, record the unavailable portions as `SKIP`; do not claim a visual or real-SMS `PASS` from unit tests alone.

- [ ] **Step 5: Run whitespace and scope checks**

```powershell
cd ..
git diff --check
git status --short --branch
git diff --name-only
```

Expected: `git diff --check` exits 0. Review the final diff only for this task's files and their direct call chain; do not clean unrelated workspace changes.

- [ ] **Step 6: Report without committing**

Report in Chinese first:

- behavior implemented;
- backend and frontend gate exit codes and exact test counts;
- desktop/mobile visual result or precise `SKIP` reason;
- real Aliyun SMS result as `SKIP` unless it was actually delivered in a configured environment;
- all files changed for this task and any remaining risks.

Do not commit or push unless the user separately authorizes it.
