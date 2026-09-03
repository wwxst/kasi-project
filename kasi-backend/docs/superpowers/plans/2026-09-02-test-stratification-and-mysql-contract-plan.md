# Backend Test Stratification and MySQL Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with review checkpoints.

**Goal:** Add explicit unit, integration, MySQL-contract, and real-smoke execution paths, real MySQL high-risk contracts, JaCoCo reporting, SpotBugs blocking analysis, dependency review, and independently visible CI jobs without changing production behavior.

**Architecture:** Preserve the single Spring Boot module and default `mvn verify` suite. Use JUnit 5 tags for classified tests, Maven profiles for category execution and JaCoCo data files, and separate GitHub Actions jobs for H2 integration, MySQL 8.4 contracts, static analysis, and dependency review.

**Tech Stack:** Java 25, Maven Surefire 3.5.6, JUnit 5, Spring Boot Test, MyBatis, H2, MySQL 8.4, JaCoCo 0.8.15, SpotBugs Maven Plugin 4.10.4.0, GitHub Actions.

---

### Task 1: Add category markers and Maven profiles

**Files:**
- Modify: `pom.xml`
- Modify: Spring/database tests under `src/test/java/com/kasi/backend` that extend `BaseAuthTest` or use `DatabaseInitializationTestSupport`
- Modify: `src/test/java/com/kasi/backend/provider/goodshort/GoodShortFreeContentIntegrationTest.java`
- Modify: `src/test/java/com/kasi/backend/MySqlContractIT.java`
- Create: `src/test/java/com/kasi/backend/architecture/TestCategoryStructureTest.java`

- [ ] **Step 1: Write the failing structure test.** Assert that legacy Spring/database tests have `integration`, while `MySqlContractIT` has `mysql-contract` and `GoodShortFreeContentIntegrationTest` has `real-smoke`; contract and smoke classes are explicit exceptions to the generic integration classification.
- [ ] **Step 2: Run the focused test and observe the expected failure.** Run `.\mvnw.cmd -Dtest=TestCategoryStructureTest test`; it must fail because the markers are not yet complete.
- [ ] **Step 3: Add JUnit tags.** Add `@Tag("integration")` to the existing Spring/H2/embedded-Redis and migration tests; add `@Tag("mysql-contract")` to `MySqlContractIT`; add `@Tag("real-smoke")` to the GoodShort test.
- [ ] **Step 4: Configure profiles.** In `pom.xml`, add `test.category`, JaCoCo `0.8.15`, and profiles `unit-tests`, `integration-tests`, `mysql-contract-tests`, and `real-smoke-tests`. Unit excludes `integration,mysql-contract,real-smoke`; integration includes `integration` and excludes the other two; contract includes `mysql-contract`; smoke includes `real-smoke` and sets `goodshort.integration=true`. Use `${project.build.directory}/jacoco-${test.category}.exec` and keep no profile active by default.
- [ ] **Step 5: Run the profile commands.** Run `.\mvnw.cmd -Punit-tests test`, `.\mvnw.cmd -Pintegration-tests test`, and `.\mvnw.cmd -Pmysql-contract-tests -Dtest=MySqlContractIT test`. The first two must execute their tagged sets; the last may explicitly SKIP without `MYSQL_CONTRACT_URL`.
- [ ] **Step 6: Commit.** Stage only `pom.xml` and the changed test files, run `git diff --cached --check`, and commit `test: add explicit backend test categories`.

### Task 2: Add real MySQL order idempotency and locking contracts

**Files:**
- Create: `src/test/java/com/kasi/backend/support/MySqlContractTestSupport.java`
- Create: `src/test/java/com/kasi/backend/promotion/PromotionOrderMySqlContractIT.java`
- Create: `src/test/java/com/kasi/backend/promotion/LockingMySqlContractIT.java`
- Modify: `src/test/java/com/kasi/backend/MySqlContractIT.java`

- [ ] **Step 1: Write failing contracts.** Use `@SpringBootTest`, `@Tag("mysql-contract")`, `@EnabledIfEnvironmentVariable`, `@DynamicPropertySource`, real `TransactionTemplate`, production MyBatis mappers, and a Mockito verification sender. Run two transactions upserting one `(connection_id, external_order_id)` and assert one insert, one existing result, and one final row. Hold a production `FOR UPDATE` lock in one transaction, prove the competing update is blocked with latches and a bounded timeout, release it, and assert the second transaction commits.
- [ ] **Step 2: Run the focused contracts.** Run `.\mvnw.cmd -Pmysql-contract-tests -Dtest=PromotionOrderMySqlContractIT,LockingMySqlContractIT test`; without MySQL it must report explicit SKIP.
- [ ] **Step 3: Implement test-only setup.** Centralize environment registration, fixed `mysql-contract-` cleanup, and insertion of provider, connection, drama, link, and commission-history fixtures. Do not add production helpers or alter SQL.
- [ ] **Step 4: Run against MySQL 8.4.** Execute the same command with the contract variables configured and require all assertions to pass.
- [ ] **Step 5: Commit.** Stage the support and two contract classes, run `git diff --cached --check`, and commit `test: cover MySQL order idempotency and row locking`.

