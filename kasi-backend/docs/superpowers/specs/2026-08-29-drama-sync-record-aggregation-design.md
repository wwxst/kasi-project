# 短剧与剧集同步记录聚合设计

- 日期：2026-08-29
- 状态：已批准实施
- 范围：管理端同步中心的短剧同步页、剧集同步页，以及支撑展示的只读聚合接口

## 1. 目标

同步中心继续保留两个独立页面：

```text
/drama/sync/catalog    短剧同步
/drama/sync/content    剧集同步
```

两个页面不混合数据，但各自的同步记录表统一展示：

```text
创建时间 | 触发方式 | 任务类型 | 状态 | 新增数 | 更新数 | 总处理数 | 操作
```

同一次触发产生的多语言或多短剧子任务在列表中聚合为一条记录；语言、短剧、子任务状态和错误进入详情。

## 2. 边界

当前短剧任务按 `(connection_id, sync_type, language)` 保存，剧集任务按 `drama_id` 保存。本次不改变这两个任务模型，也不改变 worker 的扫描、领取、租约、断点、重试和终态更新。

本次列表表示当前可查询的同步运行记录，不建设独立的历史审计系统。旧任务仍保留，新的展示运行只作为查询分组元数据。

## 3. 独立展示运行元数据

新增 `drama_sync_display_run` 和 `drama_sync_display_run_item` 两张表，不向现有任务表加列：

```text
drama_sync_display_run
  id               UUID 字符串
  provider_id      平台 ID
  parent_run_id    目录运行触发的自动剧集运行，可为空
  domain           CATALOG / CONTENT
  task_type        FULL / INCREMENTAL / SINGLE / BATCH / ALL / MISSING / CATALOG_AUTO
  trigger_source   MANUAL / SCHEDULED
  requested_at     本次触发时间

drama_sync_display_run_item
  run_id           展示运行 ID
  task_domain      CATALOG / CONTENT
  task_id          现有 checkpoint 或剧集任务 ID
```

展示表只承担分组、来源和详情关联，不参与现有任务的到期扫描、领取条件、分页断点、租约或状态写回。

## 4. 触发与任务类型

手动目录同步写入 `MANUAL`，固定目录增量调度写入 `SCHEDULED`。目录 worker 自动排队的剧集任务创建 `CATALOG_AUTO` 运行并继承目录运行的触发方式。剧集单部、勾选批量、全部和仅缺失请求分别使用 `SINGLE`、`BATCH`、`ALL`、`MISSING`。

## 5. 聚合规则

列表按展示运行 ID 聚合并按 `requested_at` 倒序展示：

```text
新增数      子任务 inserted_count 之和
更新数      子任务 updated_count 之和
总处理数    子任务 total_fetched 之和
```

`total_fetched` 保持上游实际处理量语义。

```text
WAITING          所有子任务都尚未开始
RUNNING          任一子任务执行中，或终态与待执行状态并存
SUCCESS          所有子任务成功
PARTIAL_FAILED   所有子任务已结束，且成功和失败并存
FAILED           所有子任务失败
```

## 6. 接口与页面

保留现有原始状态接口。同步中心增加分别属于两个页面的记录和详情接口：

```text
GET /api/admin/drama/catalog/sync/records?providerId={id}
GET /api/admin/drama/catalog/sync/records/{runId}?providerId={id}
GET /api/admin/drama/catalog/contents/sync/records?providerId={id}
GET /api/admin/drama/catalog/contents/sync/records/{runId}?providerId={id}
```

短剧详情返回语言 checkpoint；剧集详情返回短剧标题、语言和剧集任务。失败重试复用现有目录同步和剧集批量同步入口，页面不增加第二套 worker。

## 7. 验证

- 后端测试覆盖展示运行持久化、同一次触发共享运行 ID、统计求和、五种状态、平台隔离和详情。
- 控制器测试覆盖四个只读接口及管理员权限。
- 前端测试覆盖两个页面的数据隔离、统一八列、详情子任务和失败重试。
- 使用 Java 25、前端 Vitest、类型检查、构建和本次文件的 `git diff --check`。
