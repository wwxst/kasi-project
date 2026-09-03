# Single Database Initialization Script Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove Flyway and replace all versioned migrations with one complete SQL file for initializing an empty database.

**Architecture:** The application no longer owns schema migration at startup. `src/main/resources/db/kasi_promotion.sql` is the single production schema source, while tests execute it directly against isolated H2 databases through a shared test helper.

**Tech Stack:** Java 25, Spring Boot 4, MySQL 8, H2 MySQL mode, Maven Wrapper, JUnit 5

---

### Task 1: Establish direct initialization-script verification

**Files:**
- Create: `src/test/java/com/kasi/backend/support/DatabaseInitializationTestSupport.java`
- Modify: database structure tests under `src/test/java/com/kasi/backend`
- Modify: `src/test/java/com/kasi/backend/drama/GoodShortDramaCatalogSeedTest.java`

- [x] **Step 1: Add the shared test initializer**

Create a helper that builds a unique H2 MySQL-mode data source and executes `classpath:db/kasi_promotion.sql` with `ResourceDatabasePopulator` using UTF-8.

- [x] **Step 2: Switch current-state structure tests from Flyway to the helper**

Replace `Flyway.configure()` calls with `DatabaseInitializationTestSupport.initializeDatabase(...)`. Rename test descriptions and helper methods so they describe initialization rather than migration versions.

- [x] **Step 3: Remove historical-upgrade-only assertions**

Replace the V18 draft-row conversion test with assertions that a newly inserted online drama defaults to `PUBLISHED` and an explicit `OFFLINE` value is preserved. Remove the V17 missing-column repair scenario and V15 existing-row history backfill scenario; retain final schema, seed, uniqueness and foreign-key assertions.

- [x] **Step 4: Run the focused structure tests**

Run the database initialization and seed test classes. Expected result: tests initially fail until the single SQL file exists and contains the complete final schema.

### Task 2: Build the single final-state SQL

**Files:**
- Move: `src/main/resources/db/migration/V1__kasi_promotion.sql` to `src/main/resources/db/kasi_promotion.sql`
- Delete: remaining files under `src/main/resources/db/migration`
- Delete: `src/main/java/db/migration/V17__goodshort_order_scheduled_sync.java`
- Delete: `src/main/java/db/migration/V18__drama_default_published.java`

- [x] **Step 1: Flatten the existing V1 definitions**

Move `base_url` and `filing_mode` into `short_drama_connection`; move `task_data_version` and `operate_by` into `provider_media_filing` and make `next_action_at` nullable in its original definition; move all cycle fields into `system_scheduled_task`.

- [x] **Step 2: Fold the final drama and promotion-link structures into their original table definitions**

Define `provider_drama.local_status` with default `PUBLISHED` and include `commission_scope`, `promotion_description`, `title_zh`, `label_names`, `category_name`, `remote_rank`, `novel_type`, `novel_sub_type` and `remote_created_at`. Define `promotion_link` directly with `batch_no`, `media_type`, `link_variant`, the four-column idempotency key, and no `media_account_id` or `landing_type`.

- [x] **Step 3: Append current final tables in dependency order**

Create `provider_commission_rule_history`, `promotion_task`, `promotion_order` and `drama_download_task` directly. Do not include historical row-backfill SQL.

- [x] **Step 4: Define both fixed scheduled-task seeds in final form**

Seed `GOODSHORT_DRAMA_INCREMENTAL_SYNC` with a 60-minute cycle and `GOODSHORT_ORDER_SYNC` with a 1-minute cycle while retaining the legacy `interval_minutes` values required by the current table constraint.

- [x] **Step 5: Delete all versioned SQL and Java migration files**

After consolidation, `src/main/resources/db` contains only `kasi_promotion.sql` as the production initialization schema.

### Task 3: Remove Flyway runtime and build dependencies

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/resources/application-test.properties`

- [x] **Step 1: Remove Flyway dependencies**

Delete `spring-boot-starter-flyway`, `flyway-mysql` and `spring-boot-starter-flyway-test` from `pom.xml`.

- [x] **Step 2: Remove Flyway properties**

Delete `spring.flyway.enabled`, `spring.flyway.locations` and the test-only disable flag. Keep all datasource properties unchanged.

- [x] **Step 3: Compile test sources**

Run `./mvnw.cmd -DskipTests test-compile` with JDK 25. Expected result: no Flyway imports or classes remain and compilation exits with code 0.

### Task 4: Synchronize current documentation

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `DEVELOPMENT.md`
- Modify: `docs/architecture-decisions.md`

- [x] **Step 1: Document the new initialization workflow**

State that the database must be empty and initialized manually with `src/main/resources/db/kasi_promotion.sql`, and that application startup does not create or upgrade schemas.

- [x] **Step 2: Remove current-state Flyway instructions**

Replace active migration-version lists, Flyway validation commands and compatibility guidance. Historical plan/spec files remain historical records and are not rewritten.

- [x] **Step 3: Record the architecture decision**

Add a current decision noting the disposable development database, lack of history migration, manual rebuild requirement and rollback constraint.

### Task 5: Verify the consolidated result

**Files:**
- Verify all files changed by Tasks 1-4

- [x] **Step 1: Verify no active Flyway implementation remains**

Run `rg -n "org\\.flywaydb|spring\\.flyway|classpath:db/migration|src/main/resources/db/migration" pom.xml src/main src/test README.md AGENTS.md DEVELOPMENT.md docs/architecture-decisions.md`. Expected result: no matches.

- [x] **Step 2: Run focused initialization tests**

Run the adapted initialization schema, scheduled-task, order, link, drama and seed tests with JDK 25. Expected result: zero failures and zero errors.

- [x] **Step 3: Run the full Maven test suite**

Run `./mvnw.cmd test` with JDK 25. Expected result: build success with zero failures and zero errors.

- [x] **Step 4: Inspect final scope and whitespace**

Run `git diff --check`, `git status --short --branch`, and a scoped diff of database, build, test and documentation files. Confirm unrelated dirty files were not modified.
