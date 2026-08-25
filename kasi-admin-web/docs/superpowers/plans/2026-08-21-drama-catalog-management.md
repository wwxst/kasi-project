# 短剧目录管理前端 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Kasi 管理后台交付 GoodShort 短剧目录查询、详情、同步、同步状态和本地上下架页面。

**Architecture:** 按现有 feature/page 分层新增 drama 领域模块，API 层完整映射后端五个管理员目录接口，页面层使用 `PageContainer + ProTable` 并以抽屉和弹窗承载详情与任务操作。平台选项复用 provider API，页面状态保持局部，不增加全局状态或新依赖。

**Tech Stack:** React 19、TypeScript 6、Vite 8、Ant Design 6、Ant Design Pro Components、Axios、React Router 7、Vitest、React Testing Library、MSW、pnpm。

---

## 固定契约

- 只接入 GoodShort；平台选项来自 `GET /api/admin/drama/providers`，仅保留启用且具备 `FULL_DRAMA_SYNC` 或 `INCREMENTAL_DRAMA_SYNC` 能力的平台。
- 页面路径为 `/drama/catalog`，普通管理员和超级管理员都可访问。
- 本地状态仅使用 `DRAFT/PUBLISHED/OFFLINE`；同步类型仅使用 `FULL/INCREMENTAL`；同步状态仅使用 `IDLE/REQUESTED/RUNNING/SUCCESS/FAILED`。
- 列表和详情不显示 connectionId、partnerId、apiKey、credential、leaseOwner 或 leaseUntil。
- 同步请求只提交任务；成功后刷新状态，不承诺目录立即出现新数据。

### Task 1: 目录 API 和类型契约

**Files:**

- Create: `src/features/drama/dramaCatalogTypes.ts`
- Create: `src/features/drama/dramaCatalogApi.ts`
- Test: `src/features/drama/dramaCatalogApi.test.ts`

- [ ] **Step 1: 写失败的 API 映射测试**

用 MSW 注册五个目录端点，调用计划导出的 `listDramaCatalog`、`getDramaCatalogDetail`、`requestDramaCatalogSync`、`listDramaSyncStatuses`、`updateDramaLocalStatus`，断言分页查询参数、路径和两个请求体。另让一个响应返回非零业务码，断言沿用 `unwrapApiResponse` 抛出的消息。

- [ ] **Step 2: 运行测试确认 RED**

Run: `pnpm test src/features/drama/dramaCatalogApi.test.ts`

Expected: FAIL，原因是 `dramaCatalogApi` 模块尚不存在。

- [ ] **Step 3: 定义精确类型**

在 `dramaCatalogTypes.ts` 中定义：

```ts
export type DramaLocalStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE'
export type DramaSyncType = 'FULL' | 'INCREMENTAL'
export type DramaSyncStatus =
  'IDLE' | 'REQUESTED' | 'RUNNING' | 'SUCCESS' | 'FAILED'

export interface DramaCatalogPageQuery {
  page: number
  size: number
  providerId?: number
  title?: string
  language?: string
  remoteShowStatus?: string
  localStatus?: DramaLocalStatus
}

export interface DramaCatalogListItem {
  id: number
  externalDramaId: string
  title: string | null
  originalTitle: string | null
  coverUrl: string | null
  language: string | null
  dramaType: string | null
  remoteShowStatus: string | null
  localStatus: DramaLocalStatus
  remoteUpdatedAt: string | null
  lastSeenAt: string | null
  updatedAt: string | null
}
```

继续定义 `DramaCatalogPage`、`DramaContent`、`DramaCatalogDetail`、`DramaSyncTask`、`RequestDramaSync` 和 `UpdateDramaLocalStatusRequest`，字段与后端 VO 一一对应。

- [ ] **Step 4: 实现最小 API 模块**

基准路径固定为 `/api/admin/drama/catalog`：

```ts
export async function listDramaCatalog(query: DramaCatalogPageQuery) {
  const response = await httpClient.get<ApiResponse<DramaCatalogPage>>(
    basePath,
    {
      params: query,
    },
  )
  return unwrapApiResponse(response.data)
}
```

其余四个函数使用对应 `GET/POST/PATCH` 路径并统一解包，不在 API 层添加 UI 文案或状态转换。

