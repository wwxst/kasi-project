# 管理员详情头像、编辑与修改密码 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在管理员详情抽屉展示目标管理员头像，并让“编辑”和“修改密码”始终操作当前详情记录，同时支持本人和其他管理员的不同接口契约。

**Architecture:** 详情抽屉通过可选配置渲染身份区和密码操作，管理员页面根据详情记录 ID 是否等于当前认证管理员 ID 分流到本人认证接口或管理员管理接口。后端本人改密接口调整为仅接收新密码和确认密码，成功后仍通过现有 Redis 会话版本机制使旧 Token 失效。

**Tech Stack:** React 19、TypeScript、Ant Design 6、Vitest、MSW、Spring Boot、Jakarta Validation、MyBatis、JUnit 5、MockMvc。

---

### Task 1: 调整后端本人改密契约

**Files:**

- Modify: `E:/JavaProjects/kasi-project/kasi-backend/src/test/java/com/kasi/backend/admin/controller/AdminAuthControllerTest.java`
- Modify: `E:/JavaProjects/kasi-project/kasi-backend/src/main/java/com/kasi/backend/admin/dto/AdminChangePasswordDTO.java`
- Modify: `E:/JavaProjects/kasi-project/kasi-backend/src/main/java/com/kasi/backend/admin/service/impl/AdminAuthServiceImpl.java`
- Modify: `E:/JavaProjects/kasi-project/kasi-backend/src/main/java/com/kasi/backend/common/exception/ErrorCode.java`
- Modify: `E:/JavaProjects/kasi-project/kasi-backend/README.md`

- [x] **Step 1: 写本人无需原密码的失败测试**

将成功请求改为只提交新密码和确认密码，并把“旧密码错误”用例改为“未提交原密码也成功”：

```java
mockMvc.perform(MockMvcRequestBuilders
        .put("/api/admin/auth/password")
        .header("Authorization", "Bearer " + token)
        .contentType("application/json")
        .content("""
                {"newPassword":"newpass123","confirmPassword":"newpass123"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));
```

- [x] **Step 2: 运行测试确认失败**

Run:

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd --% -Dtest=AdminAuthControllerTest test
```

Expected: 请求因 `oldPassword` 缺失返回参数校验错误。

- [x] **Step 3: 删除旧密码字段与校验**

`AdminChangePasswordDTO` 只保留：

```java
private String newPassword;
private String confirmPassword;
```

保留两个字段现有的 `@NotBlank`、`@Size(min = 8, max = 72)`、ASCII `@Pattern` 和 `@Utf8ByteLength`。在 `AdminAuthServiceImpl.changePassword` 中删除 `request.getOldPassword()` 的校验，继续拒绝新密码与数据库当前密码相同，并保留确认密码一致性、Redis `MUTATING` 和事务提交后会话激活逻辑。

从 `ErrorCode` 删除已不可达的 `ADMIN_OLD_PASSWORD_ERROR(2004, "原密码错误")`，不重排其他错误码。

- [x] **Step 4: 更新后端 README 当前契约**

明确 `PUT /api/admin/auth/password` 只接收 `newPassword`、`confirmPassword`，无需原密码，成功后旧 Token 失效。

- [x] **Step 5: 运行后端针对性测试**

Run: 与 Step 2 相同。

Expected: `AdminAuthControllerTest` 全部通过。

### Task 2: 增加前端本人资料与密码 API

**Files:**

- Modify: `src/App.test.tsx`
- Modify: `src/features/auth/authTypes.ts`
- Modify: `src/features/auth/authApi.ts`
- Modify: `src/features/auth/authStore.ts`

- [x] **Step 1: 写 API 分流和认证状态失败测试**

在管理员测试中增加 MSW handlers：

```ts
http.put('/api/admin/auth/profile', async ({ request }) => {
  selfProfileBody = await request.json()
  return HttpResponse.json({ code: 0, message: '修改成功', data: updatedAdmin })
})

http.put('/api/admin/auth/password', async ({ request }) => {
  selfPasswordBody = await request.json()
  return HttpResponse.json({ code: 0, message: '修改成功', data: null })
})
```

断言本人资料请求更新顶栏管理员资料；本人密码请求体严格等于 `{ newPassword, confirmPassword }`，成功后路由进入 `/login`。

- [x] **Step 2: 运行前端测试确认失败**

Run: `pnpm test -- src/App.test.tsx --reporter=verbose`

Expected: 找不到本人编辑或修改密码入口。

- [x] **Step 3: 增加认证类型和 API**

在 `authTypes.ts` 增加：

```ts
export interface UpdateAdminProfileRequest {
  username: string
  realName: string
  mobile?: string
  email?: string
  avatarUrl?: string
}

