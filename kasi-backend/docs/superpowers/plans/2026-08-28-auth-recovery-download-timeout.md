# Auth Recovery and Download Timeout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore Redis mutation/reset-token state on every transaction rollback and bound HTTP media connection/read waits.

**Architecture:** Keep the existing Redis Lua state machines and transaction synchronization APIs. Register completion callbacks immediately after state acquisition, before database calls that can fail. Replace the media downloader's Java `HttpClient` streaming path with `HttpURLConnection`, which supports connect and per-read timeouts while preserving existing status, retry, size, redirect, and FFmpeg behavior.

**Tech Stack:** Java 25, Spring Boot 4, Spring transactions, Redis, JUnit 5, Mockito, JDK `HttpURLConnection`, Maven Wrapper.

---

### Task 1: Register `MUTATING` recovery before database writes

**Files:**
- Modify: `src/test/java/com/kasi/backend/admin/service/AdminManagementServiceTest.java`
- Modify: `src/test/java/com/kasi/backend/admin/service/AdminAuthServiceTest.java`
- Modify: `src/test/java/com/kasi/backend/user/UserManagementServiceTest.java`
- Modify: `src/main/java/com/kasi/backend/admin/service/impl/AdminManagementServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/admin/service/impl/AdminAuthServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/user/service/impl/UserManagementServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/user/service/impl/UserAuthServiceImpl.java`

- [ ] **Step 1: Change database-failure tests to require early registration**

Use Mockito `inOrder` so each representative path requires:

```java
order.verify(sessionService).beginMutation(SubjectType.ADMIN, 2L);
order.verify(sessionService).registerMutationCompletion(mutation);
order.verify(mapper).updateStatus(2L, 0);
```

For a Mapper exception, verify `registerMutationCompletion` was already called. Add representative assertions for administrator self-service and promotion-user management.

- [ ] **Step 2: Run focused tests and verify RED**

Run `./mvnw.cmd --% -Dtest=AdminManagementServiceTest,AdminAuthServiceTest,UserManagementServiceTest test` under JDK 25.

Expected: failures because current implementations register only after successful Mapper writes.

- [ ] **Step 3: Move registration immediately after every `beginMutation`**

Use this sequence at every transactional call site:

```java
SessionMutation mutation = sessionService.beginMutation(subjectType, subjectId);
sessionService.registerMutationCompletion(mutation);
// Database writes follow only after callback registration.
```

For optional identifier/contact changes, register inside the same conditional immediately after acquisition. Remove old post-write registrations and unused helpers. Do not change Redis Lua scripts or nonce behavior.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2. Expected: all selected tests pass with zero failures and errors.

### Task 2: Restore or consume reset Tokens according to transaction outcome

**Files:**
- Modify: `src/test/java/com/kasi/backend/user/service/UserPasswordResetServiceTest.java`
- Modify: `src/main/java/com/kasi/backend/user/service/impl/UserAuthServiceImpl.java`

- [ ] **Step 1: Define rollback and commit behavior in tests**

Change the database-uncertainty test to require `restoreReady(reservation)` and prohibit `completeToken(reservation)`. Add a successful reset test requiring `completeToken(reservation)` after commit and prohibiting `restoreReady(reservation)`.

- [ ] **Step 2: Run reset tests and verify RED**

Run `./mvnw.cmd -Dtest=UserPasswordResetServiceTest test`.

Expected: database-exception case fails because the Token remains `PROCESSING`.

- [ ] **Step 3: Register reset-token synchronization immediately after reservation**

Immediately after `reserveToken`, register:

```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        passwordResetTokenService.completeToken(reservation);
    }

    @Override
    public void afterCompletion(int status) {
        if (status != TransactionSynchronization.STATUS_COMMITTED) {
            passwordResetTokenService.restoreReady(reservation);
        }
    }
});
```

Remove manual `restoreReady` branches and the prior commit-only synchronization. Register session mutation completion before updating the password.

- [ ] **Step 4: Run reset tests and verify GREEN**

Run the command from Step 2. Expected: all reset transaction tests pass.

### Task 3: Add configurable media connect and read timeouts

**Files:**
- Modify: `src/test/java/com/kasi/backend/drama/download/service/HttpDramaMediaDownloaderTest.java`
- Modify: `src/main/java/com/kasi/backend/drama/download/service/HttpDramaMediaDownloader.java`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Add a delayed-body regression test**

Add a local endpoint that sends headers, pauses longer than the configured read timeout, and then writes its body. Construct the downloader with:

```java
new HttpDramaMediaDownloader(Duration.ofSeconds(1), Duration.ofMillis(100));
```

Assert the delayed download throws `SocketTimeoutException` or an `IOException` caused by read timeout within a bounded JUnit deadline.

- [ ] **Step 2: Run downloader tests and verify RED**

Run `./mvnw.cmd -Dtest=HttpDramaMediaDownloaderTest test`.

Expected: test compilation fails because the duration constructor does not exist.

- [ ] **Step 3: Implement connect/read timeouts using `HttpURLConnection`**

Inject durations with defaults:

```java
public HttpDramaMediaDownloader(
        @Value("${app.drama.download.connect-timeout:10s}") Duration connectTimeout,
        @Value("${app.drama.download.read-timeout:30s}") Duration readTimeout) {
    this.connectTimeoutMillis = Math.toIntExact(connectTimeout.toMillis());
    this.readTimeoutMillis = Math.toIntExact(readTimeout.toMillis());
}
```

Configure `GET`, `setConnectTimeout`, `setReadTimeout`, and `setInstanceFollowRedirects(true)`. Close response/error streams and disconnect in all paths. Preserve 403/404 expiry signaling, other non-2xx handling, the 2 GiB episode cap, partial-file deletion, and the 30-minute FFmpeg deadline.

Add these properties:

```properties
app.drama.download.connect-timeout=${APP_DOWNLOAD_CONNECT_TIMEOUT:10s}
app.drama.download.read-timeout=${APP_DOWNLOAD_READ_TIMEOUT:30s}
```

- [ ] **Step 4: Run downloader tests and verify GREEN**

Run the command from Step 2. Expected: normal stream, expired HLS, and delayed-body tests pass.

### Task 4: Synchronize documentation and verify

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`

- [ ] **Step 1: Document current behavior**

Document that mutation callbacks are registered before MySQL writes, rollback restores `ACTIVE`/`READY`, and downloads default to 10-second connect and 30-second per-read timeouts configured by `APP_DOWNLOAD_CONNECT_TIMEOUT` and `APP_DOWNLOAD_READ_TIMEOUT`.

- [ ] **Step 2: Run all focused tests**

Run `./mvnw.cmd --% -Dtest=SessionServiceTest,AdminManagementServiceTest,AdminAuthServiceTest,UserManagementServiceTest,UserPasswordResetServiceTest,DramaDownloadTaskServiceTest,HttpDramaMediaDownloaderTest test`.

Expected: zero failures and zero errors.

- [ ] **Step 3: Run complete tests**

Run `./mvnw.cmd test`. Expected: `BUILD SUCCESS`, zero failures, and zero errors.

- [ ] **Step 4: Check final scope and whitespace**

Run `git diff --check`, `git status --short --branch`, and a scoped `git diff` over the listed source, test, configuration, documentation, spec, and plan files.

Expected: no whitespace errors and no changes outside the approved scope. Do not stage, commit, or push.
