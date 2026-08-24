# Default Super Administrator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the production Flyway `V1` migration create the enabled super administrator `kasiadmin` whose BCrypt password matches `kasi123456`.

**Architecture:** Keep initialization inside the existing one-time `V1__kasi_promotion.sql` because the project is still in the disposable-database development phase and has no production migration history to preserve. Verify the real production migration against an H2 database in MySQL mode, then synchronize current-state documentation without adding a runtime initializer or a compatibility migration.

**Tech Stack:** Java 25, Spring Boot 4.0.7, Flyway 11.14.1, MySQL 8 SQL, H2 2.4.240 MySQL mode, JUnit 5, AssertJ, Spring Security BCrypt

---

### Task 1: Test and seed the default super administrator

**Files:**
- Create: `src/test/java/com/kasi/backend/DefaultSuperAdminMigrationTest.java`
- Modify: `src/main/resources/db/migration/V1__kasi_promotion.sql`

- [ ] **Step 1: Write the failing production-migration test**

Create `src/test/java/com/kasi/backend/DefaultSuperAdminMigrationTest.java` with this complete test:

```java
package com.kasi.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSuperAdminMigrationTest {

    private static final String DEFAULT_USERNAME = "kasiadmin";
    private static final String DEFAULT_PASSWORD = "kasi123456";

    @Test
    @DisplayName("V1迁移植入可登录的默认超级管理员")
    void migrateV1SeedsDefaultSuperAdmin() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:default_admin_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        List<Map<String, Object>> admins = jdbcTemplate.queryForList(
                "SELECT username, password, real_name, status, is_super_admin FROM sys_admin_user");

        assertThat(admins).singleElement().satisfies(admin -> {
            assertThat(admin.get("username")).isEqualTo(DEFAULT_USERNAME);
            assertThat(admin.get("real_name")).isEqualTo("系统管理员");
            assertThat(((Number) admin.get("status")).intValue()).isEqualTo(1);
            assertThat(((Number) admin.get("is_super_admin")).intValue()).isEqualTo(1);

            String storedPassword = (String) admin.get("password");
            assertThat(storedPassword).isNotEqualTo(DEFAULT_PASSWORD);
            assertThat(new BCryptPasswordEncoder().matches(DEFAULT_PASSWORD, storedPassword)).isTrue();
        });
    }
}
```

- [ ] **Step 2: Run the focused test and verify the red state**

Run in PowerShell with Java 25:

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=DefaultSuperAdminMigrationTest test
```

Expected: `FAILURE`; Flyway successfully applies `V1`, then AssertJ reports that `admins` is empty instead of containing one element. The failure must be caused by the missing seed row, not by compilation or migration syntax.

- [ ] **Step 3: Add the minimal seed SQL to V1**

Append this block after the `promotion_user` table definition in `src/main/resources/db/migration/V1__kasi_promotion.sql`:

```sql

-- 初始超级管理员（首次登录后应立即修改默认密码）
INSERT INTO `sys_admin_user` (`username`, `password`, `real_name`, `status`, `is_super_admin`)
VALUES ('kasiadmin',
        '$2a$10$mROjhwtfAn0JbImE7Cp4M.u3cBPvWwXGDesSyrBvB69jON/DwzeKm',
        '系统管理员',
        1,
        1);
```

The BCrypt hash above has already been checked with Spring Security BCrypt and matches `kasi123456`. Do not add plaintext password storage, conditional insert behavior, a `V2` migration, or a startup initializer.

- [ ] **Step 4: Run the focused test and verify the green state**

Run:

```powershell
.\mvnw.cmd -Dtest=DefaultSuperAdminMigrationTest test
```

Expected: `BUILD SUCCESS`; one test runs and passes. The query sees exactly one enabled super administrator and BCrypt matches the requested password.

- [ ] **Step 5: Commit the migration and regression test**

Run:

```powershell
git add src/main/resources/db/migration/V1__kasi_promotion.sql src/test/java/com/kasi/backend/DefaultSuperAdminMigrationTest.java
git diff --cached --check
git commit -m "feat: seed default super administrator"
```

Expected: only the migration and its focused test are committed.

### Task 2: Synchronize current-state documentation

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-08-13-admin-management-design.md`

- [ ] **Step 1: Update README startup, database, structure, and test sections**

Make these exact behavior changes in `README.md`:

1. Change the `V1__kasi_promotion.sql` tree comment from “数据库迁移脚本（2张表）” to “数据库迁移脚本（2张表 + 默认超级管理员）”.
2. Replace the single first-start sentence with these paragraphs:

```markdown
首次启动时 Flyway 会扫描 `db/migration/` 下的 `V1__kasi_promotion.sql`，创建所需表并植入唯一的初始超级管理员：

- 账号：`kasiadmin`
- 初始密码：`kasi123456`

密码在数据库中保存为 BCrypt 哈希。首次登录后应立即通过 `PUT /api/admin/auth/password` 修改默认密码。项目当前仍处于可重建数据库的开发阶段；如果开发数据库已经执行过旧版 `V1`，应删除并重新创建数据库，不能在保留旧 Flyway 校验和的情况下直接替换迁移脚本。
```

