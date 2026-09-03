# Git 与发布规范

根目录是唯一 Git 操作入口。提交前检查：

```powershell
git status --short --branch
git diff --check
git diff --stat
```

只按当前任务意图逐文件暂存；不得提交 `node_modules`、`target`、`dist`、日志、环境文件、IDE 状态或凭据。默认不提交、不推送；未经明确授权不得 force push。

发布前运行 [测试规范](testing.md) 中三个核心 Gate 和适用的 Real Verification。生产数据库按 [ADR-0004](../adr/ADR-0004-production-database-migrations.md) 通过独立 Flyway 步骤升级，应用启动不自动迁移。

迁移前必须冻结代码和迁移文件、备份生产数据库并确认备份可恢复。连接信息只通过发布环境变量或密钥服务注入，不写入仓库、配置文件或命令历史：

```powershell
$env:FLYWAY_URL='jdbc:mysql://host:3306/kasi_promotion?characterEncoding=UTF-8&connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true'
$env:FLYWAY_USER='...'
$env:FLYWAY_PASSWORD='...'
cd kasi-backend
.\mvnw.cmd -Pmigration flyway:info
.\mvnw.cmd -Pmigration flyway:validate
.\mvnw.cmd -Pmigration flyway:migrate
```

已经由旧 `kasi_promotion.sql` 初始化且结构核对无误的数据库，只在首次纳管时执行一次 `.\mvnw.cmd -Pmigration flyway:baseline "-Dflyway.baselineVersion=1"`。禁止 `flyway:clean`、修改已执行迁移、跳过版本或手工改写 `flyway_schema_history`。任一步失败立即停止发布，根据数据状态恢复备份或新增正向修复迁移。

获得推送授权后核对：

```powershell
git rev-parse HEAD
git rev-parse @{upstream}
git ls-remote origin refs/heads/master
```

只有本地、跟踪分支和远端目标状态符合本次发布意图时才能报告发布完成。
