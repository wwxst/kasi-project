# Kasi Monorepo Agent Guide

本文件适用于仓库根目录及三个子项目。根仓库统一管理提交、发布和跨项目文档；子项目保留各自技术栈、构建、测试和运行边界。

## 开发原则

- 每次修复先梳理现象、影响范围和根因，再做最小改动。
- 一个阶段只解决一个主要问题；涉及大范围重构、数据迁移或 API 破坏性变更时先汇报并确认。
- 当前实现、已验证行为和未来规划必须分开记录，不把规划描述成已实现。
- 未发布 API 可以做必要的破坏性重构；已被客户端使用的契约必须同步更新三端测试和文档。
- 不提前实现尚未有真实用例支撑的复杂抽象。短剧 CPS 现有逻辑优先复用；CapCut/CPA/CPM 暂不属于本次上线范围。
- 重要架构决策写入 `docs/adr/`，实施计划写入 `docs/plans/`，历史设计资料放在 `docs/archive/`。
- 不提交凭据、日志、依赖目录、构建产物或本机配置。

## 工作区规则

- 所有 Git 操作从仓库根目录执行；提交和推送统一针对根仓库。
- 修改前检查 `git status` 和相关 diff，保留用户已有改动。
- 禁止使用 `git reset --hard`、`git checkout --` 和未经确认的 force push。
- 三个项目仍可独立进入目录运行其命令；根级文档只描述编排边界。

## 文档入口

- [开发总则](DEVELOPMENT.md)
- [文档与开发规范](docs/development/governance.md)
- [文档索引](docs/README.md)
- [当前架构](docs/architecture/current.md)
- [缺口清单](docs/development/gaps.md)
