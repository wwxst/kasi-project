# Media Account Filing Database Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the first durable database schema for platform connections, promotion media accounts, and per-platform filing records.

**Architecture:** Add one Flyway `V2` migration. It includes the minimal platform and connection tables required by the filing foreign key, then adds the reusable media-account table and one-to-many filing table. Keep all HTTP clients, services, and controllers out of this change.

**Tech Stack:** Flyway, MySQL 8, H2 MySQL compatibility mode, Spring JDBC, JUnit 5, AssertJ.

---

### Task 1: Lock the schema contract with a failing migration test

**Files:**
- Create: `src/test/java/com/kasi/backend/MediaAccountFilingMigrationTest.java`

- [ ] **Step 1: Write the failing migration test**

Create an isolated H2/MySQL-mode database, run every migration from `classpath:db/migration`, and assert the four required tables, seeded GoodShort provider, defaults, unique constraints, and delete protection:

```java
@Test
@DisplayName("V2创建平台接入、媒体账号和报备表并保护用户归属")
void migrateV2CreatesMediaAccountFilingSchema() {
    JdbcTemplate jdbc = migrateAllMigrations();

    assertThat(tableExists(jdbc, "SHORT_DRAMA_PROVIDER")).isTrue();
    assertThat(tableExists(jdbc, "SHORT_DRAMA_CONNECTION")).isTrue();
    assertThat(tableExists(jdbc, "PROMOTION_MEDIA_ACCOUNT")).isTrue();
    assertThat(tableExists(jdbc, "PROVIDER_MEDIA_FILING")).isTrue();
    assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM short_drama_provider WHERE provider_code = 'GOODSHORT'", Long.class))
            .isEqualTo(1L);

    Long userId = jdbc.queryForObject(
            "INSERT INTO promotion_user (user_no, password) VALUES ('100000000001', 'hash') "
                    + "RETURNING id", Long.class);
    jdbc.update("INSERT INTO promotion_media_account "
            + "(user_id, media_type, external_account_id) VALUES (?, 'TIKTOK', 'creator-1')", userId);
    Long mediaId = jdbc.queryForObject("SELECT id FROM promotion_media_account", Long.class);
    Long connectionId = jdbc.queryForObject("SELECT id FROM short_drama_connection", Long.class);
    jdbc.update("INSERT INTO provider_media_filing "
            + "(connection_id, media_account_id) VALUES (?, ?)", connectionId, mediaId);

    assertThat(jdbc.queryForObject(
            "SELECT status FROM promotion_media_account WHERE id = ?", Integer.class, mediaId)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
            "SELECT status FROM provider_media_filing WHERE media_account_id = ?", String.class, mediaId))
            .isEqualTo("PENDING");

    assertThatThrownBy(() -> jdbc.update("INSERT INTO promotion_media_account "
            + "(user_id, media_type, external_account_id) VALUES (?, 'TIKTOK', 'creator-1')", userId))
            .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> jdbc.update(
            "DELETE FROM promotion_user WHERE id = ?", userId))
            .isInstanceOf(DataAccessException.class);
}
```

Use a generated H2 URL per test and small helpers for `migrateAllMigrations()` and `tableExists()`. Do not use the application test schema for this migration test.

- [ ] **Step 2: Run the migration test and verify RED**

Run:

```powershell
.\mvnw.cmd --% -Dtest=MediaAccountFilingMigrationTest test
```

Expected: FAIL because `V2__media_account_filing.sql` and the four tables do not exist.

### Task 2: Add the production V2 migration

**Files:**
- Create: `src/main/resources/db/migration/V2__media_account_filing.sql`

- [ ] **Step 1: Add the minimal platform dependency tables**

Create `short_drama_provider` with a unique `provider_code`, `provider_name`, enabled `status`, and timestamps. Create `short_drama_connection` with `provider_id`, connection display fields, encrypted-key ciphertext, currency, status, audit fields, and a unique provider constraint for the first release. Add a foreign key from connection to provider and seed exactly one enabled `GOODSHORT` provider. Do not seed a connection secret.

- [ ] **Step 2: Add the media account table**

Create `promotion_media_account` with:

```sql
id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
user_id BIGINT UNSIGNED NOT NULL,
media_type VARCHAR(32) NOT NULL,
external_account_id VARCHAR(128) NOT NULL,
account_name VARCHAR(128) NULL,
account_link VARCHAR(512) NULL,
status TINYINT NOT NULL DEFAULT 1,
data_version INT NOT NULL DEFAULT 1,
created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
```

Add a unique key on `(media_type, external_account_id)`, an index on `(user_id, status)`, and a foreign key to `promotion_user(id)` with `ON DELETE RESTRICT`. Do not add `deleted_at` or a delete marker.

- [ ] **Step 3: Add the platform filing table**

Create `provider_media_filing` with `connection_id`, `media_account_id`, `status VARCHAR(16) DEFAULT 'PENDING'`, submitted data version, third-party status/ID/timestamps, retry scheduling fields, sanitized error fields, lease fields, and timestamps. Add a unique key on `(connection_id, media_account_id)`, indexes for due task scans, and `ON DELETE RESTRICT` foreign keys to both connection and media account.

The migration must not create drama, commission, link, order, export, or analytics tables.

- [ ] **Step 4: Run the migration test and verify GREEN**

Run the same focused command and require zero failures and zero errors.

### Task 3: Mirror the schema in the H2 application test schema

**Files:**
- Modify: `src/test/resources/test-schema.sql`

- [ ] **Step 1: Add the four tables in dependency order**

Add `short_drama_provider`, `short_drama_connection`, `promotion_media_account`, and `provider_media_filing` using H2-compatible types. Seed `GOODSHORT` once. Keep child tables before any test cleanup that deletes parent rows, and keep the same unique keys, defaults, and foreign keys as V2.

- [ ] **Step 2: Run the focused migration and application context tests**

Run:

```powershell
.\mvnw.cmd --% -Dtest=MediaAccountFilingMigrationTest,KasiBackendApplicationTests test
```

Expected: all tests pass with zero failures and zero errors.

### Task 4: Update current-state documentation and verify the repository

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Document only the implemented schema**

Update the current structure and database sections to list V2 and the four tables. State that the tables are present but media-account APIs, GoodShort filing jobs, service-level deletion errors, and all later promotion modules are not implemented yet.

- [ ] **Step 2: Run the complete verification**

Use Java 25 and run:

```powershell
.\mvnw.cmd --% test
.\mvnw.cmd --% -DskipTests compile
git diff --check
```

Expected: build success, zero test failures/errors, and no whitespace errors.

- [ ] **Step 3: Commit only this database increment**

```powershell
git add src/main/resources/db/migration/V2__media_account_filing.sql src/test/resources/test-schema.sql src/test/java/com/kasi/backend/MediaAccountFilingMigrationTest.java README.md docs/superpowers/plans/2026-08-17-media-account-filing-database.md
git commit -m "feat: add media account filing schema"
```
