# TDesign Starter SideLayout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with verification checkpoints.

**Goal:** 将 TDesign Starter 的 SideLayout 壳层、视觉细节和响应式行为迁移到 `kasi-user-web`，让登录页保持全屏并提供可访问的用户端工作区路由。

**Architecture:** 使用 React Router 管理 `/login` 和 `/workspace/*`，使用独立的 React Context + `useReducer` 管理菜单收起、主题、Header/Breadcrumb/Footer 和设置抽屉状态。SideLayout 只负责壳层，菜单从用户端路由元数据生成；业务页面先使用无业务数据的页面占位，不复制 Starter Demo 页面。

**Tech Stack:** React 19, TypeScript strict, React Router 7, TDesign React, TDesign Icons React, Vite, Vitest, Testing Library.

---

### Task 1: 建立布局状态边界

**Files:**

- Create: `src/layout/layoutTypes.ts`
- Create: `src/layout/LayoutProvider.tsx`
- Test: `src/layout/LayoutProvider.test.tsx`

- [ ] **Step 1: Write the failing state test**

测试初始状态为 `collapsed=false`、`theme='light'`、`showHeader=true`、`showBreadcrumbs=true`、`showFooter=true`、`settingOpen=false`；分别 dispatch `toggleMenu`、`switchTheme`、`toggleSetting`、`toggleBreadcrumbs`、`toggleFooter` 后断言状态变化。

- [ ] **Step 2: Run the state test and verify it fails**

Run: `pnpm vitest run src/layout/LayoutProvider.test.tsx`

Expected: FAIL because the layout context and reducer do not exist.

- [ ] **Step 3: Implement the minimal context and reducer**

`layoutTypes.ts` 定义：

```ts
export type LayoutTheme = 'light' | 'dark'

export interface LayoutState {
  collapsed: boolean
  theme: LayoutTheme
  showHeader: boolean
  showBreadcrumbs: boolean
  showFooter: boolean
  settingOpen: boolean
}
```

`LayoutProvider.tsx` 提供 `LayoutContext`, `useLayoutState()` 和 action helpers；主题 action 同步 `document.documentElement.dataset.theme`，但不保存业务数据。

- [ ] **Step 4: Run the state test and verify it passes**

Run: `pnpm vitest run src/layout/LayoutProvider.test.tsx`

Expected: PASS with all state assertions green.

### Task 2: 建立用户端路由元数据和页面占位

**Files:**

- Create: `src/app/routes.tsx`
- Create: `src/pages/WorkspacePage.tsx`
- Create: `src/pages/WorkspacePage.css`
- Create: `src/app/AppRouter.tsx`
- Test: `src/app/AppRouter.test.tsx`

- [ ] **Step 1: Write the failing route test**

渲染 `AppRouter`：断言 `/login` 渲染 `LoginPage`，`/workspace` 渲染“用户首页”占位；断言路由元数据包含 `home`、`media-accounts`、`drama`、`promotion-links`、`orders`、`commission` 六个用户端菜单项。

- [ ] **Step 2: Run the route test and verify it fails**

Run: `pnpm vitest run src/app/AppRouter.test.tsx`

Expected: FAIL because the router and user route metadata do not exist.

- [ ] **Step 3: Implement routes and a no-data workspace page**

`routes.tsx` 使用 `RouteConfig` 保存 `path`, `title`, `icon`, `element`, `children` 和 `breadcrumb`，只定义用户端路径；不复制 Starter Dashboard/List/Form/Detail Demo。

`WorkspacePage.tsx` 只输出页面标题和当前路由内容容器，不伪造业务统计或后端数据。

- [ ] **Step 4: Run the route test and verify it passes**

Run: `pnpm vitest run src/app/AppRouter.test.tsx`

Expected: PASS for login bypass, workspace route and menu metadata.

### Task 3: 迁移 Side 菜单和 Logo

**Files:**

- Create: `src/layout/SidebarMenu.tsx`
- Create: `src/layout/sidebar.css`
- Copy: `src/assets/svg/assets-t-logo.svg`
- Test: `src/layout/SidebarMenu.test.tsx`

- [ ] **Step 1: Write the failing menu test**

断言菜单显示六个用户端标题，当前路径对应菜单项拥有 active 状态，点击菜单项调用 React Router 导航；移动收起状态下只保留折叠 Logo 和图标。

- [ ] **Step 2: Run the menu test and verify it fails**

Run: `pnpm vitest run src/layout/SidebarMenu.test.tsx`

Expected: FAIL because `SidebarMenu` does not exist.

- [ ] **Step 3: Implement Starter-compatible Side menu**

使用 TDesign `Menu`, `MenuItem`, `SubMenu`，固定展开宽度 `232px`，使用路由元数据生成菜单，复用 Starter 的激活背景、品牌色、滚动条、Logo 和底部版本区域样式。

- [ ] **Step 4: Run the menu test and verify it passes**

Run: `pnpm vitest run src/layout/SidebarMenu.test.tsx`

Expected: PASS for titles, active state, navigation and collapsed rendering.

### Task 4: 迁移 Header、用户菜单和设置入口

