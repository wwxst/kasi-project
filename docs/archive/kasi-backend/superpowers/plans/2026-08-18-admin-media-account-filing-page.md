# 管理员端媒体账号报备页面 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐管理员编辑媒体账号的后端契约，并在 Kasi 管理后台实现媒体账号查询、详情、编辑和失败报备重试页面。

**Architecture:** 后端继续以 `MediaAccountAdminService` 作为管理员用例边界，新增专用更新 DTO，并复用现有媒体账号版本隔离与报备重排规则。前端新增独立的 promotion feature 和页面组件，直接使用 `PageContainer + ProTable + Drawer + Form`，不复用会强制包含新增和删除操作的 `ManagementTablePage`。

**Tech Stack:** Java 25、Spring Boot、MyBatis、JUnit 5、MockMvc、React 19、TypeScript 6、Ant Design 6、Ant Design Pro Components、Axios、Vitest、React Testing Library、MSW。

---

## 文件结构

后端仓库 `E:/JavaProjects/kasi-project/kasi-backend`：

- Create: `src/main/java/com/kasi/backend/promotion/dto/AdminUpdateMediaAccountDTO.java` — 管理员编辑请求及 Jakarta Validation。
- Modify: `src/main/java/com/kasi/backend/promotion/controller/AdminMediaAccountController.java` — 暴露管理员更新接口。
- Modify: `src/main/java/com/kasi/backend/promotion/service/MediaAccountAdminService.java` — 定义管理员更新用例。
- Modify: `src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountAdminServiceImpl.java` — 执行身份锁定、唯一性、版本更新、状态更新和报备重排。
- Modify: `src/main/java/com/kasi/backend/promotion/vo/AdminMediaAccountListItemVO.java` — 增加 `accountName`、`updatedAt`。
- Modify: `src/main/java/com/kasi/backend/promotion/vo/MediaFilingVO.java` — 增加 `nextActionAt`。
- Modify: `src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountServiceImpl.java` — 映射 `nextActionAt`。
- Modify: `src/test/java/com/kasi/backend/promotion/controller/AdminMediaAccountControllerTest.java` — 接口权限、校验、列表和更新测试。
- Modify: `src/test/java/com/kasi/backend/promotion/service/MediaAccountServiceTest.java` — 版本隔离与锁定规则测试。

前端目录 `E:/JavaProjects/kasi-project/kasi-admin-web`：

- Create: `src/features/promotion/mediaAccountTypes.ts` — 页面使用的查询、列表、详情和更新类型。
- Create: `src/features/promotion/mediaAccountApi.ts` — 管理员媒体账号 API 封装。
- Create: `src/features/promotion/mediaAccountApi.test.ts` — API 路径和请求参数测试。
- Create: `src/pages/promotion/MediaAccountFilingPage.tsx` — ProTable、详情抽屉、编辑抽屉和重试交互。
- Create: `src/pages/promotion/media-account-filing-page.css` — 页面和移动端布局。
- Create: `src/pages/promotion/MediaAccountFilingPage.test.tsx` — 页面行为测试。
- Modify: `src/router/AppRouter.tsx` — 注册 `/promotion/media-accounts` 懒加载路由。
- Modify: `src/layouts/AdminLayout.tsx` — 增加“推广管理 / 媒体账号报备”菜单。
- Modify: `src/App.test.tsx` — 验证路由和菜单可见性。
- Modify: `README.md` — 记录管理员媒体账号报备页面和接口。

> `kasi-admin-web` 当前没有 `.git` 目录，因此计划中的前端步骤只做文件和验证；不在该目录执行提交。后端提交只暂存本计划涉及的文件，不包含现有 `application.properties`、`dump.rdb` 或其他用户修改。

### Task 1: 补齐管理员列表和详情响应契约

**Files:**
- Modify: `src/test/java/com/kasi/backend/promotion/controller/AdminMediaAccountControllerTest.java`
- Modify: `src/main/java/com/kasi/backend/promotion/vo/AdminMediaAccountListItemVO.java`
- Modify: `src/main/java/com/kasi/backend/promotion/vo/MediaFilingVO.java`
- Modify: `src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountAdminServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountServiceImpl.java`

