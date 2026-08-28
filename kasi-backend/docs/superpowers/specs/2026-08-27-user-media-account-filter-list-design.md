# 用户媒体账号筛选列表页设计

## 目标

将 `tdesign-react-starter` 的 `List/Select` 页面结构迁移到 `kasi-user-web` 的 `/workspace/media-accounts`，保留 Starter 的筛选表单、TDesign 表格、分页和容器视觉；数据继续使用现有用户媒体账号接口。

## 当前契约

用户接口为 `GET /api/user/promotion/media-accounts`，返回当前用户的完整媒体账号列表，不接受分页或筛选参数。每项包含 `id`、`mediaType`、`externalAccountId`、`accountName`、`accountLink`、`status` 和 `filings`。

## 方案

在用户前端新增媒体账号 API 类型和查询函数，使用 React Query 加载并缓存完整列表。筛选条件和分页在页面内存中派生：筛选按媒体平台、账号名称/账号 ID、账号状态和报备状态执行，再按当前页和每页条数切片。查询或重置时回到第一页；接口错误显示 TDesign 错误提示，加载中使用 Table loading，空结果使用表格空状态。

页面组件拆为筛选表单和列表页两部分，沿用 Starter 的 `Form + Row + Col` 响应式布局、`pageWithPadding/pageWithColor` 容器、`Table.pagination` 配置和状态标签视觉。表格列为媒体平台、账号名称、账号 ID、账号链接、报备状态和账号状态，不虚构接口未返回的日期、金额或合同字段。当前阶段不新增操作按钮行为，不改后端 API，不迁移其他路由。

## 验证

- 组件测试覆盖初次加载、筛选、重置、分页、空结果和接口错误。
- 类型检查、Lint、格式检查和生产构建通过。
- 浏览器验证 `/workspace/media-accounts` 的 Starter 容器、筛选区、表格、分页、加载态和错误态。

## 范围边界

当前实现只覆盖用户媒体账号列表页。后续其他列表页可复用视觉和组件组织，但不在本次变更中扩展后端分页契约或实现详情、编辑、报备重试交互。
