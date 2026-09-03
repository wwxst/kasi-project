# GoodShort 免费剧集同步管理端设计

日期：2026-08-29

状态：已确认，已实施

## 1. 当前情况

管理后台 `/drama/catalog` 已有短剧目录查询、详情抽屉、本地上下架、目录全量/增量同步和目录同步状态。后端已经新增免费剧集同步任务、永久视频 URL、自动调度及以下管理员接口，但管理端尚未接入：

```text
POST /api/admin/drama/catalog/{id}/contents/sync
POST /api/admin/drama/catalog/contents/sync
POST /api/admin/drama/catalog/contents/sync/all
GET  /api/admin/drama/catalog/{id}/contents/sync/status
```

GoodShort 当前没有收费剧集列表或收费资源接口，因此管理端只操作免费剧集同步，不展示或暗示收费剧集可以同步。

## 2. 目标与非目标

### 2.1 目标

- 管理员可以从目录表格对单部短剧发起免费剧集同步。
- 管理员可以勾选不超过 100 部短剧批量发起同步。
- 管理员可以按平台和可选语言同步全部在线短剧。
- “同步全部”默认处理所有在线短剧，并可切换为仅补齐缺失视频地址。
- 短剧详情展示该短剧的剧集同步状态、统计和最近错误。
- 请求只创建任务，不让页面等待 GoodShort 完成。
- 保留现有目录同步功能和术语，不把剧集补齐称为“增量同步”。

### 2.2 非目标

- 不新增独立剧集同步页面或菜单。
- 不修改目录全量/增量同步弹窗和目录同步状态抽屉的业务含义。
- 不展示视频 URL、连接 ID、PID、KEY、租约所有者或内部任务调度字段。
- 不支持收费剧集同步、单集编辑、删除或手工填写视频地址。
- 不在管理端直接调用 GoodShort。

## 3. 方案比较

### 3.1 独立页面

新建剧集任务列表页，信息最完整，但当前后端没有全局剧集任务分页接口。为此新增页面会要求扩展后端契约，超出本次范围，不采用。

### 3.2 复用目录同步弹窗

把“剧集同步”塞入现有“同步短剧目录”弹窗，文件改动较少，但会同时出现目录的 `FULL/INCREMENTAL` 和剧集的 `missingOnly`，容易让管理员误以为两类增量含义相同，不采用。

### 3.3 独立剧集同步弹窗

在现有短剧目录页新增“同步剧集”弹窗，目录同步和剧集同步分别处理各自契约。单部与勾选批量使用直接操作，全平台同步使用弹窗。该方案边界清楚且不需要新增后端接口，采用此方案。

## 4. 页面入口

现有工具栏保留：

```text
同步状态
同步目录
```

新增：

```text
同步所选剧集
同步剧集
```

- `同步所选剧集` 仅在有勾选项时可用，按钮文字显示已选数量，例如“同步所选剧集（3）”。
- `同步剧集` 打开全平台剧集同步弹窗。
- 表格操作列在“详情”“上架/下架”之外增加“同步剧集”，用于单部同步。
- 表格启用行选择；最多选择 100 部。筛选条件、页码或每页数量变化时清空选择，避免提交不可见的旧选择。
- 成功提交单部或勾选批量后保留当前筛选和分页，只清空已提交的勾选项并显示结果。

## 5. 全平台剧集同步弹窗

弹窗标题为“同步免费剧集”，字段如下：

```text
短剧平台    必填，只展示可用的短剧平台
语言        可选，单选；留空表示该平台全部已同步语言
同步范围    必填，默认“同步全部在线短剧”
```

同步范围使用分段控件：

```text
同步全部在线短剧     missingOnly=false
仅补齐缺失视频地址   missingOnly=true
```

“仅补齐缺失视频地址”包括没有任何剧集记录，或至少一集 `content_url` 为空的在线短剧。

弹窗显示业务边界提示“当前仅同步 GoodShort 免费剧集”。提交按钮为“提交任务”，提交中禁用关闭外的重复操作。

请求示例：

```json
{
  "providerId": 1,
  "language": "ENGLISH",
  "missingOnly": false
}
```

成功后关闭弹窗并展示本次结果：匹配数量、排队数量、运行中跳过数量和无效数量。接口不返回全部任务明细，页面不伪造任务列表。

## 6. 单部与勾选批量

### 6.1 单部同步

点击行操作“同步剧集”后弹出确认框，确认文案明确只同步免费剧集。确认后调用：

```text
POST /api/admin/drama/catalog/{id}/contents/sync
```

成功时提示“免费剧集同步任务已提交”。若当前详情抽屉展示同一短剧，则立即刷新该短剧的剧集任务状态。

### 6.2 勾选批量

