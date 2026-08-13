# Administrator Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add super-admin-only CRUD for ordinary administrator accounts, plus self-service administrator profile editing, while preserving the existing JWT + Redis fail-closed session model.

**Architecture:** Keep authentication in `AdminAuthController` / `AdminAuthService` and add `AdminManagementController` / `AdminManagementService` for managing ordinary administrators. Reuse `SysAdminUserMapper`, derive `ROLE_SUPER_ADMIN` from the current database row, and wrap login-identifier, status, password, and delete mutations with the existing Redis `MUTATING -> ACTIVE` workflow.

**Tech Stack:** Java 25, Spring Boot 4.0.7, Spring Security, Jakarta Validation, MyBatis, MySQL/H2, Redis Lua session state, JUnit 5, MockMvc, Mockito, Maven Wrapper.

**Approved spec:** `docs/superpowers/specs/2026-08-13-admin-management-design.md`

---

## File Map

### Create

- `src/main/java/com/kasi/backend/admin/controller/AdminManagementController.java` - super-admin management HTTP endpoints.
- `src/main/java/com/kasi/backend/admin/service/AdminManagementService.java` - administrator management contract.
- `src/main/java/com/kasi/backend/admin/service/impl/AdminManagementServiceImpl.java` - business rules, transactions, uniqueness, and Redis mutation orchestration.
- `src/main/java/com/kasi/backend/admin/dto/AdminPageQueryDTO.java` - page and keyword query.
- `src/main/java/com/kasi/backend/admin/dto/CreateAdminDTO.java` - ordinary administrator creation request.
- `src/main/java/com/kasi/backend/admin/dto/UpdateAdminDTO.java` - ordinary administrator profile request.
- `src/main/java/com/kasi/backend/admin/dto/UpdateAdminProfileDTO.java` - current administrator profile request.
- `src/main/java/com/kasi/backend/admin/dto/UpdateAdminStatusDTO.java` - ordinary administrator status request.
- `src/main/java/com/kasi/backend/admin/dto/ResetAdminPasswordDTO.java` - super-admin password reset request.
- `src/main/java/com/kasi/backend/admin/dto/AdminChangePasswordDTO.java` - current administrator password change request; separate from the user-facing shared DTO so the administrator ASCII password rule does not change user behavior.
- `src/main/java/com/kasi/backend/admin/vo/AdminListItemVO.java` - management list item.
- `src/main/java/com/kasi/backend/admin/vo/AdminDetailVO.java` - management detail response.
- `src/main/java/com/kasi/backend/admin/vo/AdminPageVO.java` - page response.
- `src/main/java/com/kasi/backend/common/validation/OptionalMobile.java` - optional mobile constraint that validates the trimmed value.
- `src/main/java/com/kasi/backend/common/validation/OptionalMobileValidator.java` - optional mobile implementation.
- `src/main/java/com/kasi/backend/common/validation/OptionalEmail.java` - optional email constraint that validates the trimmed value.
- `src/main/java/com/kasi/backend/common/validation/OptionalEmailValidator.java` - optional email implementation.
- `src/test/java/com/kasi/backend/admin/controller/AdminManagementPermissionTest.java` - 401/403/super-admin authority coverage.
- `src/test/java/com/kasi/backend/admin/controller/AdminManagementQueryTest.java` - pagination, search, order, and detail coverage.
- `src/test/java/com/kasi/backend/admin/controller/AdminManagementMutationTest.java` - create, update, status, reset, and delete coverage.
- `src/test/java/com/kasi/backend/admin/dto/AdminDtoValidationTest.java` - isolated Jakarta Validation coverage for administrator DTOs.
- `src/test/java/com/kasi/backend/admin/service/AdminManagementServiceTest.java` - Redis-first ordering and failure behavior.

### Modify

