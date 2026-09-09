# 管理端菜单目录整理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按已确认目录重整 Kasi 管理端菜单，并将短信/邮箱页面的菜单显示名改为“系统配置”，不改变既有路由、权限和业务功能。

**Architecture:** 继续在 `AdminLayout` 内使用 Ant Design Menu 的静态配置。顶级入口保留“用户管理”“管理员管理”，短剧相关页面统一放入“短剧管理”，两类同步页面再放入“同步管理”，系统页面统一放入“系统设置”。通过当前 pathname 计算 `defaultOpenKeys`，让深层页面只展开所属分支；桌面侧栏和移动端 Drawer 继续复用同一份 JSX。

**Tech Stack:** React 19、TypeScript、React Router、Ant Design 6、Vitest、Testing Library、pnpm。

---

### Task 1: 更新菜单层级与显示名称

**Files:**
- Modify: `kasi-admin-web/src/layouts/AdminLayout.tsx`

- [ ] **Step 1: 调整菜单项结构**

在 `navigation` 中保留现有权限条件，并将 `items` 调整为以下结构；所有叶子节点继续使用原路径：

```tsx
items={[
  ...(admin?.isSuperAdmin === 1
    ? [{
        key: '/admin-management',
        icon: <ShieldCheck size={18} strokeWidth={1.8} />,
        label: <Link to="/admin-management">管理员管理</Link>,
      }]
    : []),
  {
    key: '/user-management',
    icon: <Users size={18} strokeWidth={1.8} />,
    label: <Link to="/user-management">用户管理</Link>,
  },
  {
    key: 'drama-management',
    icon: <LibraryBig size={18} strokeWidth={1.8} />,
    label: '短剧管理',
    children: [
      {
        key: '/drama/catalog',
        icon: <Clapperboard size={18} strokeWidth={1.8} />,
        label: <Link to="/drama/catalog">短剧目录</Link>,
      },
      {
        key: '/promotion/media-accounts',
        icon: <BadgeCheck size={18} strokeWidth={1.8} />,
        label: <Link to="/promotion/media-accounts">账号报备</Link>,
      },
      {
        key: '/promotion/links',
        icon: <ListOrdered size={18} strokeWidth={1.8} />,
        label: <Link to="/promotion/links">推广任务</Link>,
      },
      {
        key: '/promotion/orders',
        icon: <ListOrdered size={18} strokeWidth={1.8} />,
        label: <Link to="/promotion/orders">推广订单</Link>,
      },
      {
        key: 'drama-sync-management',
        icon: <ListOrdered size={18} strokeWidth={1.8} />,
        label: '同步管理',
        children: [
          {
            key: '/drama/sync/catalog',
            icon: <ListOrdered size={18} strokeWidth={1.8} />,
            label: <Link to="/drama/sync/catalog">短剧同步</Link>,
          },
          {
            key: '/drama/sync/content',
            icon: <ListOrdered size={18} strokeWidth={1.8} />,
            label: <Link to="/drama/sync/content">剧集同步</Link>,
          },
        ],
      },
    ],
  },
  {
    key: 'system-config',
    icon: <Settings size={18} strokeWidth={1.8} />,
    label: '系统设置',
    children: [
      {
        key: '/system-config/drama-api',
        icon: <Clapperboard size={18} strokeWidth={1.8} />,
        label: <Link to="/system-config/drama-api">平台接入</Link>,
      },
      {
        key: '/system-config/commission-rules',
        icon: <Clapperboard size={18} strokeWidth={1.8} />,
        label: <Link to="/system-config/commission-rules">分佣规则</Link>,
      },
      {
        key: '/system-config/scheduled-tasks',
        icon: <Clock3 size={18} strokeWidth={1.8} />,
        label: <Link to="/system-config/scheduled-tasks">定时任务</Link>,
      },
      ...(admin?.isSuperAdmin === 1
        ? [{
            key: '/system-config/sms',
            icon: <Settings size={18} strokeWidth={1.8} />,
            label: <Link to="/system-config/sms">系统配置</Link>,
          }]
        : []),
    ],
  },
]}
```

“账号报备”替代菜单显示名“媒体账号报备”，“平台接入”替代“短剧 API 配置”，“系统配置”替代“短信配置”；页面标题和 URL 不改。

- [ ] **Step 2: 计算当前分支的展开项**

将现有只处理 `/drama/sync/` 的 `defaultOpenKeys` 改为：