- [ ] **Step 1: 写列表和详情字段的失败测试**

在 `AdminMediaAccountControllerTest` 中准备一个媒体账号和报备记录，然后断言：

```java
mockMvc.perform(get("/api/admin/promotion/media-accounts")
        .header("Authorization", "Bearer " + adminToken))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.list[0].accountName").value("TikTok 运营号"))
    .andExpect(jsonPath("$.data.list[0].updatedAt").isNotEmpty());

mockMvc.perform(get("/api/admin/promotion/media-accounts/{id}", mediaAccountId)
        .header("Authorization", "Bearer " + adminToken))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.mediaAccount.filings[0].nextActionAt").isNotEmpty());
```

- [ ] **Step 2: 运行测试并确认字段缺失**

Run:

```powershell
$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -Dtest=AdminMediaAccountControllerTest test
```

Expected: FAIL，JSON 中不存在 `accountName`、`updatedAt` 或 `nextActionAt`。

- [ ] **Step 3: 增加 VO 字段和映射**

在 `AdminMediaAccountListItemVO` 增加：

```java
private String accountName;
private LocalDateTime updatedAt;
```

在 `MediaFilingVO` 增加：

```java
private LocalDateTime nextActionAt;
```

在 `MediaAccountAdminServiceImpl.toListItem` 增加：

```java
.accountName(account.getAccountName())
.updatedAt(account.getUpdatedAt())
```

在 `MediaAccountServiceImpl.toFilingVO` 增加：

```java
.nextActionAt(filing.getNextActionAt())
```

- [ ] **Step 4: 运行聚焦测试**

Run: `.\mvnw.cmd -Dtest=AdminMediaAccountControllerTest test`

Expected: PASS，零失败、零错误。

- [ ] **Step 5: 提交响应契约**

```powershell
git add src/main/java/com/kasi/backend/promotion/vo/AdminMediaAccountListItemVO.java `
  src/main/java/com/kasi/backend/promotion/vo/MediaFilingVO.java `
  src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountAdminServiceImpl.java `
  src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountServiceImpl.java `
  src/test/java/com/kasi/backend/promotion/controller/AdminMediaAccountControllerTest.java
git commit -m "feat: expose admin media filing display fields"
```

### Task 2: 实现管理员编辑媒体账号接口

**Files:**
- Create: `src/main/java/com/kasi/backend/promotion/dto/AdminUpdateMediaAccountDTO.java`
- Modify: `src/main/java/com/kasi/backend/promotion/controller/AdminMediaAccountController.java`
- Modify: `src/main/java/com/kasi/backend/promotion/service/MediaAccountAdminService.java`
- Modify: `src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountAdminServiceImpl.java`
- Modify: `src/test/java/com/kasi/backend/promotion/controller/AdminMediaAccountControllerTest.java`
- Modify: `src/test/java/com/kasi/backend/promotion/service/MediaAccountServiceTest.java`

- [ ] **Step 1: 写管理员更新接口失败测试**

覆盖普通管理员成功更新、推广用户 403、非法状态返回业务校验错误、已加白禁止修改身份、未加白允许纠正身份、重复媒体账号拒绝、仅改名称时保留已加白状态。

```java
mockMvc.perform(put("/api/admin/promotion/media-accounts/{id}", mediaAccountId)
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "mediaType":"TIKTOK",
              "externalAccountId":"creator-1001",
              "accountName":"TikTok 新名称",
              "accountLink":"https://www.tiktok.com/@creator-1001",
              "status":1
            }
            """))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.mediaAccount.accountName").value("TikTok 新名称"));
```

- [ ] **Step 2: 运行测试并确认 405**

Run: `.\mvnw.cmd -Dtest=AdminMediaAccountControllerTest,MediaAccountServiceTest test`

Expected: FAIL，管理员 `PUT` 端点不存在或 Service 方法未定义。

- [ ] **Step 3: 创建管理员更新 DTO**