### Task 3: Add MySQL transaction propagation and lease exclusivity

**Files:**
- Create: `src/test/java/com/kasi/backend/integration/TransactionMySqlContractIT.java`
- Modify: `src/test/java/com/kasi/backend/support/MySqlContractTestSupport.java`

- [ ] **Step 1: Write the failing contract.** Verify an outer rollback removes an ordinary update while a production Spring proxy `REQUIRES_NEW` status write remains committed. Compete two real `claimLease` calls for one due task and assert exactly one returns `1`, the other `0`, and the winner is persisted.
- [ ] **Step 2: Run it in the contract profile.** Run `.\mvnw.cmd -Pmysql-contract-tests -Dtest=TransactionMySqlContractIT test`; require SKIP only when MySQL is absent.
- [ ] **Step 3: Implement setup and cleanup in the test support only.** Reuse existing service interfaces and mapper methods; add no production transaction changes.
- [ ] **Step 4: Run all contracts.** Run `.\mvnw.cmd -Pmysql-contract-tests -Dtest='*MySqlContractIT' test` against MySQL 8.4.
- [ ] **Step 5: Commit.** Stage the transaction contract and support changes, run `git diff --cached --check`, and commit `test: verify MySQL transaction and lease contracts`.

### Task 4: Add JaCoCo, SpotBugs, and CI quality jobs

**Files:**
- Modify: `pom.xml`
- Modify: `E:/JavaProjects/kasi-project/.github/workflows/ci.yml`

- [ ] **Step 1: Add the static-analysis profile.** Bind SpotBugs `4.10.4.0` to `verify` with `effort=Max`, `threshold=High`, and `failOnError=true`; keep it outside default `mvn verify` until reviewed.
- [ ] **Step 2: Run static analysis.** Run `.\mvnw.cmd -Pstatic-analysis -DskipTests verify`; fix only concrete high-confidence findings and do not suppress globally.
- [ ] **Step 3: Split backend CI.** Add independent `Backend Unit`, `Backend Integration`, `MySQL Contract`, and `Backend Static Analysis` jobs. Unit/integration upload JaCoCo HTML/XML artifacts. MySQL uses the existing MySQL 8.4 service and runs `-Pmysql-contract-tests -Dtest='*MySqlContractIT' test` with contract variables.
- [ ] **Step 4: Add dependency review.** Add `actions/dependency-review-action@v4` with `fail-on-severity: high`; do not add an NVD key or an unbounded vulnerability database download.
- [ ] **Step 5: Validate and commit.** Run static analysis and `git diff --check`, inspect that existing admin/user jobs remain intact, then commit `ci: add backend test and quality gates` with only `pom.xml` and the workflow staged.

### Task 5: Add manual real-smoke workflow and synchronize docs

**Files:**
- Create: `E:/JavaProjects/kasi-project/.github/workflows/real-smoke.yml`
- Modify: `scripts/dev/smoke-goodshort-free-content.ps1`
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `E:/JavaProjects/kasi-project/DEVELOPMENT.md`
- Modify: `E:/JavaProjects/kasi-project/docs/development/testing.md`

- [ ] **Step 1: Add manual workflow.** Use `workflow_dispatch`, `pwsh`, protected GoodShort secrets, and `-Required`; missing secrets, request errors, and assertion errors must be non-zero.
- [ ] **Step 2: Document exact commands and semantics.** Document `-Punit-tests`, `-Pintegration-tests`, `-Pmysql-contract-tests`, and `-Preal-smoke-tests`, JaCoCo as informational, SpotBugs/dependency review as blocking, and real-smoke as manual/secret-protected. Separate current behavior from future coverage thresholds.
- [ ] **Step 3: Verify docs and commit.** Run `git diff --check` and targeted `rg` checks, then commit `docs: document backend test gates and real smoke` with only the smoke, workflow, and documentation files staged.

### Task 6: Run the complete verification matrix

**Files:**
- Verify only; no source edits expected.

- [ ] **Step 1: Run default Gate.** With Java 25, run `.\mvnw.cmd verify`; require zero failures and zero errors, with only explicitly allowed external skips.
- [ ] **Step 2: Run category and static checks.** Run the unit, integration, and static-analysis profile commands and inspect JaCoCo reports.
- [ ] **Step 3: Run MySQL contracts where available.** Set `MYSQL_CONTRACT_URL`, `MYSQL_CONTRACT_USERNAME`, and `MYSQL_CONTRACT_PASSWORD`, then run `.\mvnw.cmd -Pmysql-contract-tests -Dtest='*MySqlContractIT' test`; record PASS or environment-based SKIP.
- [ ] **Step 4: Inspect final scope.** Run `git diff --check`, `git status --short --branch`, and `git diff --stat HEAD~5..HEAD`; confirm unrelated user changes remain untouched.
