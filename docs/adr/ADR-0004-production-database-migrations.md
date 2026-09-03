# ADR-0004：生产数据库版本迁移

- 日期：2026-09-03
- 状态：已实施
- 范围：生产 schema 所有权、数据库升级、发布顺序、MySQL Contract
- 关联：[生产数据库增量迁移设计](../superpowers/specs/2026-09-03-production-database-migration-design.md)、[ADR-0003](ADR-0003-database-schema-time-contract.md)、[Git 与发布规范](../development/git-and-release.md)

## 背景与问题

项目准备进入不可删库重建的生产阶段。原有 `kasi_promotion.sql` 能可靠创建开发空库，但不能记录已部署版本，也不能安全地按顺序升级保留业务数据的数据库。

## 决策

生产 schema 的版本真相是 `kasi-backend/src/main/resources/db/migration/V*.sql` 不可变 Flyway 链。当前完整结构冻结为 `V1__baseline.sql`；执行过的迁移禁止修改，后续结构变化只能新增 `V2__...sql`、`V3__...sql`。

Flyway 只存在于 Maven `migration` profile，作为独立发布步骤执行。应用没有 Flyway 运行时依赖，且通过 `spring.flyway.enabled=false` 明确禁止启动迁移。连接 URL、用户名和密码只从 `FLYWAY_URL`、`FLYWAY_USER`、`FLYWAY_PASSWORD` 注入，不提供默认生产连接。

`kasi_promotion.sql` 保留为开发环境空库重建脚本，始终描述最新最终结构。每次新增版本迁移时必须同步更新该文件；MySQL Contract 分别执行开发初始化和完整 Flyway 链，并比较最终表、列、索引、约束和固定初始数据。

已有数据库必须先确认当前结构与 `V1` 一致，再由发布人员显式执行一次 version `1` baseline。`baselineOnMigrate=false`，不允许把未知非空数据库静默登记为基线。`cleanDisabled=true`，禁止对受管数据库执行 `clean`。

## 备选方案

- 拒绝继续只维护完整初始化 SQL：它不能审计生产数据库已经执行的版本，也无法可靠升级保留数据的数据库。
- 拒绝应用启动自动迁移：数据库变更必须在应用发布前独立观察、停止和审计，不能把 DDL 成功与应用进程启动绑定。
- 拒绝长期启用 `baselineOnMigrate`：目标库选错或结构不一致时可能被静默接受。
- 拒绝为每个迁移强制维护反向 SQL：MySQL DDL 可能隐式提交，破坏性失败通过备份恢复或新的正向修复迁移处理。

## 影响

生产发布增加备份、`info`、`validate`、`migrate` 和结果核对步骤。迁移文件的校验和成为发布契约；已执行文件不能格式化或重写。应用启动行为和现有业务 API 不变。

开发人员仍可删除开发库并运行最新 `kasi_promotion.sql`。CI 增加第二个 MySQL 8.4 schema，用于证明开发重建结果与生产迁移链一致；本机未提供真实 MySQL 凭据时该 Contract 只能明确 `SKIP`。

## 迁移与回滚

新空库直接执行完整 Flyway 链。已经由旧初始化 SQL 创建且经核对的数据库，一次性执行：

```powershell
.\mvnw.cmd -Pmigration flyway:baseline "-Dflyway.baselineVersion=1"
```

每次生产发布必须先冻结代码和迁移、完成并验证数据库备份，再依次执行 `flyway:info`、`flyway:validate` 和 `flyway:migrate`。任何一步失败立即停止发布；禁止跳过版本、修改 `flyway_schema_history` 或修改已执行脚本。根据失败点恢复备份，或在确认数据状态后新增正向修复迁移。

## 验证证据

结构守卫 `DatabaseSchemaSourceTest` 为 1 个测试、0 失败、0 错误；Java 25 `mvn verify` 为 399 个测试、0 失败、0 错误、1 跳过；SpotBugs High 为 0 Bug、0 Error。迁移一致性 Contract 在本机缺少 `MYSQL_MIGRATION_URL` 时为 1 个测试明确 `SKIP`，不能记为真实 MySQL PASS；MySQL 8.4 结果由专用 CI Job 产生。生产备份恢复和目标环境迁移尚未执行，也不能记为已验证。
