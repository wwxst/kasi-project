# 12 位随机用户编号实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将推广用户的 `user_no` 改为固定 12 位 `SecureRandom` 纯数字字符串，保留自增 `id` 作为内部关联键，并从普通用户业务 JSON 中移除内部 `id`。

**Architecture:** 新增候选编号生成组件和负责唯一键重试的推广用户创建 Service。注册和管理员新增复用该 Service，只执行一次成功的用户插入；数据库唯一索引仍是最终保障。JWT、Redis、管理接口和未来业务关联继续使用自增 `id`，只收窄普通用户响应 VO 的展示字段。

**Tech Stack:** Java 25, Spring Boot 4.0.7, Spring Security, MyBatis 4.0.1, H2 MySQL mode, Flyway, JUnit 5, Mockito, AssertJ。

---

### Task 1: 添加 12 位随机编号生成器

**Files:**
- Create: `src/main/java/com/kasi/backend/user/generator/UserNumberGenerator.java`
- Test: `src/test/java/com/kasi/backend/user/generator/UserNumberGeneratorTest.java`

- [ ] **Step 1: Write the failing test**

Create `UserNumberGeneratorTest` with a controlled `RandomGenerator`:

```java
package com.kasi.backend.user.generator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@DisplayName("推广用户编号生成器")
class UserNumberGeneratorTest {

    private static final long MIN_INCLUSIVE = 100_000_000_000L;
    private static final long MAX_EXCLUSIVE = 1_000_000_000_000L;

    @Test
    @DisplayName("最小边界生成首位非零的12位纯数字编号")
    void generateWithMinimumValueReturnsFirstValidNumber() {
        RandomGenerator random = mock(RandomGenerator.class);
        when(random.nextLong(MIN_INCLUSIVE, MAX_EXCLUSIVE)).thenReturn(MIN_INCLUSIVE);

        String userNo = new UserNumberGenerator(random).generate();

        assertEquals("100000000000", userNo);
        assertTrue(userNo.matches("[1-9][0-9]{11}"));
        verify(random).nextLong(MIN_INCLUSIVE, MAX_EXCLUSIVE);
    }

    @Test
    @DisplayName("最大边界生成最后一个有效12位编号")
    void generateWithMaximumValueReturnsLastValidNumber() {
        RandomGenerator random = mock(RandomGenerator.class);
        when(random.nextLong(MIN_INCLUSIVE, MAX_EXCLUSIVE))
                .thenReturn(MAX_EXCLUSIVE - 1);

        String userNo = new UserNumberGenerator(random).generate();

        assertEquals("999999999999", userNo);
        assertTrue(userNo.matches("[1-9][0-9]{11}"));
    }

    @Test
    @DisplayName("可控随机源按固定顺序生成可复现编号")
    void generateWithControlledSourceReturnsRepeatableSequence() {
        RandomGenerator random = mock(RandomGenerator.class);
        when(random.nextLong(MIN_INCLUSIVE, MAX_EXCLUSIVE))
                .thenReturn(583_104_726_918L, 731_000_000_042L);
        UserNumberGenerator generator = new UserNumberGenerator(random);

        assertEquals("583104726918", generator.generate());
        assertEquals("731000000042", generator.generate());
        verify(random, times(2)).nextLong(MIN_INCLUSIVE, MAX_EXCLUSIVE);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd '-Dtest=UserNumberGeneratorTest' test
```

Expected: test compilation fails because `UserNumberGenerator` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.kasi.backend.user.generator;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.random.RandomGenerator;

@Component
public class UserNumberGenerator {

    private static final long MIN_INCLUSIVE = 100_000_000_000L;
    private static final long MAX_EXCLUSIVE = 1_000_000_000_000L;

    private final RandomGenerator randomGenerator;

    public UserNumberGenerator() {
        this(new SecureRandom());
    }

    UserNumberGenerator(RandomGenerator randomGenerator) {
        this.randomGenerator = Objects.requireNonNull(randomGenerator, "randomGenerator");
    }

