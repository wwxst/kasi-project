# GoodShort Local Catalog Seed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an idempotent, development-only GoodShort catalog seed, verify it against H2 and the current local MySQL database, and document its safe use.

**Architecture:** Keep fake data outside Flyway and application runtime code in one manually executed SQL file under `scripts/dev`. The script uses database-generated internal IDs, stable GoodShort-like external IDs, an exact safe-connection guard, and unique-key upserts; a focused H2 MySQL-mode test executes the same file twice and exercises the real-connection rejection path.

**Tech Stack:** MySQL 8 SQL, H2 2.4.240 in MySQL mode, Spring JDBC `ScriptUtils`, Flyway, JUnit 5, AssertJ, Maven Wrapper, Java 25.

---

## File Structure

- Create `scripts/dev/seed_goodshort_drama_catalog.sql`: the only executable seed artifact; validates the local-only connection, generates 24 dramas, 204 episodes, and four sync checkpoints in one transaction.
- Create `src/test/java/com/kasi/backend/drama/GoodShortDramaCatalogSeedTest.java`: migrates an isolated H2 database, executes the production seed file, verifies coverage/idempotency, and verifies refusal when a real connection exists.
- Modify `README.md`: add the development seed to the repository map, document interactive/manual execution, expected counts, and production prohibition.
- Modify `AGENTS.md`: record the current development seed contract and add its focused test command.
- Modify `docs/superpowers/specs/2026-08-21-goodshort-local-catalog-seed-design.md`: change status only after implementation and local MySQL verification succeed.

### Task 1: Add the failing seed contract tests

**Files:**
- Create: `src/test/java/com/kasi/backend/drama/GoodShortDramaCatalogSeedTest.java`
- Test: `src/test/java/com/kasi/backend/drama/GoodShortDramaCatalogSeedTest.java`

- [ ] **Step 1: Create a migration-backed test harness that executes the repository SQL file**

Create the test class with an isolated database per test and a direct `ScriptUtils` call:

```java
package com.kasi.backend.drama;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GoodShort 本地目录假数据脚本")
class GoodShortDramaCatalogSeedTest {

    private static final FileSystemResource SEED_SCRIPT =
            new FileSystemResource("scripts/dev/seed_goodshort_drama_catalog.sql");

    @Test
    @DisplayName("脚本重复执行时生成完整目录且不产生重复记录")
    void executeTwiceCreatesCompleteIdempotentCatalog() throws SQLException {
        DataSource dataSource = migrateAllMigrations();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        executeSeed(dataSource);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM short_drama_connection", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM short_drama_connection "
                        + "WHERE connection_name = 'GoodShort 本地假数据' AND status = 0 "
                        + "AND filing_mode = 'MANUAL' AND partner_id IS NULL "
                        + "AND api_key_ciphertext IS NULL AND base_url IS NULL", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_drama", Long.class))
                .isEqualTo(24L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_drama_content", Long.class))
                .isEqualTo(204L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_sync_checkpoint", Long.class))
                .isEqualTo(4L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_drama WHERE language = 'ENGLISH'", Long.class))
                .isEqualTo(12L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_drama WHERE language = 'SPANISH'", Long.class))
                .isEqualTo(12L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(DISTINCT local_status) FROM provider_drama", Long.class))
                .isEqualTo(3L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_drama WHERE cover_url IS NULL", Long.class))
                .isGreaterThan(0L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(DISTINCT is_free) FROM provider_drama_content", Long.class))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "SELECT MIN(episode_count) FROM (SELECT drama_id, COUNT(*) episode_count "
                        + "FROM provider_drama_content GROUP BY drama_id) counts", Long.class))
                .isEqualTo(5L);
        assertThat(jdbc.queryForObject(
                "SELECT MAX(episode_count) FROM (SELECT drama_id, COUNT(*) episode_count "
                        + "FROM provider_drama_content GROUP BY drama_id) counts", Long.class))
                .isEqualTo(12L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM provider_sync_checkpoint WHERE status = 'SUCCESS'", Long.class))
                .isEqualTo(4L);

        List<Long> firstDramaIds = jdbc.queryForList(
                "SELECT id FROM provider_drama ORDER BY external_drama_id", Long.class);
        List<Long> firstContentIds = jdbc.queryForList(
                "SELECT id FROM provider_drama_content ORDER BY drama_id, sequence_no", Long.class);

        executeSeed(dataSource);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_drama", Long.class))
                .isEqualTo(24L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_drama_content", Long.class))
                .isEqualTo(204L);
        assertThat(jdbc.queryForList(
                "SELECT id FROM provider_drama ORDER BY external_drama_id", Long.class))
                .isEqualTo(firstDramaIds);
        assertThat(jdbc.queryForList(
                "SELECT id FROM provider_drama_content ORDER BY drama_id, sequence_no", Long.class))
                .isEqualTo(firstContentIds);
    }

    @Test
    @DisplayName("存在真实 GoodShort 连接时拒绝写入且保留原配置")
    void executeWithRealConnectionRejectsWithoutMutation() {
        DataSource dataSource = migrateAllMigrations();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long providerId = jdbc.queryForObject(
                "SELECT id FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class);
        jdbc.update("INSERT INTO short_drama_connection "
                        + "(provider_id, connection_name, partner_id, api_key_ciphertext, currency, status, filing_mode, base_url) "
                        + "VALUES (?, 'GoodShort 真实接入', 'real-pid', 'real-ciphertext', 'USD', 1, 'API', 'https://example.test')",
                providerId);

        assertThatThrownBy(() -> executeSeed(dataSource))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_drama", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT partner_id FROM short_drama_connection WHERE provider_id = ?",
                String.class, providerId)).isEqualTo("real-pid");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM short_drama_connection WHERE provider_id = ?",
                Integer.class, providerId)).isEqualTo(1);
    }

    private static void executeSeed(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, SEED_SCRIPT);
        }
    }

    private static DataSource migrateAllMigrations() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:goodshort_seed_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        return dataSource;
    }
}
```

