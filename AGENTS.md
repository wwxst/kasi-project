# Kasi Monorepo Agent Guide

本文件适用于仓库根目录和三个子项目。根仓库统一管理 Git、跨项目文档和 CI；每个应用保留独立技术栈、依赖、构建、测试和运行边界。进入子项目后还必须遵守其 scoped `AGENTS.md`。

## 强制边界

- 开始前在根目录运行 `git status --short --branch`，阅读 `DEVELOPMENT.md` 和相关项目文档。
- 工作区属于用户。禁止重置、覆盖、批量格式化或顺手整理非当前任务改动；修改重叠文件时保留原改动意图。
- 当前未提交内容只是实现现实，不自动成为正式业务契约。只有需求、真实调用链和最新验证共同确认的行为才能进入 current docs、长期规则或永久 Gate。
- 当前实现、已批准未实施、建议/缺口和历史归档必须分开记录。
- 一次只解决一个可独立验证的问题。业务逻辑、schema、API、权限或跨模块契约变更必须先形成设计并获得确认。
- 优先最小直接实现，不为未来场景增加抽象、兼容层、Runner、Parser、Manager 或基础设施。
- 不提交凭据、日志、依赖目录、构建产物或本机配置；不主动提交或推送，除非用户明确授权。
- 生产数据库只通过独立 Flyway 发布步骤升级；已执行的 `db/migration/V*.sql` 禁止修改，应用启动不得自动迁移，开发重建脚本必须与完整迁移链保持最终结构一致。

## Gate 准入

规则只有同时满足以下条件才进入长期 Machine Gate：

- 是当前稳定规则或重要业务行为；
- 存在真实回归风险；
- 机器能够可靠判断；
- 检查简单、稳定、可重复；
- 长期维护成本低于它防止的问题。

语义性 Review 问题、实验性工作区行为和没有稳定基础的能力不机器化。未覆盖、静态搜索无调用或看似重复只能触发审查，不能单独作为删除证据。删除前必须确认调用链、Spring/runtime 注册、配置、Mapper/XML、schema、public API 和当前消费者。

## 完成要求

- 先定义与风险相称的验证，再实施最小改动。
- 完成前运行受影响项目的 canonical Gate 和 `git diff --check`，记录退出码、测试数量及环境限制。
- MySQL、GoodShort 等真实环境缺失时只能记录 `SKIP`；不能写成 `PASS`。真实执行失败必须是 `FAIL`。
- Simplification Review 只检查本次文件、直接调用链和本次验证暴露的问题，不扩展为全仓库历史清理。

## Canonical 文档

- [仓库入口](README.md)
- [日常开发流程](DEVELOPMENT.md)
- [当前架构](docs/architecture/current.md)
- [工程治理规则](docs/development/governance.md)
- [测试与 CI](docs/development/testing.md)
- [文档索引](docs/README.md)