```java
@Data
public class AdminUpdateMediaAccountDTO {
    @NotNull private MediaType mediaType;
    @NotBlank @Size(max = 128) private String externalAccountId;
    @Size(max = 128) private String accountName;
    @Pattern(regexp = "^https://.+", message = "主页链接必须使用HTTPS")
    @Size(max = 512) private String accountLink;
    @NotNull @Min(0) @Max(1) private Integer status;
}
```

- [ ] **Step 4: 增加 Controller 和 Service 契约**

Controller：

```java
@PutMapping("/{id}")
public ApiResponse<AdminMediaAccountDetailVO> update(
        @PathVariable Long id,
        @Valid @RequestBody AdminUpdateMediaAccountDTO request) {
    return ApiResponse.success(mediaAccountAdminService.update(id, request));
}
```

Service：

```java
AdminMediaAccountDetailVO update(Long id, AdminUpdateMediaAccountDTO request);
```

- [ ] **Step 5: 实现管理员更新事务**

在 `MediaAccountAdminServiceImpl.update` 使用 `findByIdForUpdate` 锁定记录，计算 `identityChanged` 和 `detailsChanged`：

```java
boolean identityChanged = request.getMediaType() != account.getMediaType()
        || !externalId.equals(account.getExternalAccountId());
boolean approved = filings.stream().anyMatch(filing -> filing.getStatus() == FilingStatus.APPROVED);
if (identityChanged && approved) {
    throw new BusinessException(ErrorCode.MEDIA_ACCOUNT_IDENTITY_LOCKED);
}
```

身份变化前调用 `findByIdentity` 排除当前记录并抛出 `MEDIA_ACCOUNT_DUPLICATE`。资料变化时递增 `dataVersion`，更新账号资料，并对全部报备调用现有 `reschedule`：身份变化统一改为 `PENDING`，只改名称或链接时保留 `APPROVED`。最后单独更新账号状态，并通过 `getById(id)` 返回完整管理员详情。

- [ ] **Step 6: 运行更新接口测试**

Run: `.\mvnw.cmd -Dtest=AdminMediaAccountControllerTest,MediaAccountServiceTest test`

Expected: PASS，普通管理员可编辑，推广用户被拒绝，锁定和唯一性规则生效。

- [ ] **Step 7: 提交管理员更新接口**

```powershell
git add src/main/java/com/kasi/backend/promotion/dto/AdminUpdateMediaAccountDTO.java `
  src/main/java/com/kasi/backend/promotion/controller/AdminMediaAccountController.java `
  src/main/java/com/kasi/backend/promotion/service/MediaAccountAdminService.java `
  src/main/java/com/kasi/backend/promotion/service/impl/MediaAccountAdminServiceImpl.java `
  src/test/java/com/kasi/backend/promotion/controller/AdminMediaAccountControllerTest.java `
  src/test/java/com/kasi/backend/promotion/service/MediaAccountServiceTest.java
git commit -m "feat: allow admins to edit media accounts"
```

### Task 3: 创建前端媒体账号类型和 API

**Files:**
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/features/promotion/mediaAccountTypes.ts`
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/features/promotion/mediaAccountApi.ts`
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/features/promotion/mediaAccountApi.test.ts`

- [ ] **Step 1: 写 API 失败测试**

使用 MSW 断言列表查询参数、详情路径、更新请求体和失败重试路径：

```ts
expect(requestUrl.searchParams.get('userNo')).toBe('123456789012')
expect(requestUrl.searchParams.get('filingStatus')).toBe('FAILED')
expect(updateBody.accountLink).toBe('https://www.tiktok.com/@creator-1001')
expect(retryUrl.pathname).toBe(
  '/api/admin/promotion/media-accounts/8/filings/1/retry',
)
```

- [ ] **Step 2: 运行测试并确认模块不存在**

Run: `pnpm test -- src/features/promotion/mediaAccountApi.test.ts`

Expected: FAIL，`mediaAccountApi` 或类型模块不存在。

- [ ] **Step 3: 定义精确类型**