- [ ] **Step 2: Run the focused test and confirm the red state is caused by the missing seed file**

Run:

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=GoodShortDramaCatalogSeedTest test
```

Expected: `Tests run: 2, Failures: 0, Errors: 2` and both errors identify `scripts/dev/seed_goodshort_drama_catalog.sql` as missing or unreadable. Do not weaken assertions to make this pass.

### Task 2: Implement the guarded idempotent SQL seed

**Files:**
- Create: `scripts/dev/seed_goodshort_drama_catalog.sql`
- Test: `src/test/java/com/kasi/backend/drama/GoodShortDramaCatalogSeedTest.java`

- [ ] **Step 1: Add the development-only header, transaction, and connection guards**

Begin the script with the exact safety boundary below. The deliberately invalid inserts are assertions: their `NULL` values violate `NOT NULL` only when the precondition query returns a row.

```sql
-- LOCAL DEVELOPMENT ONLY. DO NOT RUN IN PRODUCTION OR SHARED ENVIRONMENTS.
-- Creates a disabled, credential-free GoodShort connection and deterministic catalog fixtures.

BEGIN;

INSERT INTO short_drama_connection
    (provider_id, connection_name, currency, status, filing_mode)
SELECT NULL, 'GoodShort 本地假数据', 'USD', 0, 'MANUAL'
WHERE NOT EXISTS (
    SELECT 1 FROM short_drama_provider WHERE provider_code = 'GOODSHORT'
);

INSERT INTO short_drama_connection
    (provider_id, connection_name, currency, status, filing_mode)
SELECT p.id, NULL, 'USD', 0, 'MANUAL'
FROM short_drama_provider p
JOIN short_drama_connection c ON c.provider_id = p.id
WHERE p.provider_code = 'GOODSHORT'
  AND (
      c.connection_name <> 'GoodShort 本地假数据'
      OR c.status <> 0
      OR c.filing_mode <> 'MANUAL'
      OR c.partner_id IS NOT NULL
      OR c.api_key_ciphertext IS NOT NULL
      OR c.base_url IS NOT NULL
  );

INSERT INTO short_drama_connection
    (provider_id, connection_name, partner_id, api_key_ciphertext, currency,
     status, filing_mode, base_url)
SELECT p.id, 'GoodShort 本地假数据', NULL, NULL, 'USD', 0, 'MANUAL', NULL
FROM short_drama_provider p
WHERE p.provider_code = 'GOODSHORT'
  AND NOT EXISTS (
      SELECT 1 FROM short_drama_connection c WHERE c.provider_id = p.id
  );