    public String generate() {
        return Long.toString(randomGenerator.nextLong(MIN_INCLUSIVE, MAX_EXCLUSIVE));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run the same Maven command. Expected: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/kasi/backend/user/generator/UserNumberGenerator.java src/test/java/com/kasi/backend/user/generator/UserNumberGeneratorTest.java
git commit -m "feat: add random promotion user number generator"
```

### Task 2: 集中处理用户创建与编号碰撞重试

**Files:**
- Create: `src/main/java/com/kasi/backend/user/service/PromotionUserCreationService.java`
- Create: `src/main/java/com/kasi/backend/user/service/impl/PromotionUserCreationServiceImpl.java`
- Test: `src/test/java/com/kasi/backend/user/service/PromotionUserCreationServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Create the following unit test; it does not start Spring or inherit `BaseAuthTest`:

```java
package com.kasi.backend.user.service;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.user.entity.PromotionUser;
import com.kasi.backend.user.generator.UserNumberGenerator;
import com.kasi.backend.user.mapper.PromotionUserMapper;
import com.kasi.backend.user.service.impl.PromotionUserCreationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("推广用户创建服务")
class PromotionUserCreationServiceTest {

    @Mock private PromotionUserMapper promotionUserMapper;
    @Mock private UserNumberGenerator userNumberGenerator;
    @InjectMocks private PromotionUserCreationServiceImpl service;

    @Test
    @DisplayName("插入前写入随机编号并为未指定昵称补默认昵称")
    void createAssignsNumberBeforeInsert() {
        when(userNumberGenerator.generate()).thenReturn("583104726918");
        when(promotionUserMapper.insert(any(PromotionUser.class))).thenAnswer(invocation -> {
            PromotionUser user = invocation.getArgument(0);
            user.setId(42L);
            return 1;
        });
        PromotionUser user = new PromotionUser();
        user.setMobile("13600136000");

        service.create(user);

        assertThat(user.getUserNo()).isEqualTo("583104726918");
        assertThat(user.getNickname()).isEqualTo("用户583104726918");
        assertThat(user.getId()).isEqualTo(42L);
        verify(promotionUserMapper).insert(user);
    }

    @Test
    @DisplayName("随机编号唯一键冲突后重新生成并插入")
    void createRetriesWhenOnlyUserNumberCollides() {
        when(userNumberGenerator.generate()).thenReturn("100000000001", "200000000002");
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) {
                throw new DuplicateKeyException("Duplicate entry for key 'uk_user_no'");
            }
            PromotionUser user = invocation.getArgument(0);
            user.setId(43L);
            return 1;
        }).when(promotionUserMapper).insert(any(PromotionUser.class));
        when(promotionUserMapper.findByMobile("13600136000")).thenReturn(null);
        PromotionUser user = new PromotionUser();
        user.setMobile("13600136000");

        service.create(user);

        assertThat(user.getUserNo()).isEqualTo("200000000002");
        assertThat(user.getNickname()).isEqualTo("用户200000000002");
        verify(userNumberGenerator, times(2)).generate();
        verify(promotionUserMapper, times(2)).insert(user);
    }

    @Test
    @DisplayName("手机号唯一键冲突直接返回手机号重复错误")
    void createMapsContactConflictWithoutRetrying() {
        when(userNumberGenerator.generate()).thenReturn("583104726918");
        when(promotionUserMapper.insert(any(PromotionUser.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry"));
        PromotionUser existing = new PromotionUser();
        existing.setId(7L);
        when(promotionUserMapper.findByMobile("13600136000")).thenReturn(existing);
        PromotionUser user = new PromotionUser();
        user.setMobile("13600136000");

        assertThatThrownBy(() -> service.create(user))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(3006);
        verify(userNumberGenerator).generate();
        verify(promotionUserMapper).insert(user);
    }

    @Test
    @DisplayName("邮箱唯一键冲突直接返回邮箱重复错误")
    void createMapsEmailConflictWithoutRetrying() {
        when(userNumberGenerator.generate()).thenReturn("583104726918");
        when(promotionUserMapper.insert(any(PromotionUser.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry"));
        PromotionUser existing = new PromotionUser();
        existing.setId(8L);
        when(promotionUserMapper.findByEmail("user@example.com")).thenReturn(existing);
        PromotionUser user = new PromotionUser();
        user.setEmail("user@example.com");

        assertThatThrownBy(() -> service.create(user))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(3007);
        verify(userNumberGenerator).generate();
        verify(promotionUserMapper).insert(user);
    }

    @Test
    @DisplayName("连续三次编号冲突后创建失败")
    void createFailsAfterThreeNumberCollisions() {
        when(userNumberGenerator.generate())
                .thenReturn("100000000001", "200000000002", "300000000003");
        when(promotionUserMapper.insert(any(PromotionUser.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry for key 'uk_user_no'"));
        when(promotionUserMapper.findByMobile("13600136000")).thenReturn(null);
        PromotionUser user = new PromotionUser();
        user.setMobile("13600136000");

        assertThatThrownBy(() -> service.create(user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3");
        verify(userNumberGenerator, times(3)).generate();
        verify(promotionUserMapper, times(3)).insert(user);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
.\mvnw.cmd '-Dtest=PromotionUserCreationServiceTest' test
```

Expected: test compilation fails because the service interface and implementation do not exist.

- [ ] **Step 3: Write the service contract and implementation**

Create the interface:

```java
package com.kasi.backend.user.service;

import com.kasi.backend.user.entity.PromotionUser;

public interface PromotionUserCreationService {
    void create(PromotionUser user);
}
```

Create the implementation:

```java
package com.kasi.backend.user.service.impl;

import com.kasi.backend.common.exception.BusinessException;
import com.kasi.backend.common.exception.ErrorCode;
import com.kasi.backend.user.entity.PromotionUser;
import com.kasi.backend.user.generator.UserNumberGenerator;
import com.kasi.backend.user.mapper.PromotionUserMapper;
import com.kasi.backend.user.service.PromotionUserCreationService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PromotionUserCreationServiceImpl implements PromotionUserCreationService {

    private static final int MAX_ATTEMPTS = 3;

    private final PromotionUserMapper promotionUserMapper;
    private final UserNumberGenerator userNumberGenerator;

    @Transactional
    @Override
    public void create(PromotionUser user) {
        boolean defaultNickname = user.getNickname() == null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            user.setId(null);
            user.setUserNo(userNumberGenerator.generate());
            if (defaultNickname) {
                user.setNickname("用户" + user.getUserNo());
            }
            try {
                if (promotionUserMapper.insert(user) != 1) {
                    throw new IllegalStateException("推广用户新增未生效");
                }
                return;
            } catch (DuplicateKeyException exception) {
                throwIfContactConflict(user);
                if (attempt == MAX_ATTEMPTS) {
                    throw new IllegalStateException("连续3次生成推广用户编号均发生冲突", exception);
                }
            }
        }
        throw new IllegalStateException("推广用户创建流程未完成");
    }

    private void throwIfContactConflict(PromotionUser user) {
        if (user.getMobile() != null && promotionUserMapper.findByMobile(user.getMobile()) != null) {
            throw new BusinessException(ErrorCode.USER_MOBILE_DUPLICATE);
        }
        if (user.getEmail() != null && promotionUserMapper.findByEmail(user.getEmail()) != null) {
            throw new BusinessException(ErrorCode.USER_EMAIL_DUPLICATE);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run the same targeted command. Expected: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/kasi/backend/user/service/PromotionUserCreationService.java src/main/java/com/kasi/backend/user/service/impl/PromotionUserCreationServiceImpl.java src/test/java/com/kasi/backend/user/service/PromotionUserCreationServiceTest.java
git commit -m "feat: centralize promotion user creation"
```

### Task 3: 接入注册和管理员新增，移除临时编号及二次更新

**Files:**
- Modify: `src/main/java/com/kasi/backend/user/service/impl/UserAuthServiceImpl.java:23-115`
- Modify: `src/main/java/com/kasi/backend/user/service/impl/UserManagementServiceImpl.java:30-88,198-200`
- Modify: `src/main/java/com/kasi/backend/user/mapper/PromotionUserMapper.java:46-51`
- Modify: `src/main/resources/mapper/PromotionUserMapper.xml:94-99`
- Modify: `src/test/java/com/kasi/backend/user/UserManagementServiceTest.java:25-68`
- Modify: `src/test/java/com/kasi/backend/ServiceImplementationStructureTest.java:11-52`
- Modify: `src/test/java/com/kasi/backend/user/controller/UserAuthControllerTest.java:44-52`
- Modify: `src/test/java/com/kasi/backend/user/UserManagementMutationTest.java:18-35`
- Modify: `src/test/java/com/kasi/backend/user/PromotionUserStructureTest.java:25-51`

- [ ] **Step 1: Write the failing integration and structure assertions**

Change the registration assertion to the new contract:

```java
String userNo = jdbcTemplate.queryForObject(
        "SELECT user_no FROM promotion_user WHERE mobile = '13600136000'", String.class);
assertThat(userNo).matches("[1-9][0-9]{11}");
assertThat(userNo).doesNotStartWith("TMP-");
```

Add this import to `UserAuthControllerTest`:

```java
import static org.assertj.core.api.Assertions.assertThat;
```

Change the management-create assertion to:

```java
assertThat(stored.get("user_no").toString()).matches("[1-9][0-9]{11}");
```

Add the new Service interface/implementation pair to `ServiceImplementationStructureTest`:

```java
import com.kasi.backend.user.service.PromotionUserCreationService;
import com.kasi.backend.user.service.impl.PromotionUserCreationServiceImpl;
```

```java
assertThat(PromotionUserCreationService.class).isInterface();
assertThat(applicationContext.getBean(PromotionUserCreationService.class))
        .isInstanceOf(PromotionUserCreationServiceImpl.class);
```

Add `@Mock private PromotionUserCreationService promotionUserCreationService;` to `UserManagementServiceTest` so `@InjectMocks` can construct the updated management service. Move its current `createMapsConcurrentEmailDuplicateToBusinessError` scenario to `PromotionUserCreationServiceTest`, where the new insert/retry logic lives; remove the old test that stubs `promotionUserMapper.insert` on a service that no longer calls the mapper directly.

Add this import to that unit test:

```java
import com.kasi.backend.user.service.PromotionUserCreationService;
```

Add a structure assertion that fails while the obsolete second-write API remains:

```java
assertThat(Arrays.stream(PromotionUserMapper.class.getDeclaredMethods())
        .map(java.lang.reflect.Method::getName))
        .doesNotContain("updateUserNo");
assertThat(mapper).doesNotContain("updateUserNo");
```

Add `import com.kasi.backend.user.mapper.PromotionUserMapper;` to `PromotionUserStructureTest`.

- [ ] **Step 2: Run the affected tests to verify they fail**

```powershell
.\mvnw.cmd '-Dtest=UserAuthControllerTest,UserManagementMutationTest,UserManagementServiceTest,ServiceImplementationStructureTest,PromotionUserStructureTest' test
```

Expected: the 12-digit format and `updateUserNo`-absence assertions fail against the current implementation. The Service bean structure assertion may already pass because Task 2 created that Bean.

- [ ] **Step 3: Integrate `PromotionUserCreationService` into both creation paths**

In `UserAuthServiceImpl`, add the dependency:

```java
import com.kasi.backend.user.service.PromotionUserCreationService;

private final PromotionUserCreationService promotionUserCreationService;
```

Replace the current temporary-number, direct-insert, and `updateUserNo` block in `register` with:

```java
PromotionUser user = new PromotionUser();
if (isEmail(account)) {
    user.setEmail(account);
} else {
    user.setMobile(account);
}
user.setPassword(passwordEncoder.encode(request.getPassword()));
user.setStatus(UserStatus.NORMAL.getCode());
user.setRegisterSource(isEmail(account) ? "EMAIL" : "MOBILE");

promotionUserCreationService.create(user);
String userNo = user.getUserNo();
log.info("用户注册成功: userNo={}, account={}", userNo, account);
```

Remove the `UUID` import from this class.

In `UserManagementServiceImpl`, add the same creation-service dependency and replace the direct insert, duplicate-key catch, and `updateUserNo` block in `create` with:

```java
import com.kasi.backend.user.service.PromotionUserCreationService;
```

```java
PromotionUser user = new PromotionUser();
user.setPassword(passwordEncoder.encode(request.getPassword()));
user.setNickname(request.getNickname().trim());
user.setRealName(trimToNull(request.getRealName()));
user.setMobile(mobile);
user.setEmail(email);
user.setAvatarUrl(trimToNull(request.getAvatarUrl()));
user.setRemark(trimToNull(request.getRemark()));
user.setStatus(1);
user.setRegisterSource("ADMIN");
promotionUserCreationService.create(user);
return toDetailVO(promotionUserMapper.findById(user.getId()));
```

Remove `UUID`, `temporaryUserNo()`, and only the create-path use of `updateUserNo`; retain `DuplicateKeyException` and `mapDuplicateContact` because profile updates still translate mobile/email races.

Remove this method from `PromotionUserMapper.java`:

```java
int updateUserNo(@Param("id") Long id,
                 @Param("userNo") String userNo,
                 @Param("nickname") String nickname);
```

Remove the matching `<update id="updateUserNo">...</update>` block from `PromotionUserMapper.xml`.

- [ ] **Step 4: Run the affected tests to verify they pass**

```powershell
.\mvnw.cmd '-Dtest=UserNumberGeneratorTest,PromotionUserCreationServiceTest,UserAuthControllerTest,UserManagementMutationTest,UserManagementServiceTest,ServiceImplementationStructureTest,PromotionUserStructureTest' test
```

Expected: all selected tests pass with `BUILD SUCCESS`; no test or mapper reference to `updateUserNo` remains.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/kasi/backend/user/service/impl/UserAuthServiceImpl.java src/main/java/com/kasi/backend/user/service/impl/UserManagementServiceImpl.java src/main/java/com/kasi/backend/user/mapper/PromotionUserMapper.java src/main/resources/mapper/PromotionUserMapper.xml src/test/java/com/kasi/backend/user/UserManagementServiceTest.java src/test/java/com/kasi/backend/ServiceImplementationStructureTest.java src/test/java/com/kasi/backend/user/controller/UserAuthControllerTest.java src/test/java/com/kasi/backend/user/UserManagementMutationTest.java src/test/java/com/kasi/backend/user/PromotionUserStructureTest.java
git commit -m "refactor: generate promotion user numbers before insert"
```

### Task 4: 隐藏普通用户 JSON 中的内部 `id`

**Files:**
- Modify: `src/main/java/com/kasi/backend/user/vo/UserLoginVO.java:19-28`
- Modify: `src/main/java/com/kasi/backend/user/vo/CurrentUserVO.java:12-28`
- Modify: `src/main/java/com/kasi/backend/user/service/impl/UserAuthServiceImpl.java:155-190`
- Test: `src/test/java/com/kasi/backend/user/controller/UserAuthControllerTest.java:130-155,258-270`
- Modify: `src/test/java/com/kasi/backend/user/PromotionUserStructureTest.java:25-35`
- Modify: `src/test/java/com/kasi/backend/user/UserManagementQueryTest.java:17-28`

- [ ] **Step 1: Write the failing response-boundary assertions**

Add these expectations to the successful login and current-user tests:

```java
.andExpect(jsonPath("$.data.user.id").doesNotExist())
```

```java
.andExpect(jsonPath("$.data.id").doesNotExist())
```

Extend the structure test with:

```java
assertThat(fieldNames(CurrentUserVO.class)).doesNotContain("id");
assertThat(fieldNames(UserLoginVO.UserInfo.class)).doesNotContain("id");
```

Preserve the administrator contract explicitly in `UserManagementQueryTest`:

```java
.andExpect(jsonPath("$.data.list[0].id").isNumber())
```

- [ ] **Step 2: Run the focused tests to verify they fail**

```powershell
.\mvnw.cmd '-Dtest=UserAuthControllerTest,PromotionUserStructureTest,UserManagementQueryTest' test
```

Expected: the new `id`-absence assertions fail because both public VO classes still declare the field.

- [ ] **Step 3: Remove only the public response fields**

Change `UserLoginVO.UserInfo` to:

```java
@Data
@Builder
public static class UserInfo {
    private String userNo;
    private String nickname;
    private String mobile;
    private String email;
    private String avatarUrl;
}
```

Change `CurrentUserVO` to keep `userNo` and profile fields but remove `private Long id;`:

```java
@Data
@Builder
public class CurrentUserVO {

    private String userNo;
    private String nickname;
    private String realName;
    private String mobile;
    private String email;
    private String avatarUrl;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime createdAt;
}
```

Remove `.id(user.getId())` from the `UserLoginVO.UserInfo.builder()` and `CurrentUserVO.builder()` calls in `UserAuthServiceImpl`. Do not change `UserListItemVO`, `UserDetailVO`, JWT `sub`, `AuthContext`, or controller method parameters; those remain internal/admin contracts.

- [ ] **Step 4: Run the focused tests to verify they pass**

```powershell
.\mvnw.cmd '-Dtest=UserAuthControllerTest,PromotionUserStructureTest,UserManagementQueryTest,SecurityPermissionTest' test
```

Expected: all selected tests pass with `BUILD SUCCESS`, and management responses still contain their existing internal `id` fields.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/kasi/backend/user/vo/UserLoginVO.java src/main/java/com/kasi/backend/user/vo/CurrentUserVO.java src/main/java/com/kasi/backend/user/service/impl/UserAuthServiceImpl.java src/test/java/com/kasi/backend/user/controller/UserAuthControllerTest.java src/test/java/com/kasi/backend/user/PromotionUserStructureTest.java src/test/java/com/kasi/backend/user/UserManagementQueryTest.java
git commit -m "fix: hide internal user id from user responses"
```

### Task 5: 收紧生产与测试数据库契约并更新固定测试数据

**Files:**
- Modify: `src/main/resources/db/migration/V1__kasi_promotion.sql:41-62`
- Modify: `src/test/resources/test-schema.sql:27-45`
- Create: `src/test/java/com/kasi/backend/PromotionUserMigrationTest.java`
- Modify: `src/test/java/com/kasi/backend/BaseAuthTest.java:90-114`
- Modify: `src/test/java/com/kasi/backend/user/UserManagementQueryTest.java:20-48`
- Modify: `src/test/java/com/kasi/backend/user/UserManagementMutationTest.java:55-110`
- Modify: `src/test/java/com/kasi/backend/user/controller/UserAuthControllerTest.java:132-270`
- Modify: `src/test/java/com/kasi/backend/user/PromotionUserStructureTest.java:35-51`

- [ ] **Step 1: Write the failing migration contract test**

Create `PromotionUserMigrationTest` to run the real Flyway V1 against an isolated H2 MySQL-mode database and assert fixed length plus uniqueness:

```java
package com.kasi.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromotionUserMigrationTest {

    @Test
    @DisplayName("V1将推广用户编号定义为12位字符并保持唯一")
    void migrateV1DefinesFixedUniqueUserNumber() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:promotion_user_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .load().migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Integer length = jdbcTemplate.queryForObject(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_NAME = 'PROMOTION_USER' AND COLUMN_NAME = 'USER_NO'",
                Integer.class);
        assertThat(length).isEqualTo(12);

        jdbcTemplate.update("INSERT INTO promotion_user (user_no, password) VALUES (?, ?)",
                "100000000001", "hash");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO promotion_user (user_no, password) VALUES (?, ?)",
                "100000000001", "hash"))
                .isInstanceOf(DataAccessException.class);
    }
}
```

Extend `promotionUserSchemaAndMapperDoNotDeclareRemovedFields` in `PromotionUserStructureTest` with source-contract assertions for both schemas:

```java
assertThat(promotionBlock)
        .contains("`user_no`         CHAR(12)", "UNIQUE KEY `uk_user_no` (`user_no`)");
assertThat(testPromotionBlock)
        .contains("user_no CHAR(12) NOT NULL", "UNIQUE (user_no)");
```

- [ ] **Step 2: Run the migration test to verify it fails**

```powershell
.\mvnw.cmd '-Dtest=PromotionUserMigrationTest,PromotionUserStructureTest' test
```

Expected: the migration length assertion and both source-contract assertions fail because V1 and the test schema still use `VARCHAR(32)`.

- [ ] **Step 3: Update schema and fixtures**

In `V1__kasi_promotion.sql`, replace the promotion-user column definition with:

```sql
`user_no`         CHAR(12)        NOT NULL COMMENT '12位随机数字业务用户编号',
```

In `src/test/resources/test-schema.sql`, replace the matching H2 definition with:

```sql
user_no CHAR(12) NOT NULL,
```

Keep both unique constraints unchanged. In `BaseAuthTest`, define reusable constants and use them for all three fixtures:

```java
protected static final String PRIMARY_USER_NO = "583104726918";
protected static final String MOBILE_USER_NO = "731000000042";
protected static final String DISABLED_USER_NO = "904275816330";
```

Replace the three `KS00000x` JDBC arguments with those constants. Replace all hard-coded `KS000001`/`KS000002` references in `UserManagementQueryTest`, `UserManagementMutationTest`, and `UserAuthControllerTest` with the inherited constants; preserve the existing `id ASC` ordering assertions. Change generated-number assertions to `matches("[1-9][0-9]{11}")` and add `import static org.assertj.core.api.Assertions.assertThat;` to `UserAuthControllerTest` if it is not already present.

- [ ] **Step 4: Run migration and user tests to verify they pass**

```powershell
.\mvnw.cmd '-Dtest=PromotionUserMigrationTest,PromotionUserStructureTest,UserAuthControllerTest,UserManagementQueryTest,UserManagementMutationTest' test
```

Expected: all selected tests pass with `BUILD SUCCESS`; inserted generated values are exactly 12 digits and duplicate values are rejected by the database.

- [ ] **Step 5: Commit**

```powershell
git add src/main/resources/db/migration/V1__kasi_promotion.sql src/test/resources/test-schema.sql src/test/java/com/kasi/backend/PromotionUserMigrationTest.java src/test/java/com/kasi/backend/BaseAuthTest.java src/test/java/com/kasi/backend/user/UserManagementQueryTest.java src/test/java/com/kasi/backend/user/UserManagementMutationTest.java src/test/java/com/kasi/backend/user/controller/UserAuthControllerTest.java src/test/java/com/kasi/backend/user/PromotionUserStructureTest.java
git commit -m "refactor: enforce random promotion user number schema"
```

### Task 6: 同步当前文档与旧设计中的用户编号契约

**Files:**
- Modify: `AGENTS.md:26`
- Modify: `README.md:150,158,220,261`
- Modify: `docs/superpowers/specs/2026-08-13-user-management-design.md:62,170`
- Modify: `docs/superpowers/plans/2026-08-13-user-management.md:64`

- [ ] **Step 1: Write the documentation assertions**

Before editing, run this stale-contract scan and record the current matches:

```powershell
rg -n 'KS000001|KS%06d|user_no\(基于自增|user_no.*改为基于' AGENTS.md README.md docs/superpowers/specs docs/superpowers/plans
```

Expected before the edit: matches exist in README, the 2026-08-13 specification, and its implementation plan. The current AGENTS line does not name the old format but still requires the new public/internal identifier boundary wording.

- [ ] **Step 2: Update the current wording**

Use these exact contract replacements:

```text
AGENTS.md:
推广用户不使用独立 `username`，只用手机号或邮箱登录；`user_no` 是 12 位随机数字展示编号，内部关联继续使用自增 `id`。
```

```text
README.md storage table:
| MySQL | `promotion_user` | 推广用户 | user_no(12位随机数字字符串), password(BCrypt), nickname, mobile, email, status, register_source |
```

```text
README.md implementation note:
`user_no` 是后端生成的 12 位随机数字字符串，不包含自增 ID、时间戳或用户顺序；`promotion_user.id` 仍是内部主键和业务关联键。普通用户登录和 `/api/user/auth/me` 的 JSON 不返回内部 `id`，JWT 的 `sub` 仍按现有认证契约保存内部 `id`。
```

Replace the old user-management spec and plan statements that say `KS` plus a six-digit auto-increment ID with the same 12-digit random-number contract, and retain the explicit distinction between public `userNo` and internal `id`.

- [ ] **Step 3: Verify documentation consistency**

```powershell
rg -n 'KS000001|KS%06d|user_no\(基于自增|user_no.*改为基于' AGENTS.md README.md docs/superpowers/specs/2026-08-13-user-management-design.md docs/superpowers/plans/2026-08-13-user-management.md
git diff --check
```

Expected: the stale-contract scan returns no matches in those current documents, and `git diff --check` exits 0. The approved `2026-08-15-random-user-number-design.md` remains the detailed source of truth for the ordinary-hide JWT boundary.

- [ ] **Step 4: Commit**

```powershell
git add AGENTS.md README.md docs/superpowers/specs/2026-08-13-user-management-design.md docs/superpowers/plans/2026-08-13-user-management.md
git commit -m "docs: document random promotion user numbers"
```

### Task 7: 完整验证并交付分支

**Files:**
- Verify all files changed by Tasks 1-6; do not add generated `target/` output or unrelated worktree files.

- [ ] **Step 1: Run the complete Java 25 test suite**

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
.\mvnw.cmd -v
.\mvnw.cmd test
```

Expected: `BUILD SUCCESS`, with `Failures: 0` and `Errors: 0` in the final Surefire summary. The test count may increase from the clean baseline of 137 because this feature adds generator, creation, migration, and response-boundary tests.

- [ ] **Step 2: Run the compile-only verification**

```powershell
.\mvnw.cmd -DskipTests compile
```

Expected: `BUILD SUCCESS` under Java 25.

- [ ] **Step 3: Verify the final diff and worktree**

```powershell
git diff --check
git status --short --branch
git log --oneline -8
```

Expected: `git diff --check` exits 0; only intended commits/files are present on `codex/random-user-number`; no generated files or unrelated changes are staged. The worktree should be clean before handoff.

- [ ] **Step 4: Confirm no remaining uncommitted files**

```powershell
git status --short
```

Expected: no output. Do not push or merge without a separate user instruction.
