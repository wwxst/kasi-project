# 长期 Agent Coding 工程治理实施计划

- 日期：2026-09-01
- 状态：已实施（真实 MySQL Contract 结果待 CI 专用环境）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用最小、稳定、可重复的 Gate 和 canonical 文档保护当前已确认的 Kasi 工程契约。

**Architecture:** 复用 Maven/JUnit/Spring Test、Vitest、Prettier、pnpm 和 GitHub Actions；常规 Gate 与真实 MySQL/GoodShort 验证分离。生产 schema、业务代码和 API 保持不变。

**Tech Stack:** Java 25、Spring Boot 4、JUnit 6、MyBatis、H2、MySQL 8.4、Node 24、pnpm 11、Vitest、GitHub Actions。

---

### Task 1: Canonical 文档与历史归档

**Files:**
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `DEVELOPMENT.md`
- Modify: `docs/README.md`
- Modify: `docs/architecture/current.md`
- Modify: `docs/development/governance.md`
- Modify: `docs/development/testing.md`
- Modify: `docs/development/gaps.md`
- Modify: `docs/development/git-and-release.md`
- Modify: `docs/projects/kasi-backend.md`
- Modify: `docs/projects/kasi-admin-web.md`
- Modify: `docs/projects/kasi-user-web.md`
- Modify: `docs/adr/architecture-decisions.md`
- Modify: `kasi-backend/docs/architecture-decisions.md`
- Modify: `kasi-backend/docs/development-gaps.md`
- Create: `docs/adr/ADR-0003-database-schema-time-contract.md`
- Move: three completed files from `docs/plans/` to `docs/archive/root/`

- [x] 修正 active docs 中的 Flyway、重复 Gate 和迁移发布描述；归档目录内的历史文本不改写。
- [x] 写入 canonical ownership、当前 schema source、`Asia/Shanghai` 语义和普通 CI/real verification 边界。
- [x] 将 ADR-0002 的业务决策保留为当前，将 Flyway 交付细节标记为由 ADR-0003 替代。
- [x] 运行 `rg` 仅检查 active docs，确认不存在把 Flyway 或已归档计划描述为当前事实的命中。

### Task 2: 自动扫描应用分层 Gate

**Files:**
- Create: `kasi-backend/src/test/java/com/kasi/backend/architecture/ApplicationLayerStructureTest.java`
- Delete: `kasi-backend/src/test/java/com/kasi/backend/ServiceImplementationStructureTest.java`
- Delete: `kasi-backend/src/test/java/com/kasi/backend/DtoVoStructureTest.java`
- Delete: `kasi-backend/src/test/java/com/kasi/backend/architecture/HistoricalCompatibilityStructureTest.java`

- [x] 使用 Spring `ClassPathScanningCandidateComponentProvider` 扫描 `com.kasi.backend`，不手工列举业务类型。
- [x] 断言 `*.service/*Service` 是接口并存在对应 `*.service.impl/*ServiceImpl`，反向断言每个实现都对应接口。
- [x] 断言 dto/vo 包与 DTO/VO 后缀一致。
- [x] 断言 Controller 字段和构造参数不直接依赖 mapper；每个 `@RequestBody` 参数位于 dto 包、以 DTO 结尾并由 `@Valid`/`@Validated` 触发校验。
- [x] 运行 `.\mvnw.cmd -Dtest=ApplicationLayerStructureTest test`；若失败，先判断是否为真实稳定规则缺口，不直接修改生产结构。

### Task 3: 真实 Spring 事务 Guardrail

**Files:**
- Create: `kasi-backend/src/test/java/com/kasi/backend/integration/TransactionBoundaryIntegrationTest.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/drama/service/DramaContentSyncServiceTest.java`

- [x] 在 `BaseAuthTest` 的真实 Spring/H2 上注入 production `PromotionLinkPersistenceService`、`DramaContentSyncService` 和事务管理器，只把 `dramaSyncTaskExecutor` 替换为观察用 mock。
- [x] 通过外层 `TransactionTemplate` 调用 production Spring proxy 的 `markFailed`，回滚外层事务后断言推广链接 FAILED 已独立提交而外层用户更新已回滚。
- [x] 通过外层真实事务调用 `DramaContentSyncService.request`，提交前断言 executor 未唤醒，事务返回后断言已唤醒。
- [x] 运行 `.\mvnw.cmd -Dtest=TransactionBoundaryIntegrationTest test`；通过后删除旧注解反射和手工 callback Gate。

### Task 4: Schema source、时间与 MySQL Contract

**Files:**
- Create: `kasi-backend/src/test/java/com/kasi/backend/architecture/DatabaseSchemaSourceTest.java`
- Create: `kasi-backend/src/test/java/com/kasi/backend/MySqlContractIT.java`
- Modify: `kasi-backend/src/main/resources/application.properties`
- Modify: `kasi-backend/src/test/resources/application-test.properties`