点击“同步所选剧集”后显示确认框，确认数量和免费剧集边界，再调用：

```text
POST /api/admin/drama/catalog/contents/sync
{
  "dramaIds": [1, 2, 3]
}
```

成功后展示请求数量、排队数量、运行中跳过数量和无效数量。批量最多 100 个 ID，前端限制和后端 Jakarta Validation 同时生效。

## 7. 详情中的任务状态

短剧详情抽屉在“基本信息”和“剧集”之间新增“剧集同步”区段。打开详情时，在加载短剧详情之外调用：

```text
GET /api/admin/drama/catalog/{id}/contents/sync/status
```

显示：

```text
任务状态       等待执行/运行中/同步成功/同步失败
请求时间       requestedAt
下次执行时间   nextRunAt
重试次数       retryCount
本次统计       获取、增加、更新
最近错误       lastErrorCode + lastErrorMessage
```

错误码 `6017` 表示该短剧尚无剧集同步任务，区段显示“尚未提交剧集同步任务”，不弹全局错误提示。`REQUESTED` 或 `RUNNING` 状态每 3 秒刷新一次；任务进入 `SUCCESS` 或 `FAILED`、抽屉关闭或连续刷新 60 秒后停止。手动刷新按钮可重新查询状态。

任务成功后重新读取短剧详情，使“剧集”表格显示新同步的免费剧集。详情剧集表只展示已有元数据，不展示永久视频 URL。

## 8. API 和类型

`src/features/drama/dramaCatalogTypes.ts` 新增：

```text
DramaContentSyncStatus
DramaContentSyncTask
DramaContentSyncBatchResult
RequestAllDramaContentSync
```

`src/features/drama/dramaCatalogApi.ts` 新增：

```text
requestDramaContentSync(id)
requestDramaContentBatchSync(dramaIds)
requestAllDramaContentSync(request)
getDramaContentSyncStatus(id)
```

页面继续通过现有 `httpClient` 和 `unwrapApiResponse` 处理统一响应，不新增状态管理库。页面状态保留在 `DramaCatalogPage` 及聚焦子组件中。

## 9. 组件边界

新增聚焦组件：

```text
DramaContentSyncModal.tsx
  负责全平台同步表单和结果提交

DramaContentSyncSection.tsx
  负责单部任务状态查询、轮询、刷新和展示
```

`DramaCatalogPage.tsx` 只负责表格选择状态、打开弹窗、调用单部/勾选批量命令和刷新当前详情。现有 `DramaSyncModal` 与 `DramaSyncStatusDrawer` 不改名，继续只表示目录同步。

不新增嵌套卡片；详情同步区段沿用现有无边框 section 布局。按钮继续使用 Lucide 图标和 Ant Design Tooltip/Popconfirm。

## 10. 错误处理

- HTTP 401 继续由共享 Axios 层清理会话并返回登录页。
- HTTP 403、503、业务错误和网络错误保留当前会话，由当前操作显示错误提示。
- `6016` 显示“该短剧的剧集同步任务正在执行”，不重复提交。
- `6017` 仅在状态读取时转换为“尚未提交剧集同步任务”。
- 批量响应中的 `skippedCount` 和 `invalidCount` 作为正常结果展示，不转换为请求失败。
- 单部、批量和全部同步失败后保留当前选择或表单值，便于重试。

## 11. 测试与验收

API 层测试覆盖四个路径、请求体和响应类型。

页面测试至少覆盖：

```text
单部同步确认并提交
勾选按钮启停、数量显示、最多 100 部和筛选后清空
同步全部默认 missingOnly=false
切换“仅补齐缺失视频地址”后 missingOnly=true
语言留空和指定语言请求
批量与全部同步统计结果
详情无任务、等待执行、运行中、成功和失败状态
运行中轮询，终态或关闭抽屉停止轮询
任务成功后刷新详情剧集
6016、6017、401、403、503 和普通业务错误
现有目录同步入口和行为不回归
```

完成后运行：

```powershell
pnpm test
pnpm lint
pnpm typecheck
pnpm format:check
pnpm build
git diff --check
```

## 12. 当前与规划分离

当前已实现：后端免费剧集同步任务、自动调度、永久 URL、单部/勾选批量/全平台管理员接口；管理端短剧目录页已接入单部同步、最多 100 部勾选批量同步、全部在线/仅补齐缺失视频地址弹窗、单部任务状态区段、3 秒轮询与 60 秒停止边界，并保留原有目录全量/增量同步、目录同步状态、上下架和详情元数据能力。API、弹窗、状态区段和页面集成测试均已实现。

本设计已实施，不再保留待实施的管理端功能项。

明确缺口：收费剧集同步仍没有 GoodShort 数据来源，不属于管理端待实现能力。