```tsx
defaultOpenKeys={[
  ...(location.pathname.startsWith('/drama/') ||
  location.pathname.startsWith('/promotion/')
    ? ['drama-management']
    : []),
  ...(location.pathname.startsWith('/drama/sync/')
    ? ['drama-sync-management']
    : []),
  ...(location.pathname.startsWith('/system-config/')
    ? ['system-config']
    : []),
]}
```

`selectedKeys` 继续使用当前 pathname，因此叶子项选中行为和路由保持不变。

### Task 2: 更新菜单回归测试

**Files:**
- Modify: `kasi-admin-web/src/layouts/AdminLayout.test.tsx`
- Modify: `kasi-admin-web/src/App.test.tsx`

- [ ] **Step 1: 更新 AdminLayout 静态导航测试**

在 `AdminLayout.test.tsx` 保留品牌搜索断言，并增加/调整断言覆盖：

```tsx
expect(screen.getByText('短剧管理')).toBeInTheDocument()
expect(screen.queryByRole('link', { name: '短剧目录' })).not.toBeInTheDocument()

fireEvent.click(screen.getByRole('menuitem', { name: '短剧管理' }))
expect(screen.getByRole('link', { name: '账号报备' })).toHaveAttribute(
  'href',
  '/promotion/media-accounts',
)
expect(screen.getByRole('link', { name: '推广任务' })).toHaveAttribute(
  'href',
  '/promotion/links',
)
expect(screen.getByRole('link', { name: '推广订单' })).toHaveAttribute(
  'href',
  '/promotion/orders',
)
expect(screen.getByText('同步管理')).toBeInTheDocument()
fireEvent.click(screen.getByRole('menuitem', { name: '同步管理' }))
expect(screen.getByRole('link', { name: '短剧同步' })).toHaveAttribute(
  'href',
  '/drama/sync/catalog',
)
```

- [ ] **Step 2: 更新集成测试中的旧菜单名称**

将 `App.test.tsx` 中旧显示名断言替换为新显示名：

```tsx
expect(screen.getByText('系统设置')).toBeInTheDocument()
expect(screen.queryByRole('link', { name: '平台接入' })).not.toBeInTheDocument()
expect(screen.queryByRole('link', { name: '系统配置' })).not.toBeInTheDocument()
```

点击顺序统一为：先点击“短剧管理”，再点击“同步管理”或目标叶子项；推广页面直接从“短剧管理”展开后点击“推广订单”等入口。保留现有普通管理员隐藏超级管理员入口的断言，并增加“系统配置”不可见断言。

- [ ] **Step 3: 增加深层路由展开断言**

使用 `MemoryRouter initialEntries={['/drama/sync/content']}` 渲染 `AdminLayout`，确认“同步管理”的叶子链接直接可见；使用 `'/system-config/sms'` 确认“系统配置”链接直接可见。这样验证 `defaultOpenKeys` 的路径映射，而不仅验证手动点击。

### Task 3: 同步管理端文档

**Files:**
- Modify: `docs/projects/kasi-admin-web.md`

- [ ] **Step 1: 记录当前导航目录**

在当前页面覆盖说明后补充一段，明确管理端菜单显示结构、权限和路径保持规则：

```markdown
管理端导航按“用户管理、管理员管理、短剧管理、系统设置”组织。短剧管理包含短剧目录、账号报备、推广任务、推广订单和同步管理；同步管理包含短剧同步与剧集同步。管理员管理和系统设置仅超级管理员可见。菜单名称调整不改变既有路由地址和后端接口。
```

### Task 4: 验证并复核范围

**Files:**
- Verify: `kasi-admin-web/src/layouts/AdminLayout.tsx`
- Verify: `kasi-admin-web/src/layouts/AdminLayout.test.tsx`
- Verify: `kasi-admin-web/src/App.test.tsx`
- Verify: `docs/projects/kasi-admin-web.md`

- [ ] **Step 1: 运行管理端完整 Gate**

Run: `pnpm check` in `E:\JavaProjects\kasi-project\kasi-admin-web`

Expected: lint、format check、Vitest 和 TypeScript/Vite build 全部通过；记录测试数量和任何真实环境限制。

- [ ] **Step 2: 检查补丁格式**

Run: `git diff --check` in `E:\JavaProjects\kasi-project`

Expected: 无 whitespace 错误。

- [ ] **Step 3: 复核修改范围**

Run: `git status --short` and `git diff --stat`

Expected: 只包含管理端菜单实现、对应测试、管理端导航说明，以及本轮已批准的设计/计划文件；保留已有未跟踪的 `docs/superpowers/specs/2026-09-09-runtime-flyway-auto-migration-design.md` 不改动。
