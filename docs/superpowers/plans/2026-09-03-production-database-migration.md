# Production Database Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce an independently executed Flyway migration path for production databases without enabling application-startup migration.

**Architecture:** Keep Flyway in a Maven-only `migration` profile, freeze the current schema as `V1__baseline.sql`, and retain `kasi_promotion.sql` as the latest development rebuild script. Extend the MySQL 8.4 CI lane to migrate a second schema and compare its final metadata and fixed seed data with the development initialization schema.

**Tech Stack:** Java 25, Spring Boot 4.0.7, Maven Wrapper, Flyway 11.14.1, MySQL Connector/J 9.7.0, MySQL 8.4, JUnit 6, AssertJ, GitHub Actions.

---

### Task 1: Replace the obsolete single-schema-source guard

**Files:**
- Modify: `kasi-backend/src/test/java/com/kasi/backend/architecture/DatabaseSchemaSourceTest.java`

- [x] **Step 1: Change the test to require the approved migration contract**

Require both SQL entry points, `V1__baseline.sql`, the Maven `migration` profile, the `flyway-maven-plugin`, safe Flyway flags, and `spring.flyway.enabled=false`. Also assert that Flyway is absent from normal application dependencies.

- [x] **Step 2: Run the focused test and verify RED**

Run:

```powershell
cd kasi-backend
.\mvnw.cmd -Dtest=DatabaseSchemaSourceTest test
```

Expected: FAIL because `db/migration/V1__baseline.sql`, the migration profile, and the runtime-disable property do not exist yet.

### Task 2: Add the Maven-only Flyway baseline

**Files:**
- Modify: `kasi-backend/pom.xml`
- Modify: `kasi-backend/src/main/resources/application.properties`
- Modify: `kasi-backend/src/main/resources/db/kasi_promotion.sql`
- Create: `kasi-backend/src/main/resources/db/migration/V1__baseline.sql`

- [x] **Step 1: Add fixed tool versions and the migration profile**

Use Spring Boot's managed versions, confirmed as Flyway `11.14.1` and MySQL Connector/J `9.7.0`. Configure only the Maven plugin:

```xml
<profile>
    <id>migration</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.flywaydb</groupId>
                <artifactId>flyway-maven-plugin</artifactId>
                <version>${flyway.version}</version>
                <configuration>
                    <url>${env.FLYWAY_URL}</url>
                    <user>${env.FLYWAY_USER}</user>
                    <password>${env.FLYWAY_PASSWORD}</password>
                    <locations>
                        <location>filesystem:src/main/resources/db/migration</location>
                    </locations>
                    <baselineOnMigrate>false</baselineOnMigrate>
                    <validateOnMigrate>true</validateOnMigrate>
                    <validateMigrationNaming>true</validateMigrationNaming>
                    <outOfOrder>false</outOfOrder>
                    <cleanDisabled>true</cleanDisabled>
                </configuration>
                <dependencies>
                    <dependency>
                        <groupId>org.flywaydb</groupId>
                        <artifactId>flyway-mysql</artifactId>
                        <version>${flyway.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>com.mysql</groupId>
                        <artifactId>mysql-connector-j</artifactId>
                        <version>${mysql.version}</version>
                    </dependency>
                </dependencies>
            </plugin>
        </plugins>
    </build>
</profile>
```

- [x] **Step 2: Disable runtime migration explicitly**

Add this property while keeping Flyway out of normal dependencies:

```properties
spring.flyway.enabled=false
```

- [x] **Step 3: Freeze the current production baseline**

Copy the complete current schema and fixed initial data into `V1__baseline.sql`. Change only the leading comments so `V1` is described as immutable production baseline and `kasi_promotion.sql` as the latest development rebuild script.

- [x] **Step 4: Run the focused test and verify GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=DatabaseSchemaSourceTest test
```

Expected: PASS.

- [x] **Step 5: Verify Flyway is not a runtime application dependency**

Run:

```powershell
.\mvnw.cmd dependency:tree -Dscope=runtime -Dincludes=org.flywaydb:*
```

Expected: no Flyway runtime dependency in the application tree.

### Task 3: Add real MySQL migration parity verification

**Files:**
- Create: `kasi-backend/src/test/java/com/kasi/backend/MigrationSchemaParityMySqlContractIT.java`
- Modify: `.github/workflows/ci.yml`

- [x] **Step 1: Write the MySQL parity test**

Create a `mysql-contract` tagged test enabled only when `MYSQL_MIGRATION_URL` is set. Connect to the Flyway-created schema and compare it with the current MySQL Contract schema, excluding only `flyway_schema_history`. Compare:

```text
TABLES: TABLE_NAME, ENGINE, TABLE_COLLATION
COLUMNS: TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE,
         IS_NULLABLE, COLUMN_DEFAULT, EXTRA