- [x] 用 JDK XML/Properties API 检查唯一 SQL 文件、无 `db/migration`、无 Flyway 依赖和无应用自动 schema 初始化；不解析 schema 内容。
- [x] 在生产 datasource 增加 MySQL session `+08:00` 初始化语句；test profile 关闭该 MySQL 专用语句，保持 H2 可启动。
- [x] `MySqlContractIT` 缺 `MYSQL_CONTRACT_URL` 时由 JUnit 明确 SKIP；配置存在时使用 Spring 初始化器原样执行生产 SQL并使用真实 `SystemScheduledTaskMapper`。
- [x] 通过 `INFORMATION_SCHEMA` 检查 FK 数量、关键唯一索引和 DECIMAL 元数据，再执行 DECIMAL round-trip、到期/租约/完成和 Java/MySQL 时间一致性断言。
- [x] 本地运行 `.\mvnw.cmd -Dtest=DatabaseSchemaSourceTest,TransactionBoundaryIntegrationTest test` 和 `.\mvnw.cmd -Dtest=MySqlContractIT test`；本机无 MySQL 时后者显示 SKIP。

### Task 5: 两个前端统一 Gate

**Files:**
- Modify: `kasi-admin-web/package.json`
- Modify: `kasi-admin-web/vite.config.ts`
- Modify: five confirmed admin Prettier blocker files
- Modify: `kasi-admin-web/src/App.test.tsx` (401 redirect assertion timing)
- Modify: `kasi-user-web/package.json`
- Modify: `kasi-user-web/.prettierignore`
- Modify: scoped frontend README/AGENTS references

- [x] 在管理端 `package.json` 增加与当前环境一致的 machine-readable Node/pnpm 要求；两个前端增加 `check` script，执行 lint、format:check、test、build。
- [x] 管理端 Vitest 设置 `maxWorkers: 2`，不改 timeout；只对已确认的格式阻塞文件运行 Prettier；修改前后均连续运行 3 次默认测试，每次 91/91。
- [x] 用户端 `.prettierignore` 增加 `pnpm-lock.yaml`，未写入 lockfile。
- [x] 分别运行 `pnpm check`；管理端完成后连续运行三次默认 `pnpm test`，每次 91/91。

### Task 6: 最小 CI 与 GoodShort smoke

**Files:**
- Create: `.github/workflows/ci.yml`
- Modify: `kasi-backend/scripts/dev/smoke-goodshort-free-content.ps1`

- [x] 创建 Backend/Admin/User/MySQL Contract 四个独立 Job；Java 从 POM、Node/pnpm 从 package.json 读取。
- [x] Backend 运行 `bash ./mvnw verify`；前端 frozen install 后运行 `pnpm check`；MySQL 8.4 Job 只运行 `bash ./mvnw -Dtest=MySqlContractIT test`。
- [x] GoodShort smoke 增加 `-Required`，删除 FFmpeg 检查，保留完整环境下 Maven 失败向上传播。
- [x] 在缺环境的本机运行默认 smoke（SKIP/0）和 `-Required`（FAIL/非零）。

### Task 7: 完整验证与简化复核

**Files:**
- Review: 本次新增/修改文件及直接调用链
- Move: 本设计和本计划到 `docs/archive/root/`

- [x] Java 25 下运行 `.\mvnw.cmd verify`，记录 tests/failures/errors/skipped。
- [x] 两个前端分别运行 `pnpm install --frozen-lockfile` 和 `pnpm check`。
- [x] 运行 MySQL Contract 和 GoodShort smoke；无本地环境时只记录 SKIP，不写 PASS。
- [x] 运行 `git diff --check`、检查用户端 lockfile 未被本次写入，并复核没有本次新增的生产 Java/schema/API 变更。
- [x] 删除重复 Gate、无消费者基础设施和错误机器化规则；不扩展到历史仓库清理。
- [x] 将已完成的本设计/计划归档，并把 ADR-0003 状态和验证证据更新为实际结果。

## 实施结果（2026-09-01）

```text
Backend mvn verify       380 tests, 0 failures, 0 errors, 1 skipped
Admin pnpm check         18 test files, 91/91; build passed
Admin repeat test        3 runs, each 91/91
User pnpm check          16 test files, 40/40; build passed
MySQL Contract           3 tests SKIP (local MySQL/Docker unavailable)
GoodShort default        SKIP, exit 0 (required variables missing)
GoodShort -Required      FAIL, non-zero (required variables missing)
```

本次未回退或覆盖未提交业务改动、生产 schema、API 契约或用户端 lockfile；完整真实 MySQL 结果由专用 CI 环境产生。