CREATE TEMPORARY TABLE dev_goodshort_seed_number (
    n INT NOT NULL PRIMARY KEY
);

INSERT INTO dev_goodshort_seed_number (n) VALUES
    (1), (2), (3), (4), (5), (6), (7), (8),
    (9), (10), (11), (12), (13), (14), (15), (16),
    (17), (18), (19), (20), (21), (22), (23), (24);
```

- [ ] **Step 2: Insert or update the 24 deterministic dramas without specifying internal IDs**

Append this set-based upsert. It covers two languages, four genres, three remote statuses, three local statuses, deterministic timestamps, stable placeholder covers, and null-cover fallback cases.

```sql
INSERT INTO provider_drama
    (connection_id, external_drama_id, title, original_title, description,
     cover_url, language, drama_type, remote_show_status, local_status,
     remote_updated_at, last_seen_at)
SELECT c.id,
       CONCAT('990000', LPAD(nums.n, 2, '0')),
       CASE WHEN MOD(nums.n, 2) = 1
            THEN CONCAT('GoodShort Demo ', LPAD(nums.n, 2, '0'))
            ELSE CONCAT('Serie de Prueba ', LPAD(nums.n, 2, '0')) END,
       CONCAT('Original Demo Title ', LPAD(nums.n, 2, '0')),
       CONCAT('Local development catalog fixture number ', nums.n, '.'),
       CASE WHEN MOD(nums.n, 7) = 0 THEN NULL
            ELSE CONCAT('https://placehold.co/300x450/png?text=GoodShort+', LPAD(nums.n, 2, '0')) END,
       CASE WHEN MOD(nums.n, 2) = 1 THEN 'ENGLISH' ELSE 'SPANISH' END,
       CASE MOD(nums.n - 1, 4)
            WHEN 0 THEN 'ROMANCE'
            WHEN 1 THEN 'REVENGE'
            WHEN 2 THEN 'FAMILY'
            ELSE 'SUSPENSE' END,
       CASE MOD(nums.n - 1, 3)
            WHEN 0 THEN 'ONLINE'
            WHEN 1 THEN 'OFFLINE'
            ELSE 'COMING_SOON' END,
       CASE MOD(nums.n - 1, 3)
            WHEN 0 THEN 'DRAFT'
            WHEN 1 THEN 'PUBLISHED'
            ELSE 'OFFLINE' END,
       TIMESTAMPADD(DAY, -nums.n, '2026-08-21 12:00:00'),
       '2026-08-21 12:00:00'
FROM short_drama_provider p
JOIN short_drama_connection c ON c.provider_id = p.id
CROSS JOIN dev_goodshort_seed_number nums
WHERE p.provider_code = 'GOODSHORT'
  AND c.connection_name = 'GoodShort 本地假数据'
  AND c.status = 0
  AND c.filing_mode = 'MANUAL'
  AND c.partner_id IS NULL
  AND c.api_key_ciphertext IS NULL
  AND c.base_url IS NULL
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    original_title = VALUES(original_title),
    description = VALUES(description),
    cover_url = VALUES(cover_url),
    language = VALUES(language),
    drama_type = VALUES(drama_type),
    remote_show_status = VALUES(remote_show_status),
    local_status = VALUES(local_status),
    remote_updated_at = VALUES(remote_updated_at),
    last_seen_at = VALUES(last_seen_at);
```

- [ ] **Step 3: Insert or update 5 to 12 episodes per drama**

Append the episode upsert. The repeating counts `5,6,7,8,9,10,11,12` across 24 dramas total 204 episodes.

```sql
INSERT INTO provider_drama_content
    (drama_id, external_content_id, sequence_no, title, is_free,
     duration_seconds, remote_updated_at)
SELECT drama.id,
       CONCAT(drama.external_drama_id, LPAD(episode.n, 3, '0')),
       episode.n,
       CONCAT('Episode ', LPAD(episode.n, 2, '0')),
       CASE WHEN episode.n <= 2 THEN 1 ELSE 0 END,
       55 + MOD(drama_number.n * 7 + episode.n * 11, 66),
       TIMESTAMPADD(HOUR, -episode.n,
                    TIMESTAMPADD(DAY, -drama_number.n, '2026-08-21 12:00:00'))
