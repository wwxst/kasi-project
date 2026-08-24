# 用户端账号报白前端 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在用户端加入基于 TDesign Table + Drawer 的“账号报白”页面，支持查看、新增、编辑和失败重试。

**Architecture:** 在 `features/promotion` 中封装用户端媒体账号 API、类型和查询状态，页面层只组合数据表格、筛选和抽屉交互。所有请求经过既有 `apiRequest`，仅访问 `/api/user/promotion/media-accounts/**`。

**Tech Stack:** React 19, TypeScript strict, TDesign React, TDesign Icons React, React Router, TanStack Query, Vitest, Testing Library, MSW, pnpm。

---

### Task 1: 建立用户端媒体账号 API 契约

**Files:**

- Create: `src/features/promotion/api/mediaAccountTypes.ts`
- Create: `src/features/promotion/api/mediaAccountApi.ts`
- Test: `src/features/promotion/api/mediaAccountApi.test.ts`

- [x] **Step 1: Write the failing test**

覆盖列表 GET、详情 GET、新增 POST、编辑 PUT 和失败重试 POST，断言请求路径与 payload；先让导入的 API 函数不存在而失败。

- [x] **Step 2: Run test to verify it fails**

Run: `pnpm vitest run src/features/promotion/api/mediaAccountApi.test.ts`
Expected: FAIL because the promotion API module does not exist.

- [x] **Step 3: Write minimal implementation**

定义 `MediaAccount`、`MediaFiling`、`MediaAccountDetail`、`CreateMediaAccountRequest`、`UpdateMediaAccountRequest` 类型；实现 `fetchMediaAccounts`、`fetchMediaAccount`、`createMediaAccount`、`updateMediaAccount`、`retryMediaAccountFiling`，每个函数使用 `apiRequest` 和用户端路径。

- [x] **Step 4: Run test to verify it passes**

Run: `pnpm vitest run src/features/promotion/api/mediaAccountApi.test.ts`
Expected: PASS with all request assertions green.

### Task 2: 增加查询和报白状态映射

**Files:**

- Create: `src/features/promotion/model/mediaAccountQueries.ts`
- Create: `src/features/promotion/model/mediaAccountPresentation.ts`
- Test: `src/features/promotion/model/mediaAccountPresentation.test.ts`

- [x] **Step 1: Write the failing test**

断言 `PENDING`、`APPROVED`、`FAILED` 和没有 filing 时分别映射为“审核中”“已加白”“已失败”“审核中”，并断言已加白时身份字段不可编辑。

- [x] **Step 2: Run test to verify it fails**

Run: `pnpm vitest run src/features/promotion/model/mediaAccountPresentation.test.ts`
Expected: FAIL because the presentation helpers do not exist.

- [x] **Step 3: Write minimal implementation**

实现 `useMediaAccounts`、`useMediaAccount` 和 `useMediaAccountMutation` 所需的 TanStack Query hooks；实现 `getFilingView`、`isIdentityEditable`、`formatMediaType`、`formatDateTime` 和状态 Tag 配置。

- [x] **Step 4: Run test to verify it passes**

Run: `pnpm vitest run src/features/promotion/model/mediaAccountPresentation.test.ts`
Expected: PASS.

### Task 3: 接入账号报白页面与抽屉

**Files:**

- Create: `src/pages/promotion/MediaAccountFilingPage.tsx`
- Create: `src/pages/promotion/media-account-filing.css`
- Modify: `src/styles/global.css`
- Test: `src/pages/promotion/MediaAccountFilingPage.test.tsx`

- [x] **Step 1: Write the failing test**

用 MSW 返回三条账号数据，断言页面渲染 TDesign 表格的复选框、媒体平台、账号名称、GoodShort 状态和详情操作列；点击详情打开 Drawer；失败账号在详情中显示“重试报白”；已加白账号编辑时锁定媒体平台和账号 ID；提交新增断言 payload。

- [x] **Step 2: Run test to verify it fails**

Run: `pnpm vitest run src/pages/promotion/MediaAccountFilingPage.test.tsx`
Expected: FAIL because the page does not exist.

- [x] **Step 3: Write minimal implementation**

使用 TDesign `Table`、`Drawer`、`Form`、`FormItem`、`Input`、`Select`、`Tag`、`Button`、`Alert`；工具栏提供平台/状态筛选、刷新和新增；详情抽屉展示账号字段、GoodShort 报白详情、失败原因和编辑/重试操作；表单按后端约束校验账号 ID 与 HTTPS 主页链接；不渲染启用/禁用控件。

- [x] **Step 4: Run test to verify it passes**

Run: `pnpm vitest run src/pages/promotion/MediaAccountFilingPage.test.tsx`
Expected: PASS.

### Task 4: 接入账号布局和路由

**Files:**

- Modify: `src/app/AppRouter.tsx`
- Modify: `src/layouts/AccountLayout.tsx`
- Test: `src/app/AppRouter.test.tsx`

- [x] **Step 1: Write the failing test**

增加有效会话访问 `/account/filing` 的测试，断言“账号报白”导航项和页面标题存在；断言未登录访问仍跳转登录页。

- [x] **Step 2: Run test to verify it fails**

Run: `pnpm vitest run src/app/AppRouter.test.tsx`
Expected: FAIL because the route and navigation item do not exist.

- [x] **Step 3: Write minimal implementation**

在受保护的 `AccountLayout` 下注册 `/account/filing`，在账号导航中加入指向该路由的 `NavLink`，保持现有认证引导逻辑不变。

- [x] **Step 4: Run test to verify it passes**

Run: `pnpm vitest run src/app/AppRouter.test.tsx`
Expected: PASS.

### Task 5: 文档、静态检查和浏览器验证

**Files:**

- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-08-18-user-media-account-filing-frontend-design.md`
- Modify: `docs/superpowers/plans/2026-08-18-user-media-account-filing-frontend-plan.md`

- [x] **Step 1: 更新 README 和计划勾选状态**

补充 `/account/filing` 路由、用户端账号报白功能和只读状态规则；勾选计划中的已完成步骤。

- [x] **Step 2: 运行完整校验**

Run: `pnpm test; pnpm typecheck; pnpm lint; pnpm format:check; pnpm build`
Expected: each command exits with code 0 and reports no failures.

- [x] **Step 3: 启动开发服务器并检查视口**

Run: `pnpm dev --host 127.0.0.1`
Expected: desktop and 320px+ mobile views show the table, drawer and form without overlap or clipping.

由于当前目录不是 Git 仓库，本任务不执行 `git commit` 或 `git push`。
