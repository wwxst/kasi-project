# 仓库文档与工程治理

## Canonical Ownership

- 根 `README.md`：仓库入口、三应用边界、当前产品范围和链接。
- 根 `AGENTS.md`：Agent 强制边界、工作区保护和 Gate 准入。
- 根 `DEVELOPMENT.md`：日常流程、变更级别和核心 Gate 入口。
- `docs/architecture/current.md`：已验证的当前架构、数据与运行行为。
- `docs/development/governance.md`：工程、分层、事务、schema 和文档规则。
- `docs/development/testing.md`：测试命令、CI 数据流和结果语义。
- `docs/projects/*.md`：单个应用特有的技术边界和命令。
- `docs/adr/`：重要决策和替代关系。
- `docs/plans/`：已批准但未完成的计划。
- `docs/archive/`：完成/替代的历史资料，不作为当前契约。

工具版本、依赖和执行行为由 POM、package.json、lockfile、workflow 和生产 SQL 等机器可读文件拥有。文档只说明所有权和使用方法，不复制另一份需要人工同步的版本真相。

## 文档状态

每项内容必须属于且只属于一种状态：当前已实现、已批准未实施、建议/缺口、历史归档。未提交实现不能因为存在就升级为当前契约；必须同时有需求确认、真实调用链和最新验证。

遵循 One Fact, One Home、One Rule, Smallest Applicable Scope 和 Canonical Ownership。跨项目规则只写在根文档，项目文档通过链接引用；同一事实不在多个 README/计划中复制成长段说明。

## Gate 原则

Machine Gate 只保护稳定、重要、有真实回归风险、可可靠重复判断且维护收益高于成本的规则。语义性代码质量、实验性行为、无稳定环境或高误报规则继续由 Review/人工验证负责。

测试强度按风险选择：

- 普通业务、认证、Controller、Mapper 和 Spring Integration 进入后端 `mvn verify`。
- H2 证明应用行为和一般持久化；MySQL Contract 只证明 H2 无法可靠证明的真实方言、元数据、精度和时间行为。
- 第三方真实 smoke 与普通 CI 分离，不让私人凭据成为 push/PR 前提。

## 分层与事务

- Controller 处理 HTTP、参数校验和响应组装，不直接依赖 Mapper。
- `@RequestBody` 使用业务 DTO 并触发 Jakarta Validation。
- `*.service/*Service` 定义接口，`*.service.impl/*ServiceImpl` 提供 Spring 实现和事务边界。
- Mapper 负责持久化；“Controller 是否包含业务逻辑”等语义问题由 Review 判断，不做脆弱静态 Gate。
- 外部 HTTP 不放入数据库事务；需要独立提交或 commit 后动作时，必须通过真实 Spring proxy/transaction 验证行为。

## Schema

`kasi-backend/src/main/resources/db/migration/V*.sql` 是生产 schema 的不可变版本真相，只能新增更高版本，不能修改已执行迁移。Flyway 只作为独立发布步骤运行，应用启动不自动 migration/upgrade。`kasi-backend/src/main/resources/db/kasi_promotion.sql` 是开发空库重建脚本并表示最新最终结构；每个 schema 变更必须同时更新迁移链与重建脚本。真实 MySQL Contract 读取 `INFORMATION_SCHEMA` 比较两条路径，不新增 SQL Parser 或正则镜像测试。

## 简化与删除

未覆盖、静态无调用和表面重复只能作为 Review Signal。删除生产代码或历史测试前必须确认调用链、Spring/runtime 注册、配置、Mapper/XML、schema、public API 和当前消费者。本次简化审查默认只覆盖本次文件、直接调用链和验证明确暴露的问题。