STATISTICS: TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
TABLE_CONSTRAINTS: TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE
CHECK_CONSTRAINTS: CONSTRAINT_NAME, CHECK_CLAUSE
```

Also compare stable fixed data from `short_drama_provider` and `system_scheduled_task`; do not compare timestamps or generated IDs.

- [x] **Step 2: Run the test without MySQL and verify explicit SKIP**

Run:

```powershell
.\mvnw.cmd -Pmysql-contract-tests -Dtest=MigrationSchemaParityMySqlContractIT test
```

Expected: one test skipped because `MYSQL_MIGRATION_URL` is absent.

- [x] **Step 3: Extend the MySQL 8.4 CI job**

Before MySQL Contract tests, create an empty `kasi_migration` schema using the ephemeral CI root account. Run:

```bash
bash ./mvnw -Pmigration flyway:info flyway:migrate flyway:validate
```

with `FLYWAY_URL`, `FLYWAY_USER`, and `FLYWAY_PASSWORD` injected from workflow environment values. Pass `MYSQL_MIGRATION_URL`, `MYSQL_MIGRATION_USERNAME`, and `MYSQL_MIGRATION_PASSWORD` to the contract test step.

Expected: Flyway applies `V1`, validates its checksum, and the parity test proves both initialization paths produce the same current schema and fixed data.

### Task 4: Replace active documentation and ADR contracts

**Files:**
- Create: `docs/adr/ADR-0004-production-database-migrations.md`
- Modify: `docs/adr/architecture-decisions.md`
- Modify: `docs/adr/ADR-0003-database-schema-time-contract.md`
- Modify: `AGENTS.md`
- Modify: `DEVELOPMENT.md`
- Modify: `README.md`
- Modify: `docs/architecture/current.md`
- Modify: `docs/development/governance.md`
- Modify: `docs/development/testing.md`
- Modify: `docs/development/git-and-release.md`
- Modify: `docs/development/gaps.md`
- Modify: `docs/projects/kasi-backend.md`
- Modify: `kasi-backend/AGENTS.md`
- Modify: `kasi-backend/README.md`
- Modify: `kasi-backend/DEVELOPMENT.md`

- [x] **Step 1: Record ADR-0004**

State that production schema history is the immutable Flyway chain, development rebuild uses the latest full SQL, Flyway runs only as a release step, existing databases require explicit baseline version `1`, and ADR-0004 supersedes ADR-0003 only for schema ownership and upgrade mechanics.

- [x] **Step 2: Update active current-state documentation**

Replace claims that Flyway is absent or that `kasi_promotion.sql` is the sole production source. Document the exact environment variables and commands:

```powershell
$env:FLYWAY_URL='jdbc:mysql://host:3306/kasi_promotion?characterEncoding=UTF-8&connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true'
$env:FLYWAY_USER='...'
$env:FLYWAY_PASSWORD='...'
.\mvnw.cmd -Pmigration flyway:info
.\mvnw.cmd -Pmigration flyway:validate
.\mvnw.cmd -Pmigration flyway:migrate
```

For a verified existing database, document the one-time `flyway:baseline -Dflyway.baselineVersion=1` operation. State that `flyway:clean` is disabled and credentials must not be placed in files or command history.

- [x] **Step 3: Update the gap register**

Move the production incremental migration path out of the unresolved P1 table and record the implemented current behavior without claiming that backups or a production environment have already been validated.

### Task 5: Run release verification and preserve the single snapshot commit

**Files:**
- Verify all changed files.

- [x] **Step 1: Run focused migration checks**

```powershell
cd kasi-backend
.\mvnw.cmd -Dtest=DatabaseSchemaSourceTest test
.\mvnw.cmd -Pmysql-contract-tests -Dtest=MigrationSchemaParityMySqlContractIT test
```

Expected: structure test PASS; real MySQL parity test explicitly SKIP when environment is unavailable.

- [x] **Step 2: Run the complete backend Gate**

```powershell
.\mvnw.cmd verify
.\mvnw.cmd -Pstatic-analysis -DskipTests verify
```

Expected: zero failures and zero errors; MySQL-only tests may SKIP locally.

- [x] **Step 3: Validate repository content**

```powershell
cd ..
git diff --check
git status --short --branch
git diff --stat
```

Expected: only migration implementation, tests, CI, ADR, and active documentation changes.

- [x] **Step 4: Amend the single repository snapshot**

Stage only the planned files and amend `Initial project snapshot`, then expire the reflog and prune unreachable objects so the local repository continues to expose only the current snapshot.

- [x] **Step 5: Do not publish without a separate push instruction**

Report the new local hash and remote divergence. Force-push only after explicit user authorization.