- [ ] **Step 5: 运行测试确认 GREEN**

Run: `pnpm test src/features/drama/dramaCatalogApi.test.ts`

Expected: 该测试文件全部通过，MSW 无未处理请求。

- [ ] **Step 6: 提交 API 层**

```powershell
git add src/features/drama
git commit -m "feat: add drama catalog api client"
```

### Task 2: 目录表格、详情和本地状态

**Files:**

- Create: `src/pages/drama/DramaCatalogPage.tsx`
- Create: `src/pages/drama/drama-catalog-page.css`
- Test: `src/pages/drama/DramaCatalogPage.test.tsx`

- [ ] **Step 1: 写列表和详情失败测试**

Mock `ProTable` 的方式沿用 `MediaAccountFilingPage.test.tsx`。MSW 返回一条 `PUBLISHED` 短剧和包含两集的详情。断言页面标题、平台名称、短剧名称、详情按钮、详情身份区、简介和两条剧集记录可见，并断言列表请求包含 `page=1&size=20`。

- [ ] **Step 2: 运行测试确认 RED**

Run: `pnpm test src/pages/drama/DramaCatalogPage.test.tsx`

Expected: FAIL，原因是页面模块尚不存在。

- [ ] **Step 3: 实现 ProTable 和详情抽屉**

页面加载 `listProviders()`，过滤为可同步的平台并创建 provider `valueEnum`。`loadPage` 将 ProTable 参数转换为 `DramaCatalogPageQuery`。列按设计文档设置固定宽度和横向滚动；封面容器固定为 48x68px，失败时显示 `Clapperboard` 图标。详情按钮调用 `getDramaCatalogDetail(id)` 并打开 880px 右侧抽屉；抽屉使用 `Descriptions` 和小尺寸 `Table` 展示元数据与剧集。

- [ ] **Step 4: 运行列表和详情测试确认 GREEN**

Run: `pnpm test src/pages/drama/DramaCatalogPage.test.tsx`

Expected: 列表和详情场景通过。

- [ ] **Step 5: 写上下架失败测试**

让列表记录为 `PUBLISHED`，点击“下架”和确认按钮，断言 PATCH 请求体为 `{ localStatus: 'OFFLINE' }`；再覆盖 `DRAFT` 的“上架”请求为 `{ localStatus: 'PUBLISHED' }`。响应成功后断言列表 reload 被调用并显示成功消息。

- [ ] **Step 6: 实现本地状态操作并确认 GREEN**

使用 `Popconfirm` 做二次确认。操作目标由当前状态计算：`PUBLISHED -> OFFLINE`，其他状态 `-> PUBLISHED`。调用 `updateDramaLocalStatus` 后刷新 ProTable；若详情抽屉正在展示同一记录，同时替换详情数据。

Run: `pnpm test src/pages/drama/DramaCatalogPage.test.tsx`

Expected: 页面测试全部通过。

- [ ] **Step 7: 提交目录浏览能力**

```powershell
git add src/pages/drama
git commit -m "feat: add drama catalog management page"
```

### Task 3: 同步提交与状态查看

**Files:**

- Modify: `src/pages/drama/DramaCatalogPage.tsx`
- Modify: `src/pages/drama/drama-catalog-page.css`
- Test: `src/pages/drama/DramaCatalogPage.test.tsx`

- [ ] **Step 1: 写同步弹窗失败测试**

点击“同步目录”，断言平台、同步方式、语言字段可见，默认方式为 `INCREMENTAL`、默认语言为 `ENGLISH`。选择 GoodShort 并提交后，断言请求体为：

```json
{
  "providerId": 1,
  "syncType": "INCREMENTAL",
  "languages": ["ENGLISH"]
}
```

断言成功后弹窗关闭并出现“同步任务已提交”。

- [ ] **Step 2: 运行测试确认 RED**

Run: `pnpm test src/pages/drama/DramaCatalogPage.test.tsx`

Expected: FAIL，页面尚无“同步目录”按钮。

- [ ] **Step 3: 实现同步弹窗并确认 GREEN**

在 ProTable 工具栏加入带 `RefreshCw` 图标的“同步目录”按钮。使用 `Modal + Form`，平台必选、同步方式用 segmented control、语言用多选 Select。提交成功后记录最近 providerId，关闭弹窗并打开同步状态抽屉。