```ts
export type MediaType = 'FACEBOOK' | 'TIKTOK' | 'YOUTUBE' | 'INSTAGRAM'
export type FilingStatus = 'PENDING' | 'APPROVED' | 'FAILED'

export interface AdminMediaAccountListItem {
  id: number
  userNo: string
  nickname: string
  realName: string | null
  mediaType: MediaType
  externalAccountId: string
  accountName: string | null
  providerId: number | null
  status: number
  filingStatus: FilingStatus | null
  updatedAt: string
}

export interface AdminUpdateMediaAccountRequest {
  mediaType: MediaType
  externalAccountId: string
  accountName?: string
  accountLink?: string
  status: number
}
```

详情类型必须按后端结构定义为 `{ id, userNo, nickname, realName, mediaAccount }`，其中 `mediaAccount.filings` 包含 `providerId`、`providerName`、`status`、`externalFilingId`、`nextActionAt`、错误信息和时间字段。

- [ ] **Step 4: 实现 API 封装**

```ts
const basePath = '/api/admin/promotion/media-accounts'

export async function updateAdminMediaAccount(
  id: number,
  request: AdminUpdateMediaAccountRequest,
): Promise<AdminMediaAccountDetail> {
  const response = await httpClient.put<ApiResponse<AdminMediaAccountDetail>>(
    `${basePath}/${id}`,
    request,
  )
  return unwrapApiResponse(response.data)
}

export async function retryMediaFiling(id: number, providerId: number) {
  const response = await httpClient.post<ApiResponse<MediaFiling>>(
    `${basePath}/${id}/filings/${providerId}/retry`,
  )
  return unwrapApiResponse(response.data)
}
```

列表使用 `GET basePath` 和 `params`，详情使用 `GET ${basePath}/${id}`。

- [ ] **Step 5: 运行 API 测试**

Run: `pnpm test -- src/features/promotion/mediaAccountApi.test.ts`

Expected: PASS，四类请求路径和参数全部正确。

### Task 4: 实现管理员媒体账号报备页面

**Files:**
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/pages/promotion/MediaAccountFilingPage.tsx`
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/pages/promotion/media-account-filing-page.css`
- Create: `E:/JavaProjects/kasi-project/kasi-admin-web/src/pages/promotion/MediaAccountFilingPage.test.tsx`

- [ ] **Step 1: 写页面失败测试**

用 MSW 返回一条失败报备账号，覆盖：

```ts
expect(await screen.findByText('媒体账号报备')).toBeInTheDocument()
expect(await screen.findByText('TikTok 运营号')).toBeInTheDocument()
expect(screen.queryByRole('button', { name: '新增' })).not.toBeInTheDocument()
expect(screen.queryByRole('button', { name: '删除' })).not.toBeInTheDocument()
await user.click(screen.getByTestId('media-account-detail-8'))
expect(await screen.findByText('失败原因')).toBeInTheDocument()
expect(screen.getByRole('button', { name: '重试报备' })).toBeInTheDocument()
```

再覆盖编辑：未加白时媒体平台和账号 ID 可编辑；已加白时两个字段禁用；保存后发送完整更新请求并刷新列表和详情。

- [ ] **Step 2: 运行页面测试并确认组件不存在**

Run: `pnpm test -- src/pages/promotion/MediaAccountFilingPage.test.tsx`

Expected: FAIL，页面模块不存在。

- [ ] **Step 3: 实现 ProTable 列表和筛选**

页面使用：

```tsx
<PageContainer
  className="media-account-filing-page"
  title="媒体账号报备"
  content="查看推广用户媒体账号及短剧平台报备状态"
>
  <ProTable<AdminMediaAccountListItem>
    rowKey="id"
    columns={columns}
    request={loadMediaAccounts}
    toolBarRender={false}
    search={{ labelWidth: 88 }}
    scroll={{ x: 'max-content' }}
  />
</PageContainer>
```

筛选字段为用户编号、媒体平台、账号状态、短剧平台和报备状态；表格字段严格按设计文档展示，操作列只有“详情”。媒体类型、账号状态和报备状态分别通过集中映射显示中文标签。

- [ ] **Step 4: 实现详情、编辑和重试**

