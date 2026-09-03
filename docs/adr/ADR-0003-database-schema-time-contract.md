# ADR-0003：数据库 Schema 与时间契约

- 日期：2026-09-01
- 状态：已实施
- MySQL 真实环境结果：本机未配置；由专用 CI Contract Job 产生
- 范围：数据库最终结构、业务时间、MySQL session、MySQL Contract Gate
- 关联：[测试规范](../development/testing.md)、[当前架构](../architecture/current.md)、[ADR-0004](ADR-0004-production-database-migrations.md)

## 背景与问题

本决策实施时，代码使用单一空库初始化 SQL，尚无生产升级路径。该 schema 所有权和升级机制现已由 [ADR-0004](ADR-0004-production-database-migrations.md) 替代；本决策关于最终结构、业务时间和真实 MySQL Contract 的约束继续有效。H2 能证明大部分业务和持久化行为，不能可靠证明 MySQL 的真实索引/外键元数据、DECIMAL 精度、方言 SQL 和 session 时间。

Java 任务时间已经由 production `Clock` 使用 `Asia/Shanghai`。若 MySQL session 使用主机默认时区，`CURRENT_TIMESTAMP`、`next_run_at` 和 Java due 判断可能产生环境差异。

## 决策

开发环境最终 schema 重建脚本是：

```text
kasi-backend/src/main/resources/db/kasi_promotion.sql
```

生产版本迁移现由 ADR-0004 的不可变 Flyway 链拥有；应用仍不自动创建、升级或修补数据库。开发阶段 schema 改变后可删除数据库，在已选定空 schema 上手动执行完整重建 SQL。

业务时间唯一语义为 `Asia/Shanghai`。Java 使用 `ZoneId.of("Asia/Shanghai")`；MySQL datasource 建立连接时执行 `SET time_zone = '+08:00'`。`+08:00` 只属于 MySQL connection/session 实现，不成为业务时区名称。

GitHub CI 使用 MySQL 8.4 service，分别以开发重建 SQL 和生产 Flyway 链初始化两个空库。测试直接读取 `INFORMATION_SCHEMA`，比较两条路径的最终结构和固定数据，并通过 production `SystemScheduledTaskMapper` 验证真实 SQL；不新增 SQL Parser，不在 MySQL 上重复完整后端套件。

## 备选方案

- 当时未恢复 Flyway：项目仍处于可重建阶段；该选择已由 ADR-0004 在进入生产发布前替代。
- 不依赖 H2 推断 MySQL 元数据和 DECIMAL：两者方言与元数据行为不同。
- 不新增 Clock framework：现有 production `Clock` 已提供唯一 Java 时间来源。
- 不把 GoodShort Real Smoke 放入普通 CI：它需要私人凭据和真实业务数据，与数据库契约无关。

## 影响

普通后端 Gate 仍使用 H2。H2 test profile 明确关闭 MySQL 专用 session 初始化语句；MySQL Contract 环境使用公共 application 配置，从而验证真实 session `+08:00`。

workflow 使用独立 MySQL Contract Job。缺少本地 MySQL 环境时聚焦测试明确 SKIP；专用环境缺配置、任一初始化路径执行失败或任何契约断言失败均为 FAIL。

## 迁移与回滚

本决策本身不修改生产 schema 或业务数据。生产增量升级、备份和失败回滚现遵循 ADR-0004；若 datasource session 初始化导致连接失败，回滚连接配置和对应应用版本，不得为绕过失败修改已执行迁移。

## 验证证据

- Java 25 常规 `mvn verify` 当前结果：380 个测试，0 个失败、0 个错误、1 个跳过；跳过项是缺少外部 GoodShort 环境的现有真实 smoke。
- 结构、schema-source 和真实 Spring 事务聚焦 Gate：6 个测试，0 失败、0 错误、0 跳过。
- 当前生产 SQL 已由现有 `ResourceDatabasePopulator` 测试支持原样执行；H2 test profile 关闭 MySQL 专用 session SQL 后，应用上下文和事务集成测试通过。
- 本机未提供 MySQL/Docker：`MySqlContractIT` 3 个测试明确 `SKIP`，不能记为 `PASS`；配置完整的 CI Job 才能产生真实 MySQL 结果。
- GoodShort Real Smoke 本机缺配置时明确 `SKIP`/退出 0；`-Required` 缺配置为非零 `FAIL`。真实请求失败仍保持 `FAIL`。
