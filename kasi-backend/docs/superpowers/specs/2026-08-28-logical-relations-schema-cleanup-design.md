# 数据库逻辑关联与字段精简设计

- 日期：2026-08-28
- 状态：已实施
- 范围：`src/main/resources/db/kasi_promotion.sql`、H2 测试 schema、推广链接/订单模块、数据库映射和相关文档
- 前置决策：开发数据库可删除重建；本次不提供迁移脚本，也不操作现有数据库

## 背景

当前唯一初始化 SQL 和测试 schema 均不声明物理外键或数据库级联；业务代码通过 Service 完成关联存在性和归属校验，形成统一的应用层逻辑关联模型。

`promotion_task` 及其创建、查询接口已删除；该任务壳没有真实链接生成、统计同步或任务执行器。当前可用推广链路由 `promotion_link`、`promotion_order` 和佣金快照完成。

## 已确认目标

生产初始化 SQL 与测试 schema 必须同时满足：

- 不出现 `FOREIGN KEY`。
- 不出现 `REFERENCES`。
- 不出现 `ON DELETE CASCADE`、`ON DELETE RESTRICT` 或 `ON DELETE SET NULL`。
- `*_id` 只作为普通逻辑关联字段，保留实际查询需要的索引和唯一约束。
- Service 在业务写入时校验必要的关联记录存在性、用户归属和平台链路一致性。
- 不为理论上的并发删除统一增加锁、重试或补偿框架。

## 表与字段范围

### 删除整套未闭环模块

已删除 `promotion_task` 表及其 Controller、DTO、Entity、Mapper、Service、VO、枚举、XML 和测试，并删除用户端 `/api/user/promotion/tasks` 创建与查询接口。

### 删除已确认无消费者或兼容意义的字段

以下字段已从生产 SQL、测试 schema、Entity、Mapper 和直接响应模型中同步删除：

```text
system_scheduled_task
  id                    固定任务以 task_code 作为稳定主键，应用不需要代理主键
  title                 固定任务名称由 ScheduledTaskCode 提供
  interval_minutes      旧兼容字段，无当前消费者，随 schema 一并删除
  created_at            无读取消费者
  updated_at            无读取消费者，租约更新不需要持久化审计时间

promotion_link
  provider_code        与 provider_id 重复且无读取消费者
  media_account_name   当前 schema 无此列，且没有读取消费者
  landing_type         当前 schema 无此列，且没有读取消费者
  custom_params        链接表对 GoodShort 回传值的重复存储；本地归因键是 tracking_no

promotion_order
  media_account_id     双链接模型不绑定媒体账号，服务从未赋值
  first_synced_at      与首次插入时的 created_at 重复

provider_sync_checkpoint
  started_at            只写入，不进入同步状态响应或调度判断
  finished_at           只写入，不进入同步状态响应或调度判断
  total_upserted        当前实现等于 inserted_count + updated_count，可在响应层计算
  skipped_count         当前实现没有可达的跳过分支

provider_media_filing
  created_at            不进入报备响应，也不参与任务状态机
  updated_at            不进入报备响应，也不参与任务状态机

provider_drama_content
  created_at            不对外返回，也不参与同步判断
  updated_at            不对外返回，也不参与同步判断

short_drama_provider
  created_at            不对外返回，也没有更新消费者
  updated_at            不对外返回，也没有更新消费者

drama_download_task
  created_at            不对外返回，过期时间承担任务清理依据
  updated_at            不对外返回，进度和状态字段承担实际查询语义
```

`provider_sync_checkpoint.total_fetched` 保留为上游实际返回记录数，不能用新增数与更新数替代。`total_upserted` 在响应层由新增数加更新数计算。

`promotion_link` 的 `provider_id`、`connection_id`，以及订单和佣金历史中的平台、连接、用户、短剧 ID 暂不删除。这些 ID 是生成来源或结算归因快照；父记录删除后仍需保留历史事实，不能用一次关联查询替代它们。

