# 推广页面拆分设计

## 目标

将推广工作流拆成两个独立页面：创建推广与推广链接记录，保持现有用户端 API 和生成链接行为不变。

## 当前实现

- `/promotion/create` 显示短剧库、名称筛选、详情抽屉和生成推广链接弹窗。
- `/promotion/links` 只显示当前用户的推广链接记录，支持复制分享链接。
- `/promotion/tasks` 兼容重定向到 `/promotion/create`。
- 侧栏分别提供“创建推广”和“推广链接记录”两个入口。
- 创建页启用短剧库和媒体账号查询；记录页只启用推广链接查询，避免跨页面请求和无关加载。

## 数据与错误处理

页面继续使用现有 `fetchPublishedPromotionDramas`、`fetchMediaAccounts`、`createPromotionLink` 和 `fetchPromotionLinks` API。请求失败沿用现有 `ApiError` 文案，空数据沿用短剧库和表格空状态，不新增后端字段或接口。

## 验证

前端测试覆盖创建流程、筛选、详情抽屉、空状态、路由导航以及记录页不请求短剧库。完成标准为 `pnpm test`、`pnpm typecheck`、`pnpm lint`、`pnpm format:check` 和 `pnpm build` 全部通过。

## 规划边界

当前不实现推广任务统计、链接删除、后端接口调整或新的权限模型。