FROM short_drama_provider p
JOIN short_drama_connection c ON c.provider_id = p.id
CROSS JOIN dev_goodshort_seed_number drama_number
CROSS JOIN dev_goodshort_seed_number episode
JOIN provider_drama drama
  ON drama.connection_id = c.id
 AND drama.external_drama_id = CONCAT('990000', LPAD(drama_number.n, 2, '0'))
WHERE p.provider_code = 'GOODSHORT'
  AND c.connection_name = 'GoodShort 本地假数据'
  AND episode.n <= 5 + MOD(drama_number.n - 1, 8)
ON DUPLICATE KEY UPDATE
    external_content_id = VALUES(external_content_id),
    title = VALUES(title),
    is_free = VALUES(is_free),
    duration_seconds = VALUES(duration_seconds),
    remote_updated_at = VALUES(remote_updated_at);
```

- [ ] **Step 4: Insert four successful FULL/INCREMENTAL checkpoints and close the transaction**

Append the checkpoint upsert and cleanup:

```sql
INSERT INTO provider_sync_checkpoint
    (connection_id, sync_type, language, status, page_no, page_size,
     update_time, last_success_at, requested_at, started_at, finished_at,
     total_fetched, total_upserted, inserted_count, updated_count,
     skipped_count, error_count, last_error_code, last_error_message,
     lease_owner, lease_until)
SELECT c.id, checkpoints.sync_type, checkpoints.language, 'SUCCESS', 1, 100,
       checkpoints.update_time,
       '2026-08-21 12:00:00', '2026-08-21 11:59:50',
       '2026-08-21 11:59:51', '2026-08-21 12:00:00',
       12, 12, checkpoints.inserted_count, checkpoints.updated_count,
       checkpoints.skipped_count, 0, NULL, NULL, NULL, NULL
FROM short_drama_provider p
JOIN short_drama_connection c ON c.provider_id = p.id
CROSS JOIN (
    SELECT 'FULL' sync_type, 'ENGLISH' language, NULL update_time,
           12 inserted_count, 0 updated_count, 0 skipped_count
    UNION ALL
    SELECT 'FULL', 'SPANISH', NULL, 12, 0, 0
    UNION ALL
    SELECT 'INCREMENTAL', 'ENGLISH', 1787306400000, 2, 9, 1
    UNION ALL
    SELECT 'INCREMENTAL', 'SPANISH', 1787306400000, 1, 10, 1
) checkpoints
WHERE p.provider_code = 'GOODSHORT'
  AND c.connection_name = 'GoodShort 本地假数据'
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    page_no = VALUES(page_no),
    page_size = VALUES(page_size),
    update_time = VALUES(update_time),
    last_success_at = VALUES(last_success_at),
    requested_at = VALUES(requested_at),
    started_at = VALUES(started_at),
    finished_at = VALUES(finished_at),
    total_fetched = VALUES(total_fetched),
    total_upserted = VALUES(total_upserted),
    inserted_count = VALUES(inserted_count),
    updated_count = VALUES(updated_count),
    skipped_count = VALUES(skipped_count),
    error_count = VALUES(error_count),
    last_error_code = NULL,
    last_error_message = NULL,
    lease_owner = NULL,
    lease_until = NULL;

DROP TABLE dev_goodshort_seed_number;

COMMIT;
```

- [ ] **Step 5: Run the focused test and fix only seed-contract failures**

Run:

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=GoodShortDramaCatalogSeedTest test
```

Expected: `Tests run: 2, Failures: 0, Errors: 0` and Maven `BUILD SUCCESS`.

- [ ] **Step 6: Commit the tested seed and its contract test**

```powershell
git add -- scripts/dev/seed_goodshort_drama_catalog.sql src/test/java/com/kasi/backend/drama/GoodShortDramaCatalogSeedTest.java
git diff --cached --check
git commit -m "feat: add GoodShort local catalog seed"
```

### Task 3: Document the local-only workflow

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-08-21-goodshort-local-catalog-seed-design.md`

- [ ] **Step 1: Update `README.md` repository structure and local development section**

Add `scripts/dev/seed_goodshort_drama_catalog.sql` to the repository tree. After the MySQL/Flyway startup text, add a subsection with these exact contracts:

```markdown
### GoodShort 本地目录假数据

