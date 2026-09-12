# 当前架构

## 系统边界

```text
管理员浏览器   -> kasi-admin-web -> kasi-backend -> MySQL / Redis / GoodShort
推广用户浏览器 -> kasi-user-web  -> kasi-backend -> MySQL / Redis / GoodShort
```

三个应用共享 API 契约和发布仓库，但分别构建、运行和部署。后端根包为 `com.kasi.backend`；HTTP 入口在 Controller，业务编排与事务在 Service/impl，持久化在 MyBatis Mapper，业务请求 DTO 与响应 VO 分离。

## 已实现主线

- ADMIN/USER 双认证、JWT 和 Redis 会话版本/单会话校验。
- GoodShort 平台接入、AES-GCM 凭据保护，以及媒体账号 API/人工报白、状态核实和后台任务接续。
- 短剧目录和免费剧集全量/增量同步、断点、租约、本地上下架及永久媒体 URL。
- 平台级 CPS 费率与不可变历史快照。
- 推广链接、订单同步、trackingNo 归因、订单费率/佣金快照及管理员/用户查询；管理端账号报白和推广订单按筛选条件全量导出 XLSX。
- 系统固定任务统一通过 `system_scheduled_task` 的到期时间和数据库租约调度。

正式账单、钱包、提现、自动对账、CapCut、CPA 和 CPM 尚未实现，只能通过独立业务设计进入后续阶段。

## 数据与Schema

生产 schema 的版本真相是不可变 Flyway 迁移链：

```text
kasi-backend/src/main/resources/db/migration/V*.sql
```

Flyway 只通过 Maven `migration` profile 作为独立发布步骤运行；应用没有 Flyway 运行时依赖，并显式关闭启动迁移。当前迁移链为不可变 `V1__baseline.sql` 到 `V11__add_goodshort_drama_full_sync_task.sql`，后续变更只新增更高版本。开发环境可从 `kasi-backend/src/main/resources/db/kasi_promotion.sql` 重建空库，该文件始终描述最新最终结构；MySQL 8.4 Contract 比较开发重建与完整迁移链结果。`*_id` 是逻辑关联，当前生产 schema 不使用物理外键或数据库级联。

上游订单原始 payload、归因字段、费率快照和佣金结果分别保存；历史订单结果不因当前费率修改而重算。

## 短剧目录全量对账

固定任务 `GOODSHORT_DRAMA_FULL_SYNC` 默认每天 `03:00` 为 `DramaSyncProperties.languages` 中的全部配置语言创建全量目录任务。单连接、单语言的全部分页完整成功后，系统在同一短事务中先完成 checkpoint 成功收口，再把本轮未返回的历史短剧标记为 `remote_show_status='MISSING'`、`local_status='OFFLINE'`；同步失败、未完成或租约丢失时不执行缺失对账。

该流程不物理删除短剧、剧集和推广元数据，并保留最后一次 `last_seen_at`。甲方后续重新返回短剧时，同步恢复真实远端状态并刷新 `last_seen_at`，但本地继续保持下架，管理员可在管理端看到“远端在线、本地已下架”后手动决定是否重新上架。管理端将 `MISSING` 显示为“全量未返回”，用户端仍只展示同时满足 `local_status='PUBLISHED'` 和 `remote_show_status='1'` 的短剧。

## 媒体账号报白

GoodShort 接入账号按 Facebook、TikTok、YouTube、Instagram 分别配置 `API` 或 `MANUAL` 报白方式。报白记录持久化 `NOT_SUBMITTED`、`PENDING`、`APPROVED`、`REJECTED`、`SUBMIT_FAILED` 五种状态；管理端读取真实五状态，用户端将技术性的 `SUBMIT_FAILED` 投影为 `PENDING`，且不暴露错误信息。新建人工报白记录直接进入 `PENDING`，表示等待甲方审核；管理员根据甲方结果直接更新为 `APPROVED` 或 `REJECTED`，不需要单独标记已提交。

API 记录由后台 Worker 分批领取到期的 `SUBMIT`/`QUERY` 任务，每次领取使用独立 lease token，并以方式、动作、数据版本、到期时间和 token 共同保护提交与完成更新。新建单条 API 账号仍在本地事务提交后立即尝试提交；批量排队只入队。report 已发出但结果不确定时停止自动重报，由管理员核实甲方已收到或未收到。MANUAL 记录不进入 Worker，也不调用 GoodShort，由管理员按确认矩阵更新状态；已有终态和远端证据在方式切换时保留，未决提交禁止切换。

管理员可按平台、报白方式和真实五状态查询，单条删除任意状态/方式的本地账号及其报白记录；删除不调用甲方接口。账号报白 XLSX 和推广订单 XLSX 都按当前筛选条件导出全部匹配数据，不受列表分页影响。

当前仓库验证覆盖 H2、服务/控制器/持久层回归和三个应用 canonical Gate。真实 MySQL 存量核对与 V1..V11 结构契约、生产 Flyway 执行以及真实 GoodShort report/query 仍属于发布环境验证项，未验证时必须记录为 `SKIP`，不能视为已通过。

## 时间与事务

业务时间的唯一语义是 `Asia/Shanghai`。Java 共享 `Clock` 使用 `ZoneId.of("Asia/Shanghai")`；MySQL datasource 在创建连接时把 session 设置为 `+08:00`。`+08:00` 只是 MySQL 连接实现，不是第二个业务时区定义；H2 test profile 明确关闭该 MySQL 专用语句。

推广链接的 `PENDING`、`SUCCESS`、`FAILED` 通过 production Spring proxy 的独立短事务持久化，第三方 HTTP 调用在数据库事务之外。手动免费剧集同步在事务提交后才提交现有 worker；定时任务的 `next_run_at`、到期查询、租约和完成更新使用同一业务时间基准。
