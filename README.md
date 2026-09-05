# Kasi 推广平台 Monorepo

本仓库统一纳管三个独立应用：

```text
kasi-backend/    Spring Boot + MyBatis 后端
kasi-admin-web/  React + Ant Design Pro 管理端
kasi-user-web/   React + TDesign 用户端
```

三端共享 Git、跨项目工程规则和 CI，但不共享前端源码、依赖、构建配置或运行进程。跨端业务契约以后端 API/DTO/VO 和对应前端 API 类型为准。

## 当前产品范围

当前已实现主线是 GoodShort 短剧推广 CPS：双端认证、平台接入、媒体账号报备、短剧目录与免费剧集同步、推广链接、订单同步与归因、CPS 费率/佣金快照，以及管理员和推广用户查询导出。

正式账单、钱包、提现、自动对账、CapCut、CPA 和 CPM 不属于当前实现。当前行为以 [当前架构](docs/architecture/current.md) 和各项目文档为准；建议与未决事项见 [工程缺口](docs/development/gaps.md)。

生产数据库通过独立 Flyway 版本链升级，应用启动不自动迁移；开发空库仍可用完整 `kasi_promotion.sql` 重建。发布与 baseline 规则见 [ADR-0004](docs/adr/ADR-0004-production-database-migrations.md)。

## 入口

- [日常开发流程与核心 Gate](DEVELOPMENT.md)
- [工程治理与文档所有权](docs/development/governance.md)
- [测试、CI 与 Real Verification](docs/development/testing.md)
- [生产部署运行手册](docs/development/production-deployment.md)
- [后端说明](docs/projects/kasi-backend.md)
- [管理端说明](docs/projects/kasi-admin-web.md)
- [用户端说明](docs/projects/kasi-user-web.md)
- [架构决策](docs/adr/architecture-decisions.md)
- [Git 与发布](docs/development/git-and-release.md)

根目录是唯一 Git 提交和发布入口，远端为 `https://github.com/wwxst/kasi-project.git`。默认不提交或推送；发布必须单独获得授权并按 Git 规范核对本地、跟踪分支和远端状态。