在尚未取得 GoodShort PID、KEY 时，可手动执行
`scripts/dev/seed_goodshort_drama_catalog.sql`，为管理员目录 API 和管理端页面生成本地联调数据。

- 仅限当前本地开发 MySQL，禁止在生产、预发布或共享测试环境执行。
- 脚本不属于 Flyway，不会随应用启动自动执行。
- 内部主键由数据库自增生成；外部短剧 ID 为 `99000001` 至 `99000024`。
- 脚本创建的 GoodShort 连接处于禁用、`MANUAL`、无 PID/KEY/URL 状态，不会请求第三方接口。
- 脚本可重复执行；预期生成 24 部短剧、204 集内容和 4 条同步检查点。
- 如果已经存在真实 GoodShort 连接或凭据，脚本会失败且不会覆盖原配置。

使用 MySQL 客户端时，应先连接明确的本地开发库，再执行：

```sql
source scripts/dev/seed_goodshort_drama_catalog.sql;
```
```

- [ ] **Step 2: Update `AGENTS.md` current-state and focused-validation facts**

Add one current-state bullet stating that the manual development seed is outside Flyway, uses a disabled credential-free connection, and must never be run outside local development. Extend the drama focused test command to include `GoodShortDramaCatalogSeedTest`:

```text
.\mvnw.cmd -Dtest=GoodShortDramaCatalogSeedTest,GoodShortCatalogAdapterTest,DramaCatalogPersistenceTest,DramaCatalogSyncServiceTest,DramaCatalogAdminServiceTest,AdminDramaCatalogControllerTest,DramaCatalogSchedulerTest test
```

- [ ] **Step 3: Mark the design as implemented but not yet verified**

Change the design status to:

```markdown
状态：已实施，待本地 MySQL 与完整测试验证
```

- [ ] **Step 4: Check and commit the documentation changes**

```powershell
git diff --check
git diff -- README.md AGENTS.md docs/superpowers/specs/2026-08-21-goodshort-local-catalog-seed-design.md
git add -- README.md AGENTS.md docs/superpowers/specs/2026-08-21-goodshort-local-catalog-seed-design.md
git commit -m "docs: document GoodShort local catalog seed"
```

Expected: only the three documented files are included in this commit.

### Task 4: Execute the exact seed against the current local MySQL

**Files:**
- Read: `src/main/resources/application-local.properties`
- Execute: `scripts/dev/seed_goodshort_drama_catalog.sql`
- No repository file changes.

- [ ] **Step 1: Confirm the local database target without printing the password**

Read `SPRING_DATASOURCE_*` from the environment when present; otherwise read the three `spring.datasource.*` values from `application-local.properties`. Confirm only the JDBC host/database and username in the work log. Do not print or copy the password.

- [ ] **Step 2: Build the runtime classpath for a disposable JShell runner**

The MySQL client is not currently on `PATH`, so use the project's existing MySQL driver and Spring JDBC without adding repository runtime code:

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -DskipTests compile dependency:build-classpath '-Dmdep.outputFile=target/seed-classpath.txt'
$dependencyClasspath = Get-Content -Raw 'target/seed-classpath.txt'
$seedClasspath = "target/classes;$dependencyClasspath"
```

Expected: Maven `BUILD SUCCESS`; `target/seed-classpath.txt` remains generated build output and is not staged.

- [ ] **Step 3: Load credentials into process environment without displaying them**

```powershell
$localLines = Get-Content -Encoding UTF8 'src/main/resources/application-local.properties'
function Get-LocalProperty([string]$name) {
    $prefix = "$name="
    $line = $localLines | Where-Object { $_.StartsWith($prefix) } | Select-Object -First 1
    if ($null -eq $line) { throw "Missing local property: $name" }
    $line.Substring($prefix.Length)
}
$env:SEED_DB_URL = if ($env:SPRING_DATASOURCE_URL) { $env:SPRING_DATASOURCE_URL } else { Get-LocalProperty 'spring.datasource.url' }
$env:SEED_DB_USERNAME = if ($env:SPRING_DATASOURCE_USERNAME) { $env:SPRING_DATASOURCE_USERNAME } else { Get-LocalProperty 'spring.datasource.username' }
$env:SEED_DB_PASSWORD = if ($env:SPRING_DATASOURCE_PASSWORD) { $env:SPRING_DATASOURCE_PASSWORD } else { Get-LocalProperty 'spring.datasource.password' }
```