- `src/main/resources/db/migration/V1__kasi_promotion.sql` - remove administrator `nickname`, require `real_name`.
- `src/test/resources/test-schema.sql` - mirror the administrator schema.
- `src/main/java/com/kasi/backend/admin/entity/SysAdminUser.java` - remove `nickname` only; retain `deletedAt` mapping.
- `src/main/java/com/kasi/backend/admin/mapper/SysAdminUserMapper.java` - page, update, delete operations.
- `src/main/resources/mapper/SysAdminUserMapper.xml` - SQL for the new mapper operations.
- `src/main/java/com/kasi/backend/admin/controller/AdminAuthController.java` - add profile endpoint and use administrator-specific password DTO.
- `src/main/java/com/kasi/backend/admin/service/AdminAuthService.java` - add current-profile update contract and administrator-specific password DTO.
- `src/main/java/com/kasi/backend/admin/service/impl/AdminAuthServiceImpl.java` - remove nickname mapping, add current-profile update, retain password behavior.
- `src/main/java/com/kasi/backend/admin/vo/AdminLoginVO.java` - replace nickname with real name.
- `src/main/java/com/kasi/backend/admin/vo/CurrentAdminVO.java` - remove nickname.
- `src/main/java/com/kasi/backend/security/filter/JwtAuthenticationFilter.java` - derive `ROLE_SUPER_ADMIN` from the database row.
- `src/main/java/com/kasi/backend/security/config/SecurityConfig.java` - protect management routes before the general admin matcher.
- `src/main/java/com/kasi/backend/common/exception/ErrorCode.java` - add management errors `2006..2011`.
- `src/main/java/com/kasi/backend/admin/dto/AdminLoginDTO.java` - restrict administrator login passwords to visible ASCII and 72 characters.
- `src/test/java/com/kasi/backend/BaseAuthTest.java` - seed `kasiadmin / kasi123456`, real names, and an ordinary administrator helper.
- `src/test/java/com/kasi/backend/admin/controller/AdminAuthControllerTest.java` - real-name response, profile, and administrator password character-range tests.
- `src/test/java/com/kasi/backend/security/SessionAuthenticationTest.java` - retain physical-delete account rejection coverage.
- `src/test/java/com/kasi/backend/ServiceImplementationStructureTest.java` - verify interface/implementation separation.
- `README.md` - current API, schema, permissions, and test count.
- `AGENTS.md` - current implemented administrator-management scope only.

---

### Task 1: Align Administrator Schema and Authentication Models

**Files:**
- Modify: `src/main/resources/db/migration/V1__kasi_promotion.sql`
- Modify: `src/test/resources/test-schema.sql`
- Modify: `src/main/java/com/kasi/backend/admin/entity/SysAdminUser.java`
- Modify: `src/main/java/com/kasi/backend/admin/vo/AdminLoginVO.java`
- Modify: `src/main/java/com/kasi/backend/admin/vo/CurrentAdminVO.java`
- Modify: `src/main/java/com/kasi/backend/admin/service/impl/AdminAuthServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/admin/mapper/SysAdminUserMapper.java`
- Modify: `src/main/resources/mapper/SysAdminUserMapper.xml`
- Modify: `src/test/java/com/kasi/backend/BaseAuthTest.java`
- Modify: `src/test/java/com/kasi/backend/admin/controller/AdminAuthControllerTest.java`

- [ ] **Step 1: Write failing structure and response assertions**

Change the unique super-administrator seed to `kasiadmin / kasi123456`, use `real_name`, rename the invalid test username `disabled_admin` to `disabledadmin`, assert `$.data.realName`, and add source/schema assertions that administrator `nickname` is absent while `deleted_at` remains present.

Update the shared test constants and helper exactly as follows:

```java
protected static final String ADMIN_USERNAME = "kasiadmin";
protected static final String ADMIN_PASSWORD = "kasi123456";

protected String loginAsAdmin() throws Exception {
    return loginAsAdmin(ADMIN_USERNAME, ADMIN_PASSWORD);
}
```

Replace every hard-coded successful administrator credential in `AdminAuthControllerTest` and security tests with these constants or `loginAsAdmin()`. Keep deliberately wrong-password and nonexistent-account inputs unchanged. These credentials are test-only and must not be inserted by `V1__kasi_promotion.sql`.

```java
@Test
@DisplayName("当前管理员只返回真实姓名")
void getCurrentAdminReturnsRealNameWithoutNickname() throws Exception {
    String token = loginAsAdmin();
    mockMvc.perform(get("/api/admin/auth/me")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("kasiadmin"))
            .andExpect(jsonPath("$.data.realName").value("系统管理员"))
            .andExpect(jsonPath("$.data.nickname").doesNotExist());
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest=AdminAuthControllerTest#getCurrentAdminReturnsRealNameWithoutNickname test
```

Expected: FAIL because the current seed/VO still uses `nickname`.

- [ ] **Step 3: Apply the minimal schema/model change**

Use this administrator schema shape in both MySQL and H2:

```sql
username VARCHAR(64) NOT NULL,
password VARCHAR(255) NOT NULL,
real_name VARCHAR(64) NOT NULL,
mobile VARCHAR(32) DEFAULT NULL,
email VARCHAR(128) DEFAULT NULL,
deleted_at TIMESTAMP DEFAULT NULL
```

Delete only the administrator `nickname` property/result/insert column. Keep `deletedAt` and existing `deleted_at IS NULL` reads. Build authentication VOs with `realName`:

```java
.realName(admin.getRealName())
```

