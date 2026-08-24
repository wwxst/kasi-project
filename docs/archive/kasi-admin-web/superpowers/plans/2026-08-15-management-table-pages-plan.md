# Ant Design Pro 查询表格页移植 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 直接使用 Ant Design Pro Table List 组件结构实现管理员管理和推广用户管理页面，并接入现有后端接口。

**Architecture:** 共享 `ManagementTablePage` 使用 `PageContainer + ProTable + Drawer` 管理查询、分页、新建、分组详情、详情内编辑、状态操作和删除。管理员页与用户页分别提供严格的表格列、详情分组、编辑字段和权限配置。

**Tech Stack:** React 19、TypeScript、Vite、Ant Design 6、Ant Design Pro Components 3、Axios、Vitest、React Testing Library、MSW。

---

### Task 1: 建立新交互的失败测试

**Files:**

- Modify: `src/App.test.tsx`

- [x] 断言两个页面没有状态开关、编辑和重置密码入口。
- [x] 断言管理员操作为详情和删除，详情打开抽屉。
- [x] 断言用户操作为详情和更多，更多包含启用/禁用和删除。
- [x] 运行 `pnpm test -- src/App.test.tsx` 并确认旧实现按预期失败。

### Task 2: 移植官方 Table List 页面结构

**Files:**

- Modify: `package.json`
- Modify: `pnpm-lock.yaml`
- Modify: `src/features/management/ManagementTablePage.tsx`
- Modify: `src/pages/management/management-page.css`

- [x] 引入官方 `@ant-design/pro-components` 和 `@ant-design/icons`。
- [x] 使用 ProTable 的 request、查询表单、工具栏、分页和 ActionType 刷新。
- [x] 使用 Drawer 和 ProDescriptions 实现只读详情。
- [x] 保留新建弹窗、删除确认以及用户状态更新。

### Task 3: 配置管理员与用户字段

**Files:**

- Modify: `src/pages/management/AdminManagementPage.tsx`
- Modify: `src/pages/management/UserManagementPage.tsx`

- [x] 管理员列设置为姓名、手机号、邮箱、角色、登录时间、状态、操作。
- [x] 管理员操作设置为详情和删除，唯一超级管理员禁止删除。
- [x] 用户列设置为用户 ID、昵称、手机号、邮箱、注册来源、状态、操作。
- [x] 用户操作设置为详情和更多，更多包含启用/禁用和删除。

### Task 4: 文档与验收

**Files:**

- Modify: `README.md`
- Modify: `THIRD_PARTY_NOTICES.md`
- Modify: `docs/superpowers/specs/2026-08-15-management-table-pages-design.md`

- [x] 记录官方仓库、源码路径、参考提交和 MIT 许可。
- [x] 更新当前页面字段和交互边界。
- [x] 运行完整测试、类型检查、代码规范、格式和生产构建。
- [x] 在桌面浏览器验证真实 ProTable、详情抽屉和用户更多菜单，并保留移动端响应式间距规则。

### Task 5: 详情内编辑抽屉

**Files:**

- Modify: `src/features/management/ManagementTablePage.tsx`
- Modify: `src/pages/management/AdminManagementPage.tsx`
- Modify: `src/pages/management/UserManagementPage.tsx`
- Modify: `src/pages/management/management-page.css`
- Modify: `src/App.test.tsx`

- [x] 将详情改为带蓝色标题竖线的分组信息块，桌面两列、移动端单列。
- [x] 在基本信息标题旁增加编辑入口，并从详情上层打开右侧编辑抽屉。
- [x] 管理员和推广用户编辑分别接入已有 `PUT` 接口，只提交后端已有字段。
- [x] 唯一超级管理员不显示编辑入口；表格仍不显示编辑按钮。
- [x] 为新建与编辑表单设置独立名称空间，避免两个表单产生重复字段 ID。
- [x] 增加详情回填、编辑提交和抽屉字段隔离的自动化测试。