- [ ] **Step 4: Execute the repository SQL file and print only non-secret verification results**

```powershell
@'
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
{
    var dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
    dataSource.setUrl(System.getenv("SEED_DB_URL"));
    dataSource.setUsername(System.getenv("SEED_DB_USERNAME"));
    dataSource.setPassword(System.getenv("SEED_DB_PASSWORD"));
    try (var connection = dataSource.getConnection()) {
        ScriptUtils.executeSqlScript(connection,
            new FileSystemResource("scripts/dev/seed_goodshort_drama_catalog.sql"));
    }
    var jdbc = new JdbcTemplate(dataSource);
    System.out.println(jdbc.queryForMap(
        "SELECT connection_name, status, filing_mode, " +
        "partner_id IS NULL AS pid_empty, api_key_ciphertext IS NULL AS key_empty, " +
        "base_url IS NULL AS url_empty FROM short_drama_connection c " +
        "JOIN short_drama_provider p ON p.id=c.provider_id " +
        "WHERE p.provider_code='GOODSHORT'"));
    System.out.println(jdbc.queryForMap(
        "SELECT COUNT(*) AS dramas, " +
        "(SELECT COUNT(*) FROM provider_drama_content) AS episodes, " +
        "(SELECT COUNT(*) FROM provider_sync_checkpoint) AS checkpoints " +
        "FROM provider_drama"));
}
/exit
'@ | jshell --class-path $seedClasspath
```

Expected non-secret results:

```text
connection_name=GoodShort 本地假数据, status=0, filing_mode=MANUAL,
pid_empty=1, key_empty=1, url_empty=1
dramas=24, episodes=204, checkpoints=4
```

If the script rejects an existing real GoodShort connection, stop and report the guard result. Do not disable, delete, or rewrite that connection to force the seed through.

- [ ] **Step 5: Clear credential environment variables immediately**

```powershell
Remove-Item Env:SEED_DB_URL -ErrorAction SilentlyContinue
Remove-Item Env:SEED_DB_USERNAME -ErrorAction SilentlyContinue
Remove-Item Env:SEED_DB_PASSWORD -ErrorAction SilentlyContinue
```

### Task 5: Run complete verification and record the verified state

**Files:**
- Modify: `docs/superpowers/specs/2026-08-21-goodshort-local-catalog-seed-design.md`

- [ ] **Step 1: Run the focused drama suite with the new seed test**

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=GoodShortDramaCatalogSeedTest,GoodShortCatalogAdapterTest,DramaCatalogPersistenceTest,DramaCatalogSyncServiceTest,DramaCatalogAdminServiceTest,AdminDramaCatalogControllerTest,DramaCatalogSchedulerTest test
```

Expected: Maven `BUILD SUCCESS`, with zero failures and zero errors.

- [ ] **Step 2: Run the full test suite**

```powershell
.\mvnw.cmd test
```

Expected: Maven `BUILD SUCCESS`, with zero failures and zero errors.

- [ ] **Step 3: Run the Java 25 compile check**

```powershell
.\mvnw.cmd -DskipTests compile
```

Expected: Maven `BUILD SUCCESS` under Java 25.0.3.

- [ ] **Step 4: Run repository hygiene checks**

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; only the planned design-status update is uncommitted. Generated files remain under ignored `target/`.

- [ ] **Step 5: Record successful verification in the design document**

Only after Task 4 and Steps 1-4 all succeed, change the design header to:

```markdown
状态：已实施并验证（2026-08-21）

实施结果：已交付仅限本地开发的 GoodShort 目录假数据脚本；已验证可重复生成 24 部短剧、204 集内容和 4 条同步检查点，并在存在真实连接时拒绝写入。脚本未进入 Flyway 或应用启动流程。
```

- [ ] **Step 6: Commit the verified status and confirm the final worktree**

```powershell
git add -- docs/superpowers/specs/2026-08-21-goodshort-local-catalog-seed-design.md
git diff --cached --check
git commit -m "docs: record GoodShort seed verification"
git status --short --branch
git log -4 --oneline
```

Expected: clean worktree; the latest commits are the plan, seed implementation, documentation, and verification record. Do not push unless the user separately requests it.
