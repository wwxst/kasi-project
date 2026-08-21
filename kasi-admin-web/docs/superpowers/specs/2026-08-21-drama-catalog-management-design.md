# 短剧目录管理前端设计

日期：2026-08-21

状态：已确认，待实施

## 1. 目标

在现有 Kasi 管理后台中增加 GoodShort 短剧目录管理页面，让管理员可以查询已同步短剧、查看剧集详情、触发全量或增量同步、查看同步状态，并维护短剧的本地上架状态。

本阶段只对接现有 GoodShort 后端接口，不增加其他短剧平台、不修改后端同步逻辑，也不实现推广链接、分佣、订单或转化分析。

## 2. 导航与路由

- 左侧导航新增一级菜单“短剧管理”，使用 `Clapperboard` 图标。
- 一级菜单下新增“短剧目录”，路由为 `/drama/catalog`。
- 普通管理员和超级管理员均可访问，与后端 `ROLE_ADMIN` 权限保持一致。
- 页面通过现有 React Router 懒加载，不改变其他路由或权限规则。

## 3. 页面布局

页面沿用现有 Ant Design Pro 管理页：`PageContainer + ProTable`。界面以高信息密度、可扫描和重复操作效率为优先，不引入营销式卡片或独立视觉体系。

### 3.1 查询表格

筛选项：

- 短剧平台
- 短剧名称
- 语言
- 远端状态
- 本地状态

表格列：

- 封面
- 短剧名称
- 外部短剧 ID
- 语言
- 类型
- 远端状态
- 本地状态
- 远端更新时间
- 操作

封面缺失或加载失败时显示稳定尺寸的占位区域，避免行高变化。远端状态属于 GoodShort 原始值，前端不猜测业务含义，按原值展示。中文状态使用 Tag 区分，时间统一显示到分钟。

每行提供“详情”和本地状态操作。`DRAFT`、`OFFLINE` 可上架为 `PUBLISHED`，`PUBLISHED` 可下架为 `OFFLINE`；状态变更必须二次确认，成功后刷新列表和当前详情。

### 3.2 短剧详情抽屉

右侧抽屉宽度约 880px，内容包含：

- 封面、标题、原始标题和外部短剧 ID
- 简介、语言、类型、远端状态、本地状态
- 远端更新时间、最近同步可见时间、本地创建和更新时间
- 剧集表格：序号、标题、外部剧集 ID、免费标记、时长、远端更新时间

详情只展示后端 `DramaDetailVO` 字段，不展示平台连接 ID、PID、密钥、凭据或任务租约。

### 3.3 同步目录弹窗

工具栏提供主按钮“同步目录”。弹窗字段：

- 短剧平台：必选，仅列出具备目录同步能力的已启用平台；当前实际为 GoodShort。
- 同步方式：`INCREMENTAL`（增量）或 `FULL`（全量），默认增量。
- 语言：可多选，默认 `ENGLISH`；留空时由后端处理已配置语言。

提交调用 `POST /api/admin/drama/catalog/sync`。接口只排队任务，不等待远端同步完成；成功后关闭弹窗、提示“同步任务已提交”，刷新同步状态并打开状态抽屉。

### 3.4 同步状态抽屉

工具栏提供“同步状态”按钮。打开时先选择或沿用最近使用的平台，然后调用 `GET /api/admin/drama/catalog/sync/status?providerId=...`。

状态表按同步方式和语言展示：

- 状态、当前页码
- 拉取、写入、新增、更新、跳过、异常数量
- 最近成功时间
- 最近错误码和错误信息

`REQUESTED/RUNNING` 显示处理中状态；抽屉提供手动刷新，不做高频自动轮询。

## 4. 前端模块边界

- `src/features/drama/dramaCatalogTypes.ts`：后端 VO、查询和请求类型。
- `src/features/drama/dramaCatalogApi.ts`：五个目录 API 的 Axios 映射与统一响应解包。
- `src/pages/drama/DramaCatalogPage.tsx`：表格、详情抽屉、同步弹窗、状态抽屉和交互状态。
- `src/pages/drama/drama-catalog-page.css`：页面专属紧凑布局、封面和响应式规则。
- `src/router/AppRouter.tsx`、`src/layouts/AdminLayout.tsx`：懒加载路由和菜单入口。

不引入新的全局状态；页面级状态由 React state 管理，表格刷新使用现有 `ActionType`。

## 5. API 映射

| 前端动作     | 方法与路径                                                 |
| ------------ | ---------------------------------------------------------- |
| 查询目录     | `GET /api/admin/drama/catalog`                             |
| 查询详情     | `GET /api/admin/drama/catalog/{id}`                        |
| 提交同步     | `POST /api/admin/drama/catalog/sync`                       |
| 查询同步状态 | `GET /api/admin/drama/catalog/sync/status?providerId={id}` |
| 修改本地状态 | `PATCH /api/admin/drama/catalog/{id}/status`               |
| 加载平台选项 | `GET /api/admin/drama/providers`                           |

分页参数从 ProTable 的 `current/pageSize` 映射为后端 `page/size`。空字符串筛选不发送。所有业务错误继续由现有 `unwrapApiResponse` 转换为 `Error`，页面使用 Ant Design message 展示。

## 6. 测试与验收

- API 测试使用 MSW 验证路径、查询参数、请求体、业务错误和响应解包。
- 页面测试覆盖列表加载、详情抽屉、同步任务提交、同步状态、上架/下架、错误反馈。
- 应用级测试覆盖 `/drama/catalog` 路由和“短剧管理 / 短剧目录”菜单可见性。
- 完成后运行 `pnpm test`、`pnpm typecheck`、`pnpm lint`、`pnpm format:check`、`pnpm build`。

## 7. 后续范围

目录管理完成后，后端业务顺序仍为分佣规则、推广创建、订单归因和佣金。当前页面不提前增加这些尚未实现的入口。