**Files:**

- Create: `src/layout/AppHeader.tsx`
- Create: `src/layout/app-header.css`
- Create: `src/layout/SettingsDrawer.tsx`
- Create: `src/layout/settings-drawer.css`
- Test: `src/layout/AppHeader.test.tsx`

- [ ] **Step 1: Write failing header interaction tests**

断言 Header 渲染菜单收起按钮、搜索框、帮助入口、用户菜单和设置按钮；点击菜单按钮改变 `collapsed`，点击设置打开抽屉，抽屉可切换主题和 Footer/Breadcrumb 开关；不渲染 Top/Mix 布局选项。

- [ ] **Step 2: Run the header tests and verify they fail**

Run: `pnpm vitest run src/layout/AppHeader.test.tsx`

Expected: FAIL because Header and SettingsDrawer do not exist.

- [ ] **Step 3: Implement Starter visual details**

复用 Starter Header 的 sticky 定位、左右 `20px` 内边距、底部边框、方形图标按钮、搜索框 hover/focus 样式、用户下拉菜单和 `458px` 设置抽屉；帮助和外部链接只保留可用动作，退出按钮导航到 `/login`。

- [ ] **Step 4: Run the header tests and verify they pass**

Run: `pnpm vitest run src/layout/AppHeader.test.tsx`

Expected: PASS for all controls and settings state transitions.

### Task 5: 组装 AppShell 和 Page 容器

**Files:**

- Create: `src/layout/AppShell.tsx`
- Create: `src/layout/app-shell.css`
- Create: `src/layout/PageContainer.tsx`
- Create: `src/layout/page-container.css`
- Modify: `src/app/AppRouter.tsx`
- Modify: `src/App.tsx`
- Modify: `src/main.tsx`
- Test: `src/layout/AppShell.test.tsx`

- [ ] **Step 1: Write failing shell tests**

断言 `/login` 不显示 Sidebar/Header/Footer；`/workspace` 显示 SideLayout、Header、PageContainer、Footer；切换收起状态后侧栏宽度和内容布局类名变化；Breadcrumb/Footer 开关影响对应 DOM。

- [ ] **Step 2: Run the shell tests and verify they fail**

Run: `pnpm vitest run src/layout/AppShell.test.tsx`

Expected: FAIL because the app still renders `LoginPage` directly and has no BrowserRouter/AppShell.

- [ ] **Step 3: Implement the shell composition**

`main.tsx` 增加 `BrowserRouter` 和 `LayoutProvider`；`App.tsx` 渲染 `AppRouter`。`AppShell` 采用 Starter SideLayout 结构：`SidebarMenu -> main column -> AppHeader -> PageContainer -> Footer`；`/login` 通过 full-page route 绕过壳层。

- [ ] **Step 4: Run the shell tests and verify they pass**

Run: `pnpm vitest run src/layout/AppShell.test.tsx`

Expected: PASS for login bypass, Side shell, collapse and visibility switches.

### Task 6: 复刻响应式与全局视觉

**Files:**

- Modify: `src/styles/global.css`
- Modify: `src/layout/sidebar.css`
- Modify: `src/layout/app-header.css`
- Modify: `src/layout/app-shell.css`
- Test: `src/layout/responsive.test.tsx`

- [ ] **Step 1: Write responsive regression assertions**

在 `390px` 视口断言文档宽度等于视口宽度、侧栏默认收起、Header 菜单按钮可见；在桌面视口断言侧栏展开宽度为 `232px`、主内容不会覆盖侧栏。

- [ ] **Step 2: Run responsive tests and verify they fail**

Run: `pnpm vitest run src/layout/responsive.test.tsx`

Expected: FAIL because the shell has no responsive rules.

- [ ] **Step 3: Implement responsive CSS**

保留 Starter 的 `900px/1000px` 菜单切换阈值和 `min-width: 760px` 内容策略，同时在移动端将侧栏从 flex 流中移出并使用 fixed/transform，避免文档级横向溢出。

- [ ] **Step 4: Run responsive tests and browser verification**

Run: `pnpm vitest run src/layout/responsive.test.tsx`

Browser checks: `1280x800` and `390x844` screenshots; verify no console errors, no document horizontal overflow, and no overlap between Header, Sidebar and content.

### Task 7: 完成全量验证和文档同步

**Files:**

- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-08-26-tdesign-starter-layout-design.md`

- [ ] **Step 1: Document current implementation**

README 明确 `/login` 全屏页、`/workspace` SideLayout 预览入口、菜单范围、移动端阈值和当前没有真实业务数据的边界。

- [ ] **Step 2: Run the complete verification suite**

Run:

```powershell
pnpm test
pnpm typecheck
pnpm lint
pnpm format:check
pnpm build
```

Expected: all commands exit with code `0`; remove generated `dist` after build because it is excluded from the workspace deliverable.

- [ ] **Step 3: Run `git diff --check` if repository metadata exists**

The current `kasi-user-web` directory has no `.git`; report this limitation instead of fabricating a diff result. Preserve all user files and do not delete unrelated `.superpowers` artifacts.
