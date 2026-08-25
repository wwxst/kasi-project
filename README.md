# Kasi 推广平台 Monorepo

本仓库统一纳管 Kasi 的三个端：后端、管理端和推广用户端。三端共享同一 Git 提交历史和发布边界，但保持独立技术栈、依赖、构建和运行方式。

## 当前范围

当前已实现主线是 GoodShort 短剧推广 CPS：账号认证、平台接入、媒体账号报备、短剧目录同步、推广链接、订单每分钟自动同步最近 3 天（保留管理员手动补拉）、CPS 费率快照、订单佣金和按月查询/导出。订单钱包、自动结算和 CapCut/CPA/CPM 尚未纳入本次快速上线基线。

“当前已实现”以 [当前架构](docs/architecture/current.md) 和各项目文档为准；未来建议以 [缺口清单](docs/development/gaps.md) 和 `docs/archive/` 中的历史设计为准。

## 快速入口

- [开发总则](DEVELOPMENT.md)
- [文档与开发规范](docs/development/governance.md)
- [文档索引](docs/README.md)
- [后端说明](docs/projects/kasi-backend.md)
- [管理端说明](docs/projects/kasi-admin-web.md)
- [用户端说明](docs/projects/kasi-user-web.md)
- [迁移 ADR](docs/adr/ADR-0001-root-monorepo.md)

## 本地运行

先启动后端，再按需启动任一前端。默认后端为 `http://localhost:8080`，两个前端开发服务器默认使用 `http://localhost:5173`；若端口冲突由 Vite 自动或手动调整。

```powershell
cd kasi-backend
.\mvnw.cmd spring-boot:run
```

```powershell
cd kasi-admin-web
pnpm install --frozen-lockfile
pnpm dev
```

```powershell
cd kasi-user-web
pnpm install --frozen-lockfile
pnpm dev
```

## Git 与发布

根目录是唯一提交入口，远端为 `https://github.com/wwxst/kasi-project.git`。迁移阶段保留旧项目目录和备份；不得通过 force push 覆盖远端已有历史。发布流程见 [Git 与发布规范](docs/development/git-and-release.md)。
