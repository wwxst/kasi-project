# Backend Test Stratification and MySQL Contract Design

Date: 2026-09-02
Status: Approved for implementation

## Goal

补强 Kasi 后端测试的真实数据库验证和执行可见性，并借鉴 sub2api 的分层测试入口，同时保持现有单模块 Spring Boot 架构、API、业务代码和数据库契约不变。

## Scope

### Current implementation to preserve

- `mvn verify` remains the default local backend Gate.
- Existing H2 MySQL-mode tests, embedded Redis tests, MockMvc tests, mapper tests, and service tests remain valid.
- `MySqlContractIT` remains the dedicated production-schema contract entry.
- GoodShort real requests remain opt-in and do not run in ordinary pull requests.

### Approved changes

- Add explicit test categories: `unit`, `integration`, `mysql-contract`, and `real-smoke`.
- Add focused MySQL 8.4 contracts for row locking, concurrent order idempotency, and transaction boundaries.
- Add JaCoCo reporting without a coverage threshold.
- Add SpotBugs with high-confidence findings blocking CI.
- Add dependency review/security visibility with high and critical dependency findings blocking CI.
- Split GitHub Actions backend checks into independently visible jobs.
- Synchronize backend and repository testing documentation.

### Non-goals

- No production behavior, API, schema, or migration changes.
- No mass test-file rename or package reorganization.
- No new test-only compatibility layer or future abstraction.
- No automatic GoodShort credential use in ordinary pull-request jobs.
- No coverage threshold in this phase.

## Test categories

### `unit`

纯 Java 逻辑、计算器、校验器、适配器签名和使用 Mockito 的 Service 单元测试。该类别不要求 Spring、数据库或外部服务。

### `integration`

使用 Spring application context、H2 MySQL mode、embedded Redis、MockMvc、真实 MyBatis mapper 或真实事务代理的测试。现有 `BaseAuthTest` 及相关测试继续属于这一层。

### `mysql-contract`

使用 MySQL 8.4 服务和生产 `src/main/resources/db/kasi_promotion.sql` 的契约测试。测试只验证 H2 可能失真的数据库行为：`FOR UPDATE` 锁、唯一键并发冲突、事务隔离、租约领取、金额精度和业务时区。

### `real-smoke`

使用受保护环境变量访问 GoodShort 的真实请求测试。缺少配置时明确 `SKIP`；使用 Required 模式时缺少配置、请求失败或断言失败均为 `FAIL`。该层不进入普通 PR Gate。

## MySQL contract design

Keep `MySqlContractIT` for schema metadata, decimal round-trip, timezone, and scheduled-task lease assertions. Add focused classes:

- `PromotionOrderMySqlContractIT`: two real Spring transactions upsert the same `(connection_id, external_order_id)` and assert one insert, one duplicate-read result, and one final row.
- `LockingMySqlContractIT`: one transaction holds a production mapper `FOR UPDATE` lock while a second transaction waits; latches and bounded timeouts prove lock ownership without sleep-based ordering.
- `TransactionMySqlContractIT`: an outer rollback is combined with a production Spring proxy `REQUIRES_NEW` write; the outer write rolls back while the independent status write remains committed. The same class verifies one successful scheduled-task lease claim among competing workers.

Each contract creates records with a fixed test prefix and cleans only those records. No production schema copy is maintained.

## Build and quality tooling

Maven profiles expose the categories without removing the default command:

```text
-Punit-tests
-Pintegration-tests
-Pmysql-contract-tests
-Preal-smoke-tests
```

JaCoCo `0.8.15` produces HTML/XML reports for unit and integration executions. Reports are uploaded as CI artifacts; no minimum percentage is enforced.

SpotBugs Maven Plugin `4.10.4.0` runs with maximum effort and a high threshold. High-confidence findings fail the static-analysis job.

Dependency review blocks newly introduced high or critical vulnerabilities in pull requests. The first phase does not require an NVD API key or an unreliable full-database vulnerability download.

## CI jobs

The repository workflow exposes these independent checks:

```text
Backend Unit
Backend Integration
MySQL Contract
Backend Static Analysis
Dependency Review
Admin
User
```

`Real Smoke` is a separate manual workflow using protected GoodShort secrets. It is never silently converted into a passing result when configuration or assertions are missing.

## Acceptance criteria

- Existing `mvn verify` remains green with the current H2/embedded-Redis suite.
- MySQL Contract runs against MySQL 8.4 and executes the production initialization SQL.
- The three focused contracts prove locking, order idempotency, transaction propagation, and lease exclusivity using real database behavior.
- Unit, integration, MySQL Contract, and real-smoke commands are documented with explicit PASS/FAIL/SKIP semantics.
- JaCoCo artifacts are generated without a threshold failure.
- SpotBugs and dependency review fail on their configured high-confidence/high-severity findings.
- `git diff --check` is clean for the implementation changes.

## Risks and mitigations

- MySQL contract jobs can be slower or unavailable locally: keep them separate from H2 tests and report environment absence as `SKIP` only where the contract explicitly allows it.
- Existing tests are not uniformly tagged: introduce category entry points incrementally and avoid mass renaming; the default Gate remains the compatibility safety net.
- Static analyzers can report noisy findings: use high-confidence/high-severity blocking thresholds and keep report generation visible for later tightening.