- [ ] **Step 4: 写同步状态失败测试**

打开“同步状态”，选择 GoodShort，断言请求含 `providerId=1`，并展示语言、同步方式、状态、页码、六项统计、最近成功时间和错误信息。点击刷新按钮后断言接口再次调用。

- [ ] **Step 5: 实现状态抽屉并确认 GREEN**

使用 760px 右侧 Drawer。顶部为平台 Select 和刷新图标按钮，下方用小尺寸 Table 展示状态；统计合并为紧凑的两行数值，错误信息允许换行但不溢出。`REQUESTED/RUNNING` 使用 processing Tag，`SUCCESS` 使用 success，`FAILED` 使用 error。

Run: `pnpm test src/pages/drama/DramaCatalogPage.test.tsx`

Expected: 同步提交与状态查看场景全部通过。

- [ ] **Step 6: 提交同步交互**

```powershell
git add src/pages/drama
git commit -m "feat: manage drama catalog sync tasks"
```

### Task 4: 路由、导航和应用级覆盖

**Files:**

- Modify: `src/router/AppRouter.tsx`
- Modify: `src/layouts/AdminLayout.tsx`
- Modify: `src/App.test.tsx`

- [ ] **Step 1: 写路由和菜单失败测试**

在 `App.test.tsx` 为已登录普通管理员设置路径 `/drama/catalog`，注册平台和目录列表 MSW 响应，断言“短剧管理”“短剧目录”和页面标题可见，且未跳转到 dashboard。

- [ ] **Step 2: 运行测试确认 RED**

Run: `pnpm test src/App.test.tsx`

Expected: FAIL，未知路由当前重定向到 `/dashboard`。

- [ ] **Step 3: 添加懒加载路由和一级菜单**

在 `AppRouter.tsx` 懒加载 `DramaCatalogPage` 并注册 `/drama/catalog`。在 `AdminLayout.tsx` 新增 `drama-management` 父菜单和 `/drama/catalog` 子项，复用 `Clapperboard` 图标，并将父菜单加入 `defaultOpenKeys`。

- [ ] **Step 4: 运行应用级测试确认 GREEN**

Run: `pnpm test src/App.test.tsx src/pages/drama/DramaCatalogPage.test.tsx`

Expected: 新路由测试及原有应用测试全部通过。

- [ ] **Step 5: 提交路由导航**

```powershell
git add src/router/AppRouter.tsx src/layouts/AdminLayout.tsx src/App.test.tsx
git commit -m "feat: add drama catalog navigation"
```

### Task 5: 文档、全量验证和交付

**Files:**

- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-08-21-drama-catalog-management-design.md`
- Test: all frontend tests and build checks

- [ ] **Step 1: 更新当前行为文档**

在 README 当前状态中把短剧目录同步从“后续模块”调整为已实现管理页面，记录路由、五个 API、同步只排队、GoodShort 范围和敏感字段不展示。将设计文档状态改为“已实施并验证”，但仅在所有验证命令成功后执行。

- [ ] **Step 2: 运行完整验证**

```powershell
pnpm test
pnpm typecheck
pnpm lint
pnpm format:check
pnpm build
git diff --check
```

Expected: 所有命令退出码为 0，Vitest 显示 0 个失败测试，格式和构建无错误。

- [ ] **Step 3: 检查范围并提交文档**

运行 `git status --short`、`git diff --stat` 和 `git diff --check`，确认没有 `dist`、凭据或无关文件。然后执行：

```powershell
git add README.md docs/superpowers/specs/2026-08-21-drama-catalog-management-design.md
git commit -m "docs: document drama catalog management"
```

## 自检结果

- 规格覆盖：Task 1 覆盖五个 API；Task 2 覆盖列表、详情和上下架；Task 3 覆盖同步与状态；Task 4 覆盖路由导航；Task 5 覆盖文档和全量验证。
- 占位扫描：任务均包含精确文件、失败原因、实现边界、命令和预期结果，没有 `TBD`、`TODO` 或“类似上一任务”的占位语句。
- 类型一致性：`DramaLocalStatus`、`DramaSyncType`、`DramaSyncStatus`、分页字段和后端 DTO/VO 一致。
- 范围检查：不实现其他平台、推广链接、分佣、订单、导出或转化分析。
