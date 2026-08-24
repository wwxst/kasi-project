# Historical Compatibility Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove confirmed unused compatibility APIs, Mapper SQL, error codes, and Flyway baseline behavior without changing current authentication contracts.

**Architecture:** Keep all existing layers and runtime flows intact. Add one source-structure regression test, remove only symbols with zero callers, and update current-state documentation.

**Tech Stack:** Java 25, Spring Boot 4, MyBatis, Flyway, JUnit 5, AssertJ, Maven Wrapper

---

### Task 1: Add a failing compatibility-structure test

**Files:**
- Create: `src/test/java/com/kasi/backend/architecture/HistoricalCompatibilityStructureTest.java`

- [x] Add four tests that assert: `TokenService` exposes only the session-aware generator and parser; `PromotionUserMapper` has no `findByUserNo`; `ErrorCode` excludes the eight unused constants; and `application.properties` does not enable `baseline-on-migrate`.
- [x] Run `./mvnw.cmd -Dtest=HistoricalCompatibilityStructureTest test` under Java 25.
- [x] Confirm the test fails because the historical members and configuration still exist.

### Task 2: Remove the compatibility residuals

**Files:**
- Modify: `src/main/java/com/kasi/backend/security/service/TokenService.java`
- Modify: `src/main/java/com/kasi/backend/security/service/impl/TokenServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/user/mapper/PromotionUserMapper.java`
- Modify: `src/main/resources/mapper/PromotionUserMapper.xml`
- Modify: `src/main/java/com/kasi/backend/common/exception/ErrorCode.java`
- Modify: `src/main/resources/application.properties`

- [x] Remove the deprecated three-argument `generateToken`, unused `validateToken`, and the now-unused `UUID` import.
- [x] Remove the Java and XML `findByUserNo` Mapper declarations.
- [x] Remove exactly the eight unused error constants while retaining all emitted codes and their numbers.
- [x] Remove `spring.flyway.baseline-on-migrate=true` so Flyway uses its safe default.
- [x] Re-run `./mvnw.cmd -Dtest=HistoricalCompatibilityStructureTest test` and confirm it passes.

### Task 3: Remove the deprecated Jackson test accessor

**Files:**
- Modify: `src/test/java/com/kasi/backend/architecture/HistoricalCompatibilityStructureTest.java`
- Modify: `src/test/java/com/kasi/backend/BaseAuthTest.java`

- [x] Add a source assertion that `BaseAuthTest` does not call Jackson 3's deprecated `JsonNode.asText()`.
- [x] Run `./mvnw.cmd -Dtest=HistoricalCompatibilityStructureTest test` and confirm the new assertion fails.
- [x] Replace the two `asText()` calls with `stringValue()` and confirm the targeted test passes.

### Task 4: Synchronize documentation and verify

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`

- [x] Document that Flyway does not baseline unmanaged non-empty databases and that compatibility-only APIs are not retained without callers.
- [x] Run `./mvnw.cmd test` under Java 25 and confirm zero failures.
- [x] Run `./mvnw.cmd -DskipTests compile` and confirm exit code 0.
- [x] Run Flyway `validate` against the configured local development database when its environment is available.
- [x] Run `git diff --check` and a final `rg` scan for all removed symbols.