- [ ] **Step 4: Run schema/auth regression tests and verify GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=AdminAuthControllerTest,SessionAuthenticationTest test
```

Expected: PASS with zero failures and zero errors.

- [ ] **Step 5: Commit the schema/model slice**

```powershell
git add src/main/resources/db/migration/V1__kasi_promotion.sql src/test/resources/test-schema.sql src/main/java/com/kasi/backend/admin src/main/resources/mapper/SysAdminUserMapper.xml src/test/java/com/kasi/backend/BaseAuthTest.java src/test/java/com/kasi/backend/admin/controller/AdminAuthControllerTest.java src/test/java/com/kasi/backend/security/SessionAuthenticationTest.java
git commit -m "refactor: use real names for administrators"
```

---

### Task 2: Add Administrator DTO Validation and Management Error Codes

**Files:**
- Create: `src/main/java/com/kasi/backend/admin/dto/AdminPageQueryDTO.java`
- Create: `src/main/java/com/kasi/backend/admin/dto/CreateAdminDTO.java`
- Create: `src/main/java/com/kasi/backend/admin/dto/UpdateAdminDTO.java`
- Create: `src/main/java/com/kasi/backend/admin/dto/UpdateAdminProfileDTO.java`
- Create: `src/main/java/com/kasi/backend/admin/dto/UpdateAdminStatusDTO.java`
- Create: `src/main/java/com/kasi/backend/admin/dto/ResetAdminPasswordDTO.java`
- Create: `src/main/java/com/kasi/backend/admin/dto/AdminChangePasswordDTO.java`
- Create: `src/main/java/com/kasi/backend/common/validation/OptionalMobile.java`
- Create: `src/main/java/com/kasi/backend/common/validation/OptionalMobileValidator.java`
- Create: `src/main/java/com/kasi/backend/common/validation/OptionalEmail.java`
- Create: `src/main/java/com/kasi/backend/common/validation/OptionalEmailValidator.java`
- Modify: `src/main/java/com/kasi/backend/common/exception/ErrorCode.java`
- Modify: `src/main/java/com/kasi/backend/admin/dto/AdminLoginDTO.java`
- Create: `src/test/java/com/kasi/backend/admin/dto/AdminDtoValidationTest.java`

- [ ] **Step 1: Write failing validation tests**

Use the Jakarta `Validator` directly so this task stays green before the management Controller exists. Cover username ASCII alphanumeric rules, real-name whitespace, password visible-ASCII and length rules, invalid status, page bounds, invalid email/mobile, and valid DTOs. Representative assertion:

```java
@Test
@DisplayName("管理员账号和密码拒绝约定范围外字符")
void administratorIdentityFieldsRejectUnsupportedCharacters() {
    CreateAdminDTO request = new CreateAdminDTO();
    request.setUsername("new_admin");
    request.setPassword("密码Admin1");
    request.setConfirmPassword("密码Admin1");
    request.setRealName("张 三");

    Set<String> fields = validator.validate(request).stream()
            .map(violation -> violation.getPropertyPath().toString())
            .collect(Collectors.toSet());
    assertThat(fields).contains("username", "password", "realName");
}
```

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
.\mvnw.cmd -Dtest=AdminDtoValidationTest test
```

Expected: test compilation FAIL because the DTO classes do not exist.

- [ ] **Step 3: Create DTOs with explicit Jakarta Validation**

Use these exact validation patterns:

```java
@NotBlank(message = "登录账号不能为空")
@Size(max = 64, message = "登录账号不能超过64位")
@Pattern(regexp = "^[A-Za-z0-9]+$", message = "登录账号只能包含英文字母和数字")
private String username;

@NotBlank(message = "真实姓名不能为空")
@Size(max = 64, message = "真实姓名不能超过64位")
@Pattern(regexp = "^\\S+$", message = "真实姓名不能包含空白字符")
private String realName;

@NotBlank(message = "密码不能为空")
@Size(min = 8, max = 72, message = "密码长度必须为8到72位")
@Pattern(regexp = "^[!-~]+$", message = "密码只能包含ASCII字母、数字和特殊符号")
@Utf8ByteLength
private String password;
```

Create `@OptionalMobile` and `@OptionalEmail` in the existing `common.validation` package. Their validators return true for null/blank, otherwise trim first, enforce normalized maximum length (`32` and `128`), and match the same mobile/email regexes already used by `PhoneOrEmailValidator`. This preserves the approved rule: surrounding spaces are accepted and normalized later, internal spaces fail validation. Use those annotations on all administrator mobile/email fields; do not use raw `@Email` or a regex that rejects surrounding spaces before normalization.

Apply the same `^[A-Za-z0-9]+$` username constraint to create, management edit, and self-profile DTOs. Apply `^[!-~]+$` to every administrator password field. Creation/new/confirmation password fields use `@Size(min=8,max=72)`; login and old-password fields use `@Size(max=72)` without introducing a new minimum for credential verification. This allows `admin123`, `Admin@123`, and `12345678`, while rejecting spaces, Chinese, Chinese punctuation, and other non-ASCII characters.

