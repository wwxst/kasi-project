# Admin Header Controls Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the language, dark-mode, and layout-settings controls from the admin header while preserving every unmentioned layout control and behavior.

**Architecture:** Make a narrow deletion in the existing `AdminLayout` instead of adding feature flags or hidden DOM. Remove only state, effects, imports, CSS, tests, and documentation that exist solely for the three deleted controls; keep the existing light layout and sidebar footer collapse path.

**Tech Stack:** React 19, TypeScript 6, Ant Design 6, Lucide React, CSS, Vitest, React Testing Library, Vite, pnpm.

---

## File Structure

- Modify `src/App.test.tsx`: specify that the three header controls are absent and that retained controls remain available.
- Modify `src/layouts/AdminLayout.tsx`: remove the three controls and their direct state/effect/icon dependencies.
- Modify `src/layouts/admin-layout.css`: remove dark-only styles and obsolete responsive selectors.
- Modify `README.md`: update the current admin-shell feature list.
- Modify `docs/superpowers/specs/2026-08-21-admin-header-controls-removal-design.md`: record the verified implementation result.

### Task 1: Lock the removal boundary with a failing layout test

**Files:**
- Modify: `src/App.test.tsx:298-339`
- Test: `src/App.test.tsx`

- [ ] **Step 1: Replace the three positive control assertions with absence assertions**

In the dashboard layout test, keep `const banner = screen.getByRole('banner')`, search, notification, fullscreen, and account assertions. Replace the language, theme, and layout-settings checks with:

```tsx
expect(
  within(banner).queryByRole('button', { name: '语言' }),
).not.toBeInTheDocument()
expect(
  within(banner).queryByRole('button', { name: '切换深色模式' }),
).not.toBeInTheDocument()
expect(
  within(banner).queryByRole('button', { name: '布局设置' }),
).not.toBeInTheDocument()
```

Delete the user click and assertions that expect `data-theme="dark"` and the “切换浅色模式” button. Add an explicit retained-control assertion outside the banner:

```tsx
expect(
  screen.getByRole('button', { name: '收起侧边栏' }),
).toBeInTheDocument()
```

- [ ] **Step 2: Run the focused test and verify the intended red state**

Run:

```powershell
pnpm test -- src/App.test.tsx
```

Expected: the dashboard layout test fails because the existing language, dark-mode, and layout-settings buttons are still present. The retained notification, fullscreen, account menu, and sidebar collapse assertions must not be the cause of failure.

### Task 2: Remove the controls, clean direct dependencies, and verify

**Files:**
- Modify: `src/layouts/AdminLayout.tsx`
- Modify: `src/layouts/admin-layout.css`
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-08-21-admin-header-controls-removal-design.md`
- Test: `src/App.test.tsx`

- [ ] **Step 1: Remove the three header controls from `AdminLayout.tsx`**

Delete the `Languages`, `Moon`, and `Sun` Lucide imports. Keep `Settings` because the system-configuration navigation group still uses it.

Delete:

```tsx
const [darkMode, setDarkMode] = useState(false)

useEffect(() => {
  document.documentElement.dataset.theme = darkMode ? 'dark' : 'light'
}, [darkMode])
```

Change the root layout to the fixed light-layout class:

```tsx
<Layout className="admin-layout">
```

Delete the complete language `Popover`, dark-mode `Tooltip`/`Button`, and layout-settings `Dropdown` blocks. Do not change the notification `Popover`, fullscreen `Tooltip`, account `Dropdown`, or sidebar footer collapse button.

- [ ] **Step 2: Remove only obsolete CSS**

Delete the complete `.admin-layout--dark` block from `.admin-layout--dark,` through the final dark `.ant-card-head` border rule. In the mobile media query, change:

```css
.admin-header__search,
.admin-header__language,
.admin-header__theme,
.admin-header__settings,
.admin-header__fullscreen {
```

to:

```css
.admin-header__search,
.admin-header__fullscreen {
```

Keep all other responsive and sidebar styles unchanged.

- [ ] **Step 3: Run the focused test and verify green**

Run:

```powershell
pnpm test -- src/App.test.tsx
```

Expected: `src/App.test.tsx` passes with zero failures.

- [ ] **Step 4: Update current documentation**

In `README.md`, change the current-state sentence from:

```text
后台壳层包含固定顶栏、导航搜索、语言与通知入口、深浅主题、布局设置、全屏控制、账户菜单、可折叠桌面侧栏、移动端导航抽屉和面包屑。
```

to:

```text
后台壳层包含固定顶栏、导航搜索、通知入口、全屏控制、账户菜单、可折叠桌面侧栏、移动端导航抽屉和面包屑。
```

In the design document, set:

```markdown
状态：已实施并验证（2026-08-21）

实施结果：管理后台顶栏已移除语言、暗色模式和布局设置三个入口；通知、全屏、账户菜单及侧栏底部折叠保持不变。布局固定使用浅色样式。
```

- [ ] **Step 5: Run complete frontend verification**

Run each command and require exit code zero:

```powershell
pnpm test
pnpm typecheck
pnpm lint
pnpm format:check
pnpm build
git diff --check
```

Expected: all tests pass, TypeScript reports no errors, Oxlint reports no errors, Prettier reports all files formatted, Vite build succeeds, and Git reports no whitespace errors.

- [ ] **Step 6: Inspect the final scope and commit**

Run:

```powershell
git status --short
git diff -- src/App.test.tsx src/layouts/AdminLayout.tsx src/layouts/admin-layout.css README.md docs/superpowers/specs/2026-08-21-admin-header-controls-removal-design.md
```

Expected: only the five planned files have implementation changes. Then commit:

```powershell
git add -- src/App.test.tsx src/layouts/AdminLayout.tsx src/layouts/admin-layout.css README.md docs/superpowers/specs/2026-08-21-admin-header-controls-removal-design.md
git diff --cached --check
git commit -m "refactor: simplify admin header controls"
```
