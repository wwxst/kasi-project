# 用户端账号报白页面设计

## 目标

在 Kasi 用户端增加“账号报白”页面，使用 TDesign React 的数据表格和右侧抽屉完成推广用户媒体账号的查看、新增、编辑和失败重试。用户端只展示报白状态，不提供启用或禁用账号操作。

## 范围

包含：

- `/account/filing` 受保护路由；
- 账号列表、关键词搜索、选择、分页和导出；
- 新增媒体账号并提交 GoodShort 报白；
- 查看账号详情；
- 编辑账号资料；
- 报备失败后的重新报备；
- 桌面端优先，320px 以上窄屏不重叠、不裁切。

不包含：

- 启用/禁用账号；
- 物理删除或账号转让；
- 推广链接、订单、佣金和结算；
- 管理员接口或管理员页面。

## 技术与边界

- 使用 TDesign React 的 `Table`、`Drawer`、`Form`、`FormItem`、`Input`、`Select`、`Tag`、`Button` 等组件。
- 图标使用 TDesign Icons React。
- API、类型和查询状态放在 `features/promotion` 下，页面只负责组合和交互。
- 所有请求经过 `shared/api/httpClient.ts`，只调用 `/api/user/promotion/media-accounts/**`。
- 首期只有 GoodShort，前端使用当前初始化的平台 ID；后续平台接入时再扩展平台发现接口。

## 页面结构

页面标题为“账号报白”。工具栏包含新增账号、导出、已选数量和账号搜索。表格使用 TDesign Table，列为：

1. 复选框；
2. 媒体平台；
3. 账号名称；
4. GoodShort（报白状态）；
5. 操作（仅详情）。

报白状态来自账号 `filings` 中 GoodShort 记录：`PENDING` 显示“审核中”，`APPROVED` 显示“已加白”，`FAILED` 显示“已失败”。没有记录时按“审核中”展示，确保列表只出现三种状态。状态使用语义化 Tag，状态本身不可编辑。

## 抽屉交互

详情抽屉展示平台、媒体平台、账号 ID、账号名称、主页链接、报白状态、报备时间、最近查询时间和失败原因。抽屉底部提供“编辑”；失败状态额外提供“重试报白”。

新增抽屉字段：报白平台（GoodShort）、媒体平台（TikTok/Facebook/YouTube/Instagram）、账号 ID（必填）、账号名称（可选）、主页链接（可选且填写时必须为 HTTPS）。提交调用 `POST /api/user/promotion/media-accounts`，其中 `providerId` 使用首期 GoodShort 平台 ID。

编辑抽屉调用 `PUT /api/user/promotion/media-accounts/{id}`。已加白时锁定媒体平台和账号 ID；审核中或失败时允许修改全部账号资料字段。编辑成功后刷新列表和详情。

失败重试调用 `POST /api/user/promotion/media-accounts/{id}/filings/{providerId}`，成功后刷新列表和详情。用户端不调用现有 `PATCH .../{id}/status` 接口。

## 错误与加载状态

- 首次加载显示表格骨架或加载状态；空列表显示 TDesign 空状态和“新增账号”入口。
- 新增、编辑、重试按钮在请求期间进入 loading，防止重复提交。
- `ApiError` 的业务消息显示在抽屉内的 Alert；503、1007 保留会话并提供重试；401 由现有 HTTP 层清理会话并回到登录页。
- 表格刷新失败不清空已有数据，保留重试入口。

## 测试与验证

使用 MSW 覆盖以下行为：

- 账号报白路由在有效会话下渲染表格；
- 列表正确映射平台、账号资料和三种报白状态；
- 新增提交正确 payload；
- 已加白锁定媒体平台和账号 ID，失败状态允许编辑；
- 失败状态显示并调用重试接口；
- 401、503/1007 错误遵循现有会话规则。

完成后运行 `pnpm test`、`pnpm typecheck`、`pnpm lint`、`pnpm format:check` 和 `pnpm build`，并在桌面及移动视口检查页面无重叠或裁切。