### 保留的结算、安全和事务字段

保留订单原始 JSON、原始状态、原始金额、币种、同步窗口、`tracking_no`、归因快照、规则历史 ID、五项费率和佣金金额。保留账号报备的资料版本、远端状态、租约、重试和错误字段。保留管理员操作人字段、密钥密文、幂等键、唯一键和任务租约字段。

## 应用层关联规则

### 写入校验

- 创建推广链接时检查用户有效、平台和连接可用、短剧存在且属于该连接、分佣规则存在。
- 写入媒体报备时检查媒体账号存在且属于当前用户，并检查连接与平台一致。
- 写入短剧目录和剧集时检查连接存在；剧集写入前确认所属短剧存在。
- 创建下载任务时检查短剧和请求剧集属于当前可用短剧，并只保存合法剧集 ID。
- 订单同步以运行时连接作为来源；订单归因使用订单自身的 `custom_params` 作为供应方回传的 `tracking_no` 载体，查找推广链接的 `tracking_no`。订单事实写入不要求被归因主体仍然存在。

### 删除语义

- 订单、佣金规则历史、推广链接和审计记录是历史事实，主体删除后不删除、不置空原始 ID。
- 历史事实查询以事实表为主表；展示主体名称时只能使用 `LEFT JOIN` 或独立查询，不能因用户、平台、短剧或规则记录缺失而过滤掉事实行。
- 临时下载任务只由下载 Service 按过期时间显式清理；不依赖数据库级联。短剧剧集当前没有独立删除 API，因此本次不增加额外级联框架。
- 任何删除操作都不通过捕获数据库外键异常来判断是否允许；现有业务删除路径使用明确的 Service 规则。
- 不为所有父子关系增加统一并发锁、重试或补偿机制。现有事务边界和必要的行级更新条件保持不变。
- 推广链接保留用于历史查询和归因追溯；创建、重试或补报时必须重新通过当前用户、平台、连接、短剧和分佣规则校验，不能把已删除或已失效主体下的历史链接当作新的业务入口。

## 索引与约束

保留主键、唯一键、非空约束、状态值校验和实际查询索引。删除字段后同步删除只服务于这些字段的索引；不新增面向假设查询的索引。应用层逻辑关联字段仍使用现有命名和类型，避免无意义的兼容列。

生产初始化 SQL 与 H2 测试 schema 保持相同的 `idx_*` 查询索引名称和列顺序；测试契约按索引名及列清单逐项比较。

## API 与映射

- 定时任务标题从 `ScheduledTaskCode` 枚举生成；周期使用 `cycle_type`、`interval_value` 及余数字段。
- 同步状态响应中的拉取总数读取上游实际拉取的 `total_fetched`；写入总数由新增数与更新数计算；跳过数不再从数据库读取。
- 推广链接响应不再读取链接表的 `provider_code` 和 `custom_params`；订单响应仍保留订单表的 `custom_params`，它是供应方回传的归因载体。平台名称等已有展示数据继续通过现有查询关联提供。
- 订单管理响应不再暴露始终为空的媒体账号字段；订单历史快照字段保持不变。

## 验证

- 初始化结构测试读取生产 SQL，断言两个 schema 中禁止关键字和删除级联数量均为零，并比较生产与测试 schema 的查询索引集合。
- 优先调整并运行现有账号、平台接入、短剧目录、推广链接、订单、下载任务和定时任务测试；只有删除后历史事实仍可查询这一关键行为没有现有覆盖时，才增加最小必要测试。
- 使用 Java 25 执行聚焦测试、完整 Maven 测试和 `git diff --check`。
- 不连接、删除或重建用户当前开发数据库；交付说明只给出用户手动重建步骤。

## 非目标

- 不新增迁移脚本，不保留 Flyway 兼容路径。
- 不重做订单、钱包、提现、统计或真实推广任务执行器。
- 不把当前未实现的统计字段重新设计成新的表或事件系统。