Use `@Min(1)`, `@Max(100)`, and `@NotNull @Min(0) @Max(1)` for status. Add the visible-ASCII pattern and 72-character maximum to `AdminLoginDTO.password`, so every administrator password entry point rejects unsupported characters with `1006`. Add `ErrorCode` values `2006..2011` exactly as approved.

`AdminChangePasswordDTO` duplicates the three administrator password fields intentionally; do not add the administrator ASCII character rule to shared `auth.dto.ChangePasswordDTO`.

- [ ] **Step 4: Run DTO compilation and focused validation tests**

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd -Dtest=AdminDtoValidationTest test
```

Expected: compile succeeds and all DTO validation tests PASS.

- [ ] **Step 5: Commit validation contracts**

```powershell
git add src/main/java/com/kasi/backend/admin/dto src/main/java/com/kasi/backend/common/validation src/main/java/com/kasi/backend/common/exception/ErrorCode.java src/test/java/com/kasi/backend/admin/dto/AdminDtoValidationTest.java
git commit -m "feat: define administrator management contracts"
```

---

### Task 3: Enforce Database-Derived Super-Administrator Authority

**Files:**
- Modify: `src/main/java/com/kasi/backend/security/filter/JwtAuthenticationFilter.java`
- Modify: `src/main/java/com/kasi/backend/security/config/SecurityConfig.java`
- Create: `src/test/java/com/kasi/backend/admin/controller/AdminManagementPermissionTest.java`
- Modify: `src/test/java/com/kasi/backend/BaseAuthTest.java`

- [ ] **Step 1: Write failing 401/403/authority tests**

Seed an ordinary administrator and add:

```java
@Test
@DisplayName("普通管理员访问管理接口返回403")
void ordinaryAdminCannotAccessManagement() throws Exception {
    String token = loginAsAdmin("operator", ADMIN_PASSWORD);
    mockMvc.perform(get("/api/admin/management")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(1003));
}
```

Also assert anonymous is 401, USER is 403, and the super administrator is not 403.

- [ ] **Step 2: Run permission tests and verify RED**

```powershell
.\mvnw.cmd -Dtest=AdminManagementPermissionTest test
```

Expected: FAIL because every administrator currently has only `ROLE_ADMIN` and no management route exists.

- [ ] **Step 3: Derive authorities from the current database row**

Refactor the filter account check to return the active administrator row and construct authorities as follows:

```java
List<SimpleGrantedAuthority> authorities = new ArrayList<>();
authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
if (Objects.equals(admin.getIsSuperAdmin(), 1)) {
    authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
}
```

Place this matcher before `/api/admin/**`:

```java
.requestMatchers("/api/admin/management/**").hasRole("SUPER_ADMIN")
.requestMatchers("/api/admin/**").hasRole("ADMIN")
```

Add this minimal controller shell only long enough to prove authorization; the next task replaces it with the service call:

```java
@RestController
@RequestMapping("/api/admin/management")
public class AdminManagementController {

    @GetMapping
    public ApiResponse<Void> getPage() {
        return ApiResponse.success();
    }
}
```

- [ ] **Step 4: Run permission and existing security tests**

```powershell
.\mvnw.cmd -Dtest=AdminManagementPermissionTest,SecurityPermissionTest,SessionAuthenticationTest test
```

Expected: PASS; ordinary ADMIN is 403 and the database-backed super administrator passes authorization.

- [ ] **Step 5: Commit the authorization slice**

```powershell
git add src/main/java/com/kasi/backend/security src/main/java/com/kasi/backend/admin/controller/AdminManagementController.java src/test/java/com/kasi/backend/BaseAuthTest.java src/test/java/com/kasi/backend/admin/controller/AdminManagementPermissionTest.java
git commit -m "feat: protect administrator management routes"
```

---

### Task 4: Implement Paginated Search and Detail

**Files:**
- Create: `src/main/java/com/kasi/backend/admin/service/AdminManagementService.java`
- Create: `src/main/java/com/kasi/backend/admin/service/impl/AdminManagementServiceImpl.java`
- Create: `src/main/java/com/kasi/backend/admin/vo/AdminListItemVO.java`
- Create: `src/main/java/com/kasi/backend/admin/vo/AdminDetailVO.java`
- Create: `src/main/java/com/kasi/backend/admin/vo/AdminPageVO.java`
- Modify: `src/main/java/com/kasi/backend/admin/controller/AdminManagementController.java`
- Modify: `src/main/java/com/kasi/backend/admin/mapper/SysAdminUserMapper.java`
- Modify: `src/main/resources/mapper/SysAdminUserMapper.xml`
- Create: `src/test/java/com/kasi/backend/admin/controller/AdminManagementQueryTest.java`
- Modify: `src/test/java/com/kasi/backend/ServiceImplementationStructureTest.java`

- [ ] **Step 1: Write failing query tests**

Cover default pagination, `id ASC`, each searchable field, trimmed empty keyword, maximum size validation, detail, missing detail `2006`, and absence of `password`.

```java
mockMvc.perform(get("/api/admin/management")
                .param("keyword", "operator")
                .header("Authorization", "Bearer " + loginAsAdmin()))
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.size").value(20))
        .andExpect(jsonPath("$.data.list[0].username").value("operator"))
        .andExpect(jsonPath("$.data.list[0].password").doesNotExist());
```

- [ ] **Step 2: Run query tests and verify RED**

```powershell
.\mvnw.cmd -Dtest=AdminManagementQueryTest,ServiceImplementationStructureTest test
```

Expected: FAIL because the service, VO, and mapper methods are absent.

- [ ] **Step 3: Add exact service and mapper contracts**

```java
public interface AdminManagementService {
    AdminPageVO getPage(AdminPageQueryDTO query);
    AdminDetailVO getById(Long id);
}
```

```java
long countByKeyword(@Param("keyword") String keyword);
List<SysAdminUser> findPage(@Param("keyword") String keyword,
                            @Param("offset") int offset,
                            @Param("size") int size);
```

SQL must use one parenthesized keyword predicate and stable ordering:

```sql
WHERE deleted_at IS NULL
  AND (#{keyword} IS NULL
       OR username LIKE CONCAT('%', #{keyword}, '%')
       OR real_name LIKE CONCAT('%', #{keyword}, '%')
       OR mobile LIKE CONCAT('%', #{keyword}, '%')
       OR email LIKE CONCAT('%', #{keyword}, '%'))
ORDER BY id ASC
LIMIT #{size} OFFSET #{offset}
```

Map Entity to VO inside the service and throw `ADMIN_MANAGEMENT_NOT_FOUND` for an absent detail.

Bind the list endpoint with `@Valid AdminPageQueryDTO query` so invalid page/size values reach the existing global validation handler rather than bypassing DTO validation.

- [ ] **Step 4: Run query and structure tests**

```powershell
.\mvnw.cmd -Dtest=AdminManagementQueryTest,ServiceImplementationStructureTest test
```

Expected: PASS, including interface/`impl` bean assertions.

- [ ] **Step 5: Commit query functionality**

```powershell
git add src/main/java/com/kasi/backend/admin src/main/resources/mapper/SysAdminUserMapper.xml src/test/java/com/kasi/backend/admin/controller/AdminManagementQueryTest.java src/test/java/com/kasi/backend/ServiceImplementationStructureTest.java
git commit -m "feat: query administrator accounts"
```

---

### Task 5: Create Ordinary Administrators

**Files:**
- Modify: `src/main/java/com/kasi/backend/admin/service/AdminManagementService.java`
- Modify: `src/main/java/com/kasi/backend/admin/service/impl/AdminManagementServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/admin/controller/AdminManagementController.java`
- Modify: `src/main/java/com/kasi/backend/admin/mapper/SysAdminUserMapper.java`
- Modify: `src/main/resources/mapper/SysAdminUserMapper.xml`
- Modify: `src/test/java/com/kasi/backend/admin/controller/AdminManagementMutationTest.java`

- [ ] **Step 1: Write failing creation tests**

Test success, BCrypt storage, `status=1`, `is_super_admin=0`, actor audit IDs, lowercased email, password mismatch `2011`, duplicate username/mobile/email `2007..2009`, username rejection for `_`, `-`, Chinese, and spaces, and password acceptance for letters, digits, and ASCII special symbols without mandatory category mixing.

```java
mockMvc.perform(post("/api/admin/management")
                .header("Authorization", "Bearer " + loginAsAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"finance","password":"StrongPass1",
                         "confirmPassword":"StrongPass1","realName":"张财务",
                         "email":"Finance@Example.COM"}
                        """))
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.isSuperAdmin").value(0));
```

- [ ] **Step 2: Run creation tests and verify RED**

```powershell
.\mvnw.cmd -Dtest=AdminManagementMutationTest test
```

Expected: FAIL because create is not implemented.

- [ ] **Step 3: Implement normalized, transactional creation**

Add:

```java
AdminDetailVO create(Long operatorId, CreateAdminDTO request);
```

Normalize mobile with `trimToNull`, email with `trimToNull(...).toLowerCase(Locale.ROOT)`, compare confirmation, check uniqueness, encode BCrypt, force ordinary/active values, and insert once. Catch `DuplicateKeyException`, re-query the three unique fields, and translate it to `2007`, `2008`, or `2009`; never return a raw 500 for a concurrent duplicate.

- [ ] **Step 4: Run creation tests**

```powershell
.\mvnw.cmd -Dtest=AdminManagementMutationTest test
```

Expected: all creation and validation cases PASS.

- [ ] **Step 5: Commit creation functionality**

```powershell
git add src/main/java/com/kasi/backend/admin src/main/resources/mapper/SysAdminUserMapper.xml src/test/java/com/kasi/backend/admin/controller/AdminManagementMutationTest.java
git commit -m "feat: create ordinary administrators"
```

---

### Task 6: Edit Ordinary Administrators and Current Profiles

**Files:**
- Modify: `src/main/java/com/kasi/backend/admin/service/AdminManagementService.java`
- Modify: `src/main/java/com/kasi/backend/admin/service/impl/AdminManagementServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/admin/controller/AdminManagementController.java`
- Modify: `src/main/java/com/kasi/backend/admin/service/AdminAuthService.java`
- Modify: `src/main/java/com/kasi/backend/admin/service/impl/AdminAuthServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/admin/controller/AdminAuthController.java`
- Modify: `src/main/java/com/kasi/backend/admin/mapper/SysAdminUserMapper.java`
- Modify: `src/main/resources/mapper/SysAdminUserMapper.xml`
- Modify: `src/test/java/com/kasi/backend/admin/controller/AdminManagementMutationTest.java`
- Modify: `src/test/java/com/kasi/backend/admin/controller/AdminAuthControllerTest.java`
- Create: `src/test/java/com/kasi/backend/admin/service/AdminManagementServiceTest.java`

- [ ] **Step 1: Write failing edit/profile/session tests**

Cover ordinary-admin edit, protected super-admin management edit `2010`, current profile edit for both administrator types, duplicate values, lowercased email, identifier-change old Token 401, non-identifier edit Token still valid, and Redis failure leaving MySQL unchanged.

```java
mockMvc.perform(put("/api/admin/auth/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"admin2","realName":"系统管理员",
                         "mobile":null,"email":"ADMIN2@EXAMPLE.COM","avatarUrl":null}
                        """))
        .andExpect(jsonPath("$.data.username").value("admin2"))
        .andExpect(jsonPath("$.data.email").value("admin2@example.com"));
```

- [ ] **Step 2: Run edit/profile tests and verify RED**

```powershell
.\mvnw.cmd -Dtest=AdminManagementMutationTest,AdminAuthControllerTest,AdminManagementServiceTest test
```

Expected: FAIL because profile update methods are absent.

- [ ] **Step 3: Implement one mapper update and two service entry points**

```java
int updateProfile(SysAdminUser admin);
```

```java
AdminDetailVO update(Long operatorId, Long targetId, UpdateAdminDTO request);
CurrentAdminVO updateProfile(Long adminId, UpdateAdminProfileDTO request);
```

Lock the target with `findByIdForUpdate`. Reject a management target with `isSuperAdmin=1`. Compare normalized `username`, mobile, and email with the current row. Only when at least one login identifier changes, call `beginMutation` before the mapper update and register `completeMutation` in `afterCommit`. A real-name/avatar/department/remark-only update must not call Redis.

Use this unit-test ordering assertion:

```java
InOrder order = inOrder(sessionService, sysAdminUserMapper);
order.verify(sessionService).beginMutation(SubjectType.ADMIN, targetId);
order.verify(sysAdminUserMapper).updateProfile(any(SysAdminUser.class));
```

- [ ] **Step 4: Run edit/profile tests**

```powershell
.\mvnw.cmd -Dtest=AdminManagementMutationTest,AdminAuthControllerTest,AdminManagementServiceTest test
```

Expected: PASS; identifier changes revoke old sessions and non-identifier changes do not require Redis.

- [ ] **Step 5: Commit profile functionality**

```powershell
git add src/main/java/com/kasi/backend/admin src/main/resources/mapper/SysAdminUserMapper.xml src/test/java/com/kasi/backend/admin
git commit -m "feat: edit administrator profiles"
```

---

### Task 7: Change Status and Reset Ordinary Administrator Passwords

**Files:**
- Modify: `src/main/java/com/kasi/backend/admin/service/AdminManagementService.java`
- Modify: `src/main/java/com/kasi/backend/admin/service/impl/AdminManagementServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/admin/controller/AdminManagementController.java`
- Modify: `src/main/java/com/kasi/backend/admin/mapper/SysAdminUserMapper.java`
- Modify: `src/main/resources/mapper/SysAdminUserMapper.xml`
- Modify: `src/main/java/com/kasi/backend/admin/controller/AdminAuthController.java`
- Modify: `src/main/java/com/kasi/backend/admin/service/AdminAuthService.java`
- Modify: `src/main/java/com/kasi/backend/admin/service/impl/AdminAuthServiceImpl.java`
- Modify: `src/test/java/com/kasi/backend/admin/controller/AdminManagementMutationTest.java`
- Modify: `src/test/java/com/kasi/backend/admin/controller/AdminAuthControllerTest.java`
- Modify: `src/test/java/com/kasi/backend/admin/service/AdminManagementServiceTest.java`

- [ ] **Step 1: Write failing status/password tests**

Cover disable, re-enable, protected super administrator, reset mismatch, reset success, old/new password login, old Token 401, password acceptance for visible ASCII, rejection for spaces/non-ASCII, `8..72` length boundaries, and Redis-first failures with unchanged MySQL.

```java
mockMvc.perform(patch("/api/admin/management/{id}/status", operatorId)
                .header("Authorization", "Bearer " + superToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":0}"))
        .andExpect(jsonPath("$.code").value(0));
```

- [ ] **Step 2: Run status/password tests and verify RED**

```powershell
.\mvnw.cmd -Dtest=AdminManagementMutationTest,AdminAuthControllerTest,AdminManagementServiceTest test
```

Expected: FAIL because management status/reset methods and administrator-specific self-change DTO wiring are absent.

- [ ] **Step 3: Implement Redis-first sensitive mutations**

Add service methods:

```java
void updateStatus(Long operatorId, Long targetId, UpdateAdminStatusDTO request);
void resetPassword(Long operatorId, Long targetId, ResetAdminPasswordDTO request);
```

For each method: lock target, throw `2006` if absent, throw `2010` if super administrator, validate password confirmation when applicable, call `beginMutation`, perform exactly one mapper update, require update count `1`, then register `completeMutation` after commit.

Change only the admin auth controller/service signature from shared `ChangePasswordDTO` to `AdminChangePasswordDTO`; leave `UserAuthController` and `UserAuthService` unchanged.

- [ ] **Step 4: Run status/password and session tests**

```powershell
.\mvnw.cmd -Dtest=AdminManagementMutationTest,AdminAuthControllerTest,AdminManagementServiceTest,SessionAuthenticationTest test
```

Expected: PASS; disabling and password reset invalidate all prior target sessions.

- [ ] **Step 5: Commit status/password functionality**

```powershell
git add src/main/java/com/kasi/backend/admin src/main/resources/mapper/SysAdminUserMapper.xml src/test/java/com/kasi/backend/admin src/test/java/com/kasi/backend/security/SessionAuthenticationTest.java
git commit -m "feat: manage administrator status and passwords"
```

---

### Task 8: Physically Delete Ordinary Administrators

**Files:**
- Modify: `src/main/java/com/kasi/backend/admin/service/AdminManagementService.java`
- Modify: `src/main/java/com/kasi/backend/admin/service/impl/AdminManagementServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/admin/controller/AdminManagementController.java`
- Modify: `src/main/java/com/kasi/backend/admin/mapper/SysAdminUserMapper.java`
- Modify: `src/main/resources/mapper/SysAdminUserMapper.xml`
- Modify: `src/test/java/com/kasi/backend/admin/controller/AdminManagementMutationTest.java`
- Modify: `src/test/java/com/kasi/backend/admin/service/AdminManagementServiceTest.java`
- Modify: `src/test/java/com/kasi/backend/security/SessionAuthenticationTest.java`

- [ ] **Step 1: Write failing physical-delete tests**

Test successful deletion, old Token 401, super-admin protection, missing target `2006`, Redis failure preserving the row, and recreation with the same username/mobile/email.

```java
mockMvc.perform(delete("/api/admin/management/{id}", operatorId)
                .header("Authorization", "Bearer " + superToken))
        .andExpect(jsonPath("$.code").value(0));
assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM sys_admin_user WHERE id = ?", Integer.class, operatorId))
        .isZero();
```

- [ ] **Step 2: Run delete tests and verify RED**

```powershell
.\mvnw.cmd -Dtest=AdminManagementMutationTest,AdminManagementServiceTest,SessionAuthenticationTest test
```

Expected: FAIL because physical delete is not implemented.

- [ ] **Step 3: Implement guarded physical DELETE**

```java
int deleteOrdinaryById(@Param("id") Long id);
```

```sql
DELETE FROM sys_admin_user
WHERE id = #{id} AND is_super_admin = 0
```

Service sequence: lock and validate target, reject the unique super administrator, call `beginMutation`, execute `DELETE`, require count `1`, register `completeMutation` after commit. Do not write `deleted_at`.

- [ ] **Step 4: Run delete and management regression tests**

```powershell
.\mvnw.cmd -Dtest=AdminManagementMutationTest,AdminManagementServiceTest,AdminManagementQueryTest,SessionAuthenticationTest test
```

Expected: PASS; deleted unique values can be reused.

- [ ] **Step 5: Commit physical deletion**

```powershell
git add src/main/java/com/kasi/backend/admin src/main/resources/mapper/SysAdminUserMapper.xml src/test/java/com/kasi/backend/admin src/test/java/com/kasi/backend/security/SessionAuthenticationTest.java
git commit -m "feat: physically delete ordinary administrators"
```

---

### Task 9: Close Concurrency, Structure, and Documentation Gaps

**Files:**
- Modify: `src/main/java/com/kasi/backend/admin/service/impl/AdminManagementServiceImpl.java`
- Modify: `src/test/java/com/kasi/backend/admin/service/AdminManagementServiceTest.java`
- Modify: `src/test/java/com/kasi/backend/ServiceImplementationStructureTest.java`
- Modify: `README.md`
- Modify: `AGENTS.md`

- [ ] **Step 1: Add missing failure-path and architecture tests**

Assert:

```java
assertThat(AdminManagementService.class).isInterface();
assertThat(applicationContext.getBean(AdminManagementService.class))
        .isInstanceOf(AdminManagementServiceImpl.class);
```

Add unit tests proving `beginMutation` exceptions prevent mapper writes for profile identifier change, status, password reset, and delete. Add a duplicate-key race test that expects `2007`, `2008`, or `2009`, never `500`.

- [ ] **Step 2: Run focused service/structure tests and verify RED if a path is uncovered**

```powershell
.\mvnw.cmd -Dtest=AdminManagementServiceTest,ServiceImplementationStructureTest test
```

Expected: any uncovered race/failure branch fails before the minimal service correction; all already-covered branches remain green.

- [ ] **Step 3: Make only the minimal service corrections**

Keep normalization, duplicate translation, and transaction synchronization as private helpers in `AdminManagementServiceImpl`; do not introduce a new framework abstraction. Ensure all mutation methods use this order:

```java
SessionMutation mutation = sessionService.beginMutation(SubjectType.ADMIN, targetId);
int updated = mapperWrite.getAsInt();
if (updated != 1) {
    throw new IllegalStateException("管理员写操作未生效");
}
completeAfterCommit(mutation);
```

- [ ] **Step 4: Update current documentation**

Update README with exact API paths, one-super-admin scope, physical deletion, real-name-only administrator schema, Redis mutation behavior, and current tests. Update AGENTS current-state bullets only; do not describe RBAC or future user management as implemented.

- [ ] **Step 5: Run focused regression tests**

```powershell
.\mvnw.cmd -Dtest=AdminManagementServiceTest,ServiceImplementationStructureTest,AdminManagementPermissionTest,AdminManagementQueryTest,AdminManagementMutationTest,AdminAuthControllerTest test
```

Expected: PASS with zero failures and zero errors.

- [ ] **Step 6: Commit hardening and documentation**

```powershell
git add src/main/java/com/kasi/backend/admin/service/impl/AdminManagementServiceImpl.java src/test/java/com/kasi/backend/admin/service/AdminManagementServiceTest.java src/test/java/com/kasi/backend/ServiceImplementationStructureTest.java README.md AGENTS.md
git commit -m "docs: document administrator management"
```

---

### Task 10: Run Final Java 25 Verification

**Files:**
- Verify all files changed by Tasks 1-9.

- [ ] **Step 1: Confirm intended worktree scope**

```powershell
git status --short --branch
git diff --stat origin/master...HEAD
```

Expected: only approved administrator-management, schema, tests, and documentation changes.

- [ ] **Step 2: Run the complete test suite under Java 25**

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
.\mvnw.cmd test
```

Expected: Java `25.0.3`; Maven `BUILD SUCCESS`; zero failures and zero errors.

- [ ] **Step 3: Run a clean compile check**

```powershell
.\mvnw.cmd -DskipTests compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Check diff integrity, stale administrator nickname references, and invalid seeded usernames**

```powershell
git diff --check
rg -n "nickname|getNickname|setNickname" src/main/java/com/kasi/backend/admin src/test/java/com/kasi/backend/admin src/main/resources/mapper/SysAdminUserMapper.xml
rg -n "disabled_admin" src/test/java/com/kasi/backend/BaseAuthTest.java
$migrationAdminBlock = (Get-Content -Raw src/main/resources/db/migration/V1__kasi_promotion.sql) -split '-- 推广用户表' | Select-Object -First 1
$testAdminBlock = (Get-Content -Raw src/test/resources/test-schema.sql) -split 'CREATE TABLE IF NOT EXISTS promotion_user' | Select-Object -First 1
if ($migrationAdminBlock -match 'nickname' -or $testAdminBlock -match 'nickname') { throw '管理员表仍包含 nickname' }
```

Expected: `git diff --check` exits `0`; both `rg` commands have no administrator nickname or invalid seeded-username hits; both administrator SQL blocks pass. Promotion-user nickname and username rules remain outside this scope.

- [ ] **Step 5: Review final commits without publishing**

```powershell
git log --oneline --decorate origin/master..HEAD
git status --short --branch
```

Expected: the approved design, plan, and implementation commits are present; worktree is clean. Do not push until the user explicitly requests it.
