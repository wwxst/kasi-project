# 当前架构

## 系统边界

```text
管理员浏览器   -> kasi-admin-web -> kasi-backend -> MySQL / Redis / GoodShort
推广用户浏览器 -> kasi-user-web  -> kasi-backend -> MySQL / Redis / GoodShort
```

三个应用共享 API 契约和发布仓库，但分别构建、运行和部署。后端根包为 `com.kasi.backend`；HTTP 入口在 Controller，业务编排与事务在 Service/impl，持久化在 MyBatis Mapper，业务请求 DTO 与响应 VO 分离。

## 已实现主线

- ADMIN/USER 双认证、JWT 和 Redis 会话版本/单会话校验。
- GoodShort 平台接入、AES-GCM 凭据保护、媒体账号报备与重试。
- 短剧目录和免费剧集全量/增量同步、断点、租约、本地上下架及永久媒体 URL。
- 平台级 CPS 费率与不可变历史快照。
- 推广链接、订单同步、trackingNo 归因、订单费率/佣金快照及管理员/用户查询导出。
- 系统固定任务统一通过 `system_scheduled_task` 的到期时间和数据库租约调度。

正式账单、钱包、提现、自动对账、CapCut、CPA 和 CPM 尚未实现，只能通过独立业务设计进入后续阶段。

## 数据与Schema

生产 schema 的版本真相是不可变 Flyway 迁移链：

```text
kasi-backend/src/main/resources/db/migration/V*.sql
```

Flyway 只通过 Maven `migration` profile 作为独立发布步骤运行；应用没有 Flyway 运行时依赖，并显式关闭启动迁移。当前结构冻结为 `V1__baseline.sql`，后续变更只新增更高版本。开发环境可从 `kasi-backend/src/main/resources/db/kasi_promotion.sql` 重建空库，该文件始终描述最新最终结构；MySQL 8.4 Contract 比较开发重建与完整迁移链结果。`*_id` 是逻辑关联，当前生产 schema 不使用物理外键或数据库级联。

上游订单原始 payload、归因字段、费率快照和佣金结果分别保存；历史订单结果不因当前费率修改而重算。

## 时间与事务

业务时间的唯一语义是 `Asia/Shanghai`。Java 共享 `Clock` 使用 `ZoneId.of("Asia/Shanghai")`；MySQL datasource 在创建连接时把 session 设置为 `+08:00`。`+08:00` 只是 MySQL 连接实现，不是第二个业务时区定义；H2 test profile 明确关闭该 MySQL 专用语句。

推广链接的 `PENDING`、`SUCCESS`、`FAILED` 通过 production Spring proxy 的独立短事务持久化，第三方 HTTP 调用在数据库事务之外。手动免费剧集同步在事务提交后才提交现有 worker；定时任务的 `next_run_at`、到期查询、租约和完成更新使用同一业务时间基准。
