# 账号报白功能设计

## 目标

在用户端“账号报白”页面新增账号报白入口，并让筛选和表格状态明确展示 GoodShort 的报白状态。

## 后端契约

- `POST /api/user/promotion/media-accounts` 创建媒体账号，后端为支持报白的已启用平台初始化 filing；当前 GoodShort filing 状态从 `filings[].status` 返回。
- `POST /api/user/promotion/media-accounts/{id}/filings/{providerId}` 提交或重试指定平台的报白任务。
- filing 状态为 `PENDING`、`APPROVED`、`FAILED`，平台名称通过 `filings[].providerName` 返回。

## 设计

- 页面工具栏增加 Starter 风格主按钮“账号报白”，打开 Dialog 表单。
- 表单字段为媒体平台、账号 ID、账号名称、账号主页链接；媒体平台和账号 ID 必填，主页链接沿用后端 HTTPS 校验。
- 新增成功后关闭 Dialog、刷新列表并显示成功提示；失败显示后端业务错误提示，Dialog 保持打开。
- 筛选字段改名为“GoodShort 报白状态”，只匹配 `providerName = GoodShort` 的 filing；无 GoodShort filing 视为“未报白”。
- 表格报白状态列同样只展示 GoodShort 状态。
- 每行操作列对 GoodShort `PENDING` 显示“报白中”不可操作，对 `FAILED` 显示“重新报白”并调用提交接口，对 `APPROVED` 显示“已报白”不可重复提交；缺少 providerId 时不显示提交动作。
- 保留现有 `/workspace/media-accounts` 路由和查询接口；不修改后端。

## 验证

- API 单元测试覆盖创建和提交/重试请求路径及响应解包。
- 列表逻辑测试确认只使用 GoodShort filing，不会被其他平台状态误筛选。
- 页面测试覆盖打开/提交 Dialog、成功刷新、失败提示和报白/重试按钮。
- 运行 `pnpm test`、`pnpm typecheck`、`pnpm lint`、`pnpm format:check`、`pnpm build`，并检查桌面/移动视口无溢出。
