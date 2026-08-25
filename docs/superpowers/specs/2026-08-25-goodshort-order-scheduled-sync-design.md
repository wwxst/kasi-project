# GoodShort 订单定时同步设计

日期：2026-08-25
状态：已实施

## 1. 问题与目标

当前 GoodShort 订单只能由管理员调用 `POST /api/admin/promotion/orders/sync` 手动拉取。订单数据不能稳定地进入现有的归因和 CPS 佣金闭环，且需要人工重复执行同步操作。

本变更在不改变订单归因、佣金计算和账单语义的前提下，自动同步 GoodShort 最近 3 天的订单：

- 默认每 1 分钟执行一次。
- 同步窗口为执行时刻 `now - 3 days` 至 `now`。
- 管理员手动同步保留，用于补拉 3 天前的历史订单或指定时间范围。
- 同一订单仍按 `(connection_id, external_order_id)` 幂等写入。
- 仍只按 `customParams -> tracking_no -> user_id` 归因，并继续使用已有五项费率快照和退款冲销规则。

本次为 L2 变更：会新增 Flyway 数据，并增加跨模块的调度到订单同步调用。它不新增公开 HTTP 接口，也不修改已有接口参数或返回结构。

## 2. 已确认的现状

现有通用调度能力：

- `scheduledtask.task.ScheduledTaskScheduler` 每分钟调用 `ScheduledTaskDispatchService.processDueBatch()`。
- `scheduledtask.service.impl.ScheduledTaskDispatchServiceImpl` 从 `system_scheduled_task` 取得到期记录，通过 `claimLease` 获取数据库租约，执行后以 `completeRun` 计算并保存下一次执行时间。
- 现有任务编码只有 `ScheduledTaskCode.GOODSHORT_DRAMA_INCREMENTAL_SYNC`，分发逻辑只会调用 `DramaCatalogSyncService.requestScheduledIncremental(...)`。
- 管理端接口 `GET/PUT /api/admin/system/scheduled-tasks` 和 `ScheduledTaskPage.tsx` 已能展示、启停和编辑固定任务周期；页面由接口返回的任务列表驱动，不需要新增任务页面。

现有订单同步能力：

- `PromotionOrderAdminServiceImpl.sync(PromotionOrderSyncDTO)` 已完成平台能力解析、分页拉取、每条订单 `PromotionOrderService.upsert(...)`、新增/更新/未归因计数汇总。
- `GoodShortAdapter.fetchOrders(OrderSyncRequest)` 已支持精确时间窗和分页，并区分临时平台异常与远端拒绝。
- `PromotionOrderServiceImpl.upsert(...)` 已持久化上游订单原始数据、执行 trackingNo 归因并保存 CPS 佣金快照；重复同步不会创建重复订单。

## 3. 方案选择

选择：新增一条通用定时任务记录，并抽取订单同步编排服务供手动和自动入口共用。

不选择新增第二个 Spring `@Scheduled`。已有 `ScheduledTaskScheduler` 已每分钟扫描、支持租约、任务页配置和启停；第二个调度器会产生两套周期配置、并发控制和运维入口。

不选择在 `ScheduledTaskDispatchServiceImpl` 中复制 `PromotionOrderAdminServiceImpl.sync(...)`。复制会使手动与自动同步的分页、汇总、异常语义逐步分叉。

不选择本轮新增检查点、任务运行历史、死信队列或自动重试表。这些属于后续可观测性与补偿能力，不是最近 3 天滑动窗口上线的必要条件。

## 4. 目标架构

新增订单同步业务服务，名称以实现阶段的仓库命名为准，推荐边界如下：

```text
AdminPromotionOrderController
  -> PromotionOrderAdminService
  -> PromotionOrderSyncService
  -> ProviderRuntimeConnectionService / OrderSyncProviderAdapter
  -> PromotionOrderService.upsert

ScheduledTaskDispatchService
  -> PromotionOrderSyncService
  -> ProviderRuntimeConnectionService / OrderSyncProviderAdapter
  -> PromotionOrderService.upsert
```

`PromotionOrderSyncService` 接收 `providerId`、`startDate`、`endDate`，负责现有的分页拉取和计数汇总；它不决定自动同步窗口，也不处理 HTTP 参数。

`PromotionOrderAdminService` 继续承担管理端查询、导出和手动入口编排，手动同步直接将 DTO 中的时间范围交给共享服务。

`ScheduledTaskDispatchServiceImpl` 只负责领取任务、根据任务编码分发，并在 `Clock` 生成的当前时间上构造最近 3 天窗口。它先通过 `ShortDramaProviderMapper.findByCode("GOODSHORT")` 查找平台，再调用共享同步服务。

## 5. 定时任务与数据迁移

新增 `ScheduledTaskCode.GOODSHORT_ORDER_SYNC`。新增 Flyway 迁移 `V16__goodshort_order_scheduled_sync.sql`，只插入一条固定任务配置：

