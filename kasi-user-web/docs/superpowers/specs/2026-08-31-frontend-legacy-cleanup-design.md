# 用户前端旧体系清理设计

## 目标

只保留当前 `main.tsx -> src/App.tsx -> /workspace` 运行体系，删除 2026-08-28 前端入口切换后遗留的旧 `/account` 体系及其专用测试，恢复全量测试、类型检查和构建的一致性。

## 当前证据

- 当前生产入口是 `src/main.tsx`，只加载根级 `src/App.tsx`。
- 当前路由由 `src/app/AppRouter.tsx` 和 `src/app/routes.tsx` 提供，使用 `/workspace/**`。
- `src/app/App.tsx`、`src/layouts/**`、`src/pages/account/**`、`src/pages/auth/**`、`src/pages/promotion/**` 只形成旧体系内部调用链，没有当前生产入口消费者。
- `src/features/account/**`、`src/features/auth/api|components|model/**`、`src/features/promotion/**` 只服务旧页面和旧测试；其中订单 API 是例外，已被当前订单页使用。
- `src/test/setup.ts` 和 `src/test/server.ts` 只被旧测试消费。当前测试自行管理 mock 和 DOM 清理。

## 实施设计

1. 将订单 API、类型和 API 测试迁移到 `src/features/orders/`，更新订单页及测试导入，保持 HTTP 路径、分页、状态和 CSV 行为不变。
2. 删除旧应用入口、旧布局、旧页面、旧 feature、旧测试以及无消费者的 `ApiError` 和 MSW 测试设施。
3. 保留当前 `src/App.tsx`、`src/app/AppRouter.tsx`、`src/app/routes.tsx`、`src/layout/**`、当前业务 feature 和页面，不修改当前路由或接口契约。
4. 更新 README 和 AGENTS，明确仓库只有一套前端运行结构。

## 非目标

- 不恢复旧注册、忘记密码、账户概览或安全设置页面。
- 不重做当前登录、账号报白、短剧、推广任务、订单和佣金页面。
- 不修改后端 API、认证规则、依赖版本、构建工具或发布流程。

## 验证

- 订单聚焦测试保持通过。
- `pnpm test`、`pnpm typecheck`、`pnpm lint`、`pnpm build` 全部退出码为 0；清理涉及文件通过聚焦 Prettier 检查。全量 `pnpm format:check` 仍仅因用户已有的 `pnpm-lock.yaml` 格式差异退出码为 1。
- 精确搜索确认不存在旧目录导入、`apiRequest` 调用、旧 `AppProviders` 或旧 MSW server 引用。
- 根仓库 `git diff --check` 通过，并复核未修改本任务之外的用户文件。