export interface ChangeAdminPasswordRequest {
  newPassword: string
  confirmPassword: string
}
```

在 `authApi.ts` 增加 `updateAdminProfile(request): Promise<AdminInfo>` 和 `changeAdminPassword(request): Promise<void>`，统一通过 `unwrapApiResponse` 或等价的 `code === 0` 校验返回值。

- [x] **Step 4: 增加认证资料更新动作**

在 `AuthState` 增加：

```ts
updateAdmin: (admin: AdminInfo) => void
```

实现为只替换持久化状态中的 `admin`，不修改当前 `accessToken`。

- [x] **Step 5: 运行测试确认 API 层通过编译**

Run: `pnpm typecheck`

Expected: TypeScript 无错误。

### Task 3: 扩展通用详情抽屉

**Files:**

- Modify: `src/App.test.tsx`
- Modify: `src/features/management/ManagementTablePage.tsx`
- Modify: `src/pages/management/management-page.css`

- [x] **Step 1: 写头像和密码抽屉失败测试**

断言管理员详情包含 64px 头像回退文字、`编辑`、`修改密码`；其他管理员密码抽屉只包含“新密码”和“确认密码”，不包含“原密码”，提交后调用目标 ID 的管理接口。

- [x] **Step 2: 运行测试确认失败**

Run: `pnpm test -- src/App.test.tsx --reporter=verbose`

Expected: 找不到头像身份区和修改密码按钮。

- [x] **Step 3: 增加可选详情能力**

为 `ManagementTablePageProps` 增加：

```ts
detailIdentity?: {
  avatarUrl: (record: D) => string | null
  title: (record: D) => ReactNode
  subtitle: (record: D) => ReactNode
  fallback: (record: D) => ReactNode
}
changePassword?: (record: D, values: Record<string, unknown>) => Promise<void>
renderPasswordForm?: (form: FormInstance, record: D) => ReactNode
onPasswordChanged?: (record: D) => void
refreshAfterUpdate?: (record: D, values: Record<string, unknown>) => boolean
```

在详情第一个分组中渲染身份区，在标题操作区同时渲染编辑和修改密码。新增独立密码 Drawer、Form 实例、提交 loading、成功提示和稳定 `data-testid`。如果 `refreshAfterUpdate` 返回 `false`，把编辑值合并进当前详情而不调用管理详情接口。

- [x] **Step 4: 增加身份区和密码抽屉样式**

身份区使用 64px Avatar、姓名和账号纵向布局；标题操作使用行内间距；密码抽屉沿用 560px 宽度和右下角取消/确认按钮。移动端保持头像与文字不溢出。

- [x] **Step 5: 运行前端测试确认通用组件行为通过**

Run: `pnpm test -- src/App.test.tsx --reporter=verbose`

Expected: 通用详情和用户 CRUD 既有测试不回归。

### Task 4: 接入管理员本人/他人操作分流

**Files:**

- Modify: `src/App.test.tsx`
- Modify: `src/pages/management/AdminManagementPage.tsx`
- Modify: `src/pages/management/UserManagementPage.tsx`
- Modify: `src/features/management/adminManagementApi.ts`
- Modify: `src/features/management/managementTypes.ts`

- [x] **Step 1: 写完整分流失败测试**

覆盖：

```text
本人详情 -> auth/profile
本人详情修改密码 -> auth/password -> 清除会话 -> /login
其他管理员详情 -> management/{id}
其他管理员修改密码 -> management/{id}/password
用户详情 -> 只显示头像，不显示修改密码
```

- [x] **Step 2: 运行测试确认失败**

Run: `pnpm test -- src/App.test.tsx --reporter=verbose`

Expected: 本人操作仍被超级管理员保护规则隐藏。

- [x] **Step 3: 管理员页面按目标 ID 分流**

使用 `useAuthStore`、`useNavigate` 和新认证 API。`canEdit` 对本人返回 `true`，对其他管理员沿用“不能通过管理接口编辑超级管理员”的规则。本人编辑表单不渲染部门编号和备注；其他管理员保持现有字段。

本人资料保存后调用 `updateAdmin` 同步顶栏。若用户名、手机号或邮箱变化，调用 `clearSession()` 并导航 `/login`；仅姓名或头像变化时保留会话。

本人改密调用认证接口并在成功后清除会话、导航 `/login`；其他管理员调用现有 `resetAdminPassword(id, values)` 并保持登录。

- [x] **Step 4: 用户详情补头像**

为用户页面提供 `detailIdentity`，使用昵称作为标题、用户编号作为副标题、昵称首字作为头像回退，不提供密码相关 props。

- [x] **Step 5: 运行完整前端测试**

Run: `pnpm test`

Expected: 7 个现有测试及新增断言全部通过。

### Task 5: 文档、全量验证与浏览器验收

**Files:**

- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-08-15-admin-detail-account-actions-design.md`
- Modify: `docs/superpowers/plans/2026-08-15-admin-detail-account-actions-plan.md`
- Modify: `E:/JavaProjects/kasi-project/kasi-backend/README.md`

- [x] **Step 1: 更新前端当前行为文档**

记录管理员详情头像、目标记录驱动的编辑/改密、本人/他人接口分流和用户详情头像边界。

- [x] **Step 2: 运行后端完整校验**

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd test
.\mvnw.cmd -DskipTests compile
git diff --check
```

Expected: 测试、编译和 diff 检查退出码均为 0。若嵌入式 Redis 在 Windows 报 `0x70`，记录为环境阻塞，并至少完成 Java 25 编译及不依赖 Redis 的针对性测试。

- [x] **Step 3: 运行前端完整校验**

```powershell
pnpm test
pnpm typecheck
pnpm lint
pnpm format:check
pnpm build
```

Expected: 全部退出码为 0。

- [x] **Step 4: 浏览器验收**

验证超级管理员本人详情、普通管理员详情和用户详情；检查头像回退、编辑字段差异、两类密码请求、本人改密后返回登录页、唯一超级管理员删除仍禁用。浏览器验收创建的临时管理员必须在结束前物理删除。