```text
task_code       GOODSHORT_ORDER_SYNC
title           GoodShort 订单同步
description     每隔1分钟同步最近3天的GoodShort订单
cycle_type      INTERVAL_MINUTES
interval_value  1
interval_minutes 5（旧字段兼容值；调度不使用它计算本任务周期）
enabled         true
next_run_at     当前时间加1分钟
```

现有 V1 迁移为兼容字段 `interval_minutes` 设置了最少 5 分钟的检查约束。实际调度由 `cycle_type` 和 `interval_value` 经 `ScheduledTaskScheduleCalculator` 计算，因此 V16 保存 `interval_value=1` 和合法的兼容值 `interval_minutes=5` 即可每分钟执行，不需要扩大既有 schema 约束范围。

不修改已发布的 `V1__kasi_promotion.sql`。任务页会自动显示新任务；管理端 TypeScript 联合类型 `ScheduledTaskCode` 增加 `GOODSHORT_ORDER_SYNC`，使编辑和启停请求可以安全传递该编码。

调度分发新增 `case GOODSHORT_ORDER_SYNC`。窗口只在成功领取租约后计算：

```text
endDate   = LocalDateTime.now(clock)
startDate = endDate.minusDays(3)
```

默认租约仍沿用 `ScheduledTaskProperties.leaseDuration` 的 2 分钟配置。实施前必须以测试和实际分页规模确认单次同步通常可在 2 分钟内完成；若不能满足，须先单独调整租约策略并复核多实例重复拉取风险。即便出现重叠拉取，订单唯一键仍防止重复入库，但不能将幂等当作长期的并发控制替代品。

## 6. 失败、并发与运维语义

- 多实例仅能由一个实例领取同一条任务记录的当前租约；未领取时不执行、不推进下一次执行时间。
- GoodShort 平台记录不存在时，自动任务不访问远端，按现有调度器完成本次任务并推进周期；这允许先发布代码和迁移，再由管理员完成平台接入。
- 平台未具备 `ORDER_SYNC` 能力、凭据不可用、网络超时或远端拒绝时，共享服务沿用现有异常；分发器记录不含密钥、订单原始 payload 的错误日志，通用调度器仍按现状推进下一次执行时间。
- 不新增失败重试、运行历史或告警页面。管理员可在订单页查看结果，并可用现有手动同步接口补拉。
- 自动任务只处理最近 3 天。超过窗口的补数必须由管理员以手动接口传入明确的起止时间，避免无边界全量拉取。

## 7. 不在本次范围

- 不变更 CPS 五项费率、佣金公式、历史快照、退款冲销和 trackingNo 归因算法。
- 不变更订单表结构、用户月度只读汇总、正式账单、钱包、提现或未归因订单人工归属。
- 不新增第二个 Spring `@Scheduled`、第二张任务表、第二个任务管理页面、检查点表或消息队列。
- 不涉及 CapCut、CPA、CPM 或通用项目管理。

## 8. 验收与测试

先写失败测试，再实现生产代码。最低覆盖：

1. Flyway 执行到 V16 后，`system_scheduled_task` 存在启用的 `GOODSHORT_ORDER_SYNC`，默认周期为 1 分钟。
2. 到期任务被当前 worker 成功领取后，使用固定 `Clock` 调用共享订单同步服务，窗口精确为 `now.minusDays(3)` 到 `now`，并将下一次执行时间推进 1 分钟。
3. 未领取租约时，不调用订单同步服务，也不更新下一次执行时间。
4. GoodShort 平台不存在或不具备订单同步能力时，不访问远端；同一批次中的其他任务不受影响。
5. 共享服务保持现有分页、`fetched/inserted/updated/unattributed` 汇总与 `PromotionOrderService.upsert(...)` 调用语义。
6. 管理端定时任务类型接受并渲染新任务编码，编辑或启停仍使用原有接口。

实现完成后的最小验证：后端目标测试、管理端相关 Vitest、Java 25 编译、Flyway 迁移测试，以及 `git diff --check`。

## 9. 影响、迁移与回滚

影响文件预计包括 `scheduledtask`、`promotion`、`db/migration`、`kasi-admin-web` 的定时任务类型与测试，以及当前行为文档。用户端不需要变更，因为订单查询接口和数据模型不变。

迁移为仅插入新任务记录，不迁移或重算任何历史订单。生产回滚时先在任务页禁用 `GOODSHORT_ORDER_SYNC`，再回退应用版本；已同步订单保留，因为它们与手动同步写入的数据具有同一幂等和数据语义。不得删除订单数据作为回滚动作。

## 10. 实施顺序

1. 编写迁移、共享同步服务与调度分发的失败测试。
2. 在最小范围内抽取共享同步服务，使手动入口保持原 HTTP 契约。
3. 新增任务编码、V16 迁移和调度分支。
4. 更新管理端任务编码类型和相关渲染测试。
5. 更新 README、AGENTS、缺口清单及必要的架构决策记录，执行完整验证。
