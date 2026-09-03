# Kasi Monorepo Development

本文件是日常开发和验证入口。跨项目规则见 [工程治理](docs/development/governance.md)，命令和 CI 细节见 [测试规范](docs/development/testing.md)；子项目特有约束以各自 README/AGENTS 为准。

## 日常流程

1. 在根目录运行 `git status --short --branch`，确认已有改动和本次允许修改范围。
2. 阅读相关项目文档，记录现象、影响、复现、证据、根因假设和非目标。
3. 一次只实施一个可独立验证的阶段；先定义失败、成功和边界验证。
4. 业务逻辑、schema、API、权限、事务或跨模块契约变更先写设计并获得确认。
5. 实施最小改动，运行受影响项目 Gate，再运行 `git diff --check` 和范围复核。
6. 只把验证后的当前事实写入 current docs；完成/替代的计划移入 `docs/archive/`。

## 变更级别

- `L0`：文档、注释或格式，不改变运行行为；检查范围、链接和 diff。
- `L1`：单模块内部实现或缺陷修复，不改变外部契约；需要针对性测试和模块 Gate。
- `L2`：跨模块、API、schema、权限、事务、调度或配置契约；先设计/ADR，再分阶段实施。
- `L3`：破坏性重构、数据迁移或运行模型改变；必须说明影响、迁移/重建、回滚和验收并获得确认。

实施中若范围升级到 `L2/L3`、测试需要新依赖、或只能通过改变业务契约才能成立，立即停止并汇报，不通过继续堆叠改动绕过边界。

## 核心 Gate

后端（Java 版本以 `kasi-backend/pom.xml` 为准）：

```powershell
cd kasi-backend
.\mvnw.cmd verify
```

管理端和用户端（Node/pnpm 版本以各自 `package.json` 为准）：

```powershell
cd kasi-admin-web
pnpm install --frozen-lockfile
pnpm check

cd ..\kasi-user-web
pnpm install --frozen-lockfile
pnpm check
```

`pnpm check` 已包含 lint、format、test 和包含 TypeScript 编译的 build，不再重复运行独立 typecheck。MySQL Contract 与 GoodShort Real Smoke 有真实环境前置条件，不属于本地无条件 PASS 项；运行方式和结果语义见测试规范。

后端 CI 将 Unit、Integration、MySQL Contract 和 Static Analysis 独立显示。JaCoCo 当前只生成报告；SpotBugs High 与 pull request 的 high/critical Dependency Review 为阻断项；GoodShort Real Smoke 只通过手动 workflow 执行。

生产数据库迁移是应用发布前的独立步骤，不属于应用启动。版本迁移、已有库 baseline、备份和失败停止规则见 [ADR-0004](docs/adr/ADR-0004-production-database-migrations.md) 与 [Git 发布规范](docs/development/git-and-release.md)。