3. After the database table summary, add:

```markdown
`V1__kasi_promotion.sql` 在建表后直接插入 `kasiadmin`，并固定写入 `status=1`、`is_super_admin=1`。该初始化同时用于开发环境重建和未来生产环境首次建库，不会在应用每次启动时重复执行。
```

4. Add `DefaultSuperAdminMigrationTest.java` to the test tree and add this row to the existing test-class table:

```markdown
| `DefaultSuperAdminMigrationTest` | 使用 Flyway + H2 MySQL 模式验证生产 V1 初始化账号、权限字段和 BCrypt 密码 |
```

- [ ] **Step 2: Update AGENTS current facts and test routing**

Make these exact behavior changes in `AGENTS.md`:

1. Replace the existing database-migration current-state bullet with:

```markdown
- 数据库迁移：`db/migration/V1__kasi_promotion.sql` 定义 `sys_admin_user`、`promotion_user` 两张持久表，并植入唯一初始超级管理员 `kasiadmin / kasi123456`（数据库仅保存 BCrypt 哈希，`status=1`、`is_super_admin=1`）；验证码和密码重置 Token 等临时数据由 Redis（`vc:*`、`pwd:*` 键）管理，TTL 自动过期。
```

2. Add this current-stage rule immediately after the migration bullet:

```markdown
- 项目当前仍处于开发阶段，数据库可以删除重建；修改已执行的 `V1` 后必须重建开发数据库。未来生产首次建库也使用同一 `V1` 初始化账号，不新增运行时账号植入器。
```

3. Add this test exception after the `BaseAuthTest` inheritance rule:

```markdown
- 数据库迁移测试不属于认证接口测试，可以不继承 `BaseAuthTest`；应使用隔离的 H2 MySQL 模式数据库实际执行生产迁移。
```

4. Add this security rule after the password-storage rule:

```markdown
- 固定初始超级管理员凭据是当前明确的建库契约；不得把明文密码写入数据库、日志或 API 响应，并应在首次登录后立即修改默认密码。
```

- [ ] **Step 3: Resolve the contradiction in the administrator design document**

In `docs/superpowers/specs/2026-08-13-admin-management-design.md`:

1. Replace the out-of-scope sentence about the initial administrator with:

```markdown
- 初始超级管理员引导流程不属于管理员管理 API；初始账号由 `V1__kasi_promotion.sql` 在首次建库时提供。
```

2. Replace the old “test-only” credential paragraph with:

```markdown
唯一超级管理员的固定初始化与测试凭据为：

- 账号：`kasiadmin`
- 初始密码：`kasi123456`

该凭据由生产 Flyway `V1` 在首次建库时植入，也由 H2 测试数据和认证测试请求复用。数据库只保存 BCrypt 哈希；部署后应立即通过管理员本人改密接口修改默认密码。
```

Do not rewrite the historical implementation plan in `docs/superpowers/plans/2026-08-13-admin-management.md`.

- [ ] **Step 4: Verify documentation consistency**

Run:

```powershell
rg -n "kasiadmin|kasi123456|DefaultSuperAdminMigrationTest|V1__kasi_promotion" README.md AGENTS.md docs/superpowers/specs/2026-08-13-admin-management-design.md
rg -n "不得写入生产 Flyway|不构成生产初始管理员方案" README.md AGENTS.md docs/superpowers/specs
git diff --check
```

Expected: the first command shows the new initialization contract; the second command has no matches; `git diff --check` exits `0` with no whitespace errors.

- [ ] **Step 5: Commit the documentation update**

Run:

```powershell
git add README.md AGENTS.md docs/superpowers/specs/2026-08-13-admin-management-design.md
git diff --cached --check
git commit -m "docs: document default super administrator"
```

Expected: only the three current-state documentation files are committed.

### Task 3: Run final verification

**Files:**
- Verify: `src/main/resources/db/migration/V1__kasi_promotion.sql`
- Verify: `src/test/java/com/kasi/backend/DefaultSuperAdminMigrationTest.java`
- Verify: `README.md`
- Verify: `AGENTS.md`
- Verify: `docs/superpowers/specs/2026-08-13-admin-management-design.md`

- [ ] **Step 1: Confirm Java and Maven use JDK 25**

Run:

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
.\mvnw.cmd -v
```

Expected: both commands report Java 25.

- [ ] **Step 2: Re-run the focused migration test**

Run:

```powershell
.\mvnw.cmd -Dtest=DefaultSuperAdminMigrationTest test
```

Expected: `BUILD SUCCESS`, with `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 3: Run the full test suite**

Run:

```powershell
.\mvnw.cmd test
```

Expected: `BUILD SUCCESS`; Maven reports zero failures and zero errors for the complete suite.

- [ ] **Step 4: Check repository integrity and scope**

Run:

```powershell
git diff --check
git status --short --branch
git log -4 --oneline --decorate
```

Expected: `git diff --check` has no output; the worktree is clean; recent history contains the design, plan, migration/test, and documentation commits. Do not push unless the user separately requests it.