详情 Drawer 分为推广用户、媒体账号、平台报备三个未嵌套区块。编辑使用独立 Drawer 和 `Form`；当 `filings.some(item => item.status === 'APPROVED')` 时禁用 `mediaType` 和 `externalAccountId`。

失败报备行使用：

```tsx
{filing.status === 'FAILED' ? (
  <Button
    type="link"
    loading={retryingProviderId === filing.providerId}
    onClick={() => void handleRetry(filing.providerId)}
  >
    重试报备
  </Button>
) : null}
```

重试成功后重新请求详情并调用 `actionRef.current?.reload()`；失败时保留当前抽屉数据并展示统一错误消息。

- [ ] **Step 5: 实现响应式样式**

桌面端详情抽屉宽度 800，编辑抽屉宽度 560；移动端通过 `@media (max-width: 760px)` 将字段网格改为单列、减小抽屉内边距并允许表格横向滚动。页面区块使用现有 `management-page.css` 的间距、边框和标题层级，不创建嵌套卡片。

- [ ] **Step 6: 运行页面测试和类型检查**

Run:

```powershell
pnpm test -- src/pages/promotion/MediaAccountFilingPage.test.tsx
pnpm typecheck
```

Expected: PASS，且 TypeScript 零错误。

### Task 5: 接入路由、菜单、文档并完成验证

**Files:**
- Modify: `E:/JavaProjects/kasi-project/kasi-admin-web/src/router/AppRouter.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-admin-web/src/layouts/AdminLayout.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-admin-web/src/App.test.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-admin-web/README.md`
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-08-18-admin-media-account-filing-frontend-design.md`

- [ ] **Step 1: 写路由和菜单失败测试**

在 `App.test.tsx` 中登录普通管理员并访问 `/promotion/media-accounts`，断言侧栏显示“推广管理”和“媒体账号报备”，页面请求管理员媒体账号接口；管理员和超级管理员均可访问。

- [ ] **Step 2: 运行测试并确认路由回退**

Run: `pnpm test -- src/App.test.tsx`

Expected: FAIL，路由被重定向到 `/dashboard` 或菜单不存在。

- [ ] **Step 3: 注册懒加载路由和菜单**

在 `AppRouter.tsx` 增加：

```tsx
const MediaAccountFilingPage = lazy(() =>
  import('../pages/promotion/MediaAccountFilingPage').then((module) => ({
    default: module.MediaAccountFilingPage,
  })),
)

<Route
  path="/promotion/media-accounts"
  element={<MediaAccountFilingPage />}
/>
```

在 `AdminLayout.tsx` 增加“推广管理”子菜单，使用 Lucide `BadgeCheck` 或 `RadioTower` 图标，并让桌面和移动导航复用同一菜单定义。

- [ ] **Step 4: 更新前后端文档状态**

前端 `README.md` 记录页面路由、功能和接口；后端 `README.md`、`AGENTS.md` 和专项设计文档区分“后端管理员编辑接口已实现”和“管理员前端已接入”的当前状态，不将推广用户端页面描述为已完成。

- [ ] **Step 5: 运行完整自动化验证**

后端：

```powershell
$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd test
.\mvnw.cmd -DskipTests compile
git diff --check
```

前端：

```powershell
pnpm test
pnpm typecheck
pnpm lint
pnpm format:check
pnpm build
```

Expected: 所有命令退出码为 0，测试输出零失败、零错误。

- [ ] **Step 6: 启动开发服务器并做浏览器验收**

后端启动后，在前端运行 `pnpm dev -- --host 127.0.0.1`。使用浏览器分别验证 1440×900 和 390×844：列表非空、筛选不重叠、无新增/删除按钮、详情抽屉字段完整、已加白身份字段禁用、失败重试可见、移动端无文本遮挡和非预期横向溢出。

- [ ] **Step 7: 提交后端代码和文档**

只暂存本模块的后端代码、测试和文档，确认不包含 `src/main/resources/application.properties` 与 `dump.rdb`：

```powershell
git status --short
git diff --cached --name-only
git commit -m "feat: add admin media account filing management"
```

前端目录没有 Git 元数据，本计划不创建新仓库、不移动目录，也不伪造前端提交。
