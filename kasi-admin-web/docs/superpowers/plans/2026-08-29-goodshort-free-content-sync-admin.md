# GoodShort Free Content Sync Admin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 `/drama/catalog` 管理页面接入 GoodShort 免费剧集的单部、勾选批量、全平台同步和单部任务状态查询能力。

**Architecture:** 保留现有目录同步弹窗和状态抽屉，新增独立的免费剧集同步弹窗与详情状态区段。API 层继续使用现有 `httpClient`/`unwrapApiResponse`，仅在状态接口把业务码 `6017` 映射为 `null`；页面管理表格选择、单部/批量命令与详情刷新，子组件分别管理全平台表单和单部任务轮询。

**Tech Stack:** React 19、TypeScript 6、Ant Design 6、Ant Design Pro Components、Axios、Lucide React、Vitest、React Testing Library、MSW、pnpm 11

---

## File Map

- Modify: `kasi-admin-web/src/features/drama/dramaCatalogTypes.ts` - 定义免费剧集同步请求、任务、批量结果和状态类型。
- Modify: `kasi-admin-web/src/features/drama/dramaCatalogApi.ts` - 封装四个管理员同步接口，并把状态查询的 `6017` 转成无任务状态。
- Modify: `kasi-admin-web/src/features/drama/dramaCatalogApi.test.ts` - 验证四个接口的路径、请求体、响应和 `6017` 映射。
- Create: `kasi-admin-web/src/pages/drama/DramaContentSyncModal.tsx` - 管理全平台免费剧集同步表单、提交状态和统计结果提示。
- Create: `kasi-admin-web/src/pages/drama/DramaContentSyncModal.test.tsx` - 验证默认全量、补齐缺失、语言选择、成功结果和失败保留表单。
- Create: `kasi-admin-web/src/pages/drama/DramaContentSyncSection.tsx` - 查询、展示和轮询单部短剧的剧集同步任务。
- Create: `kasi-admin-web/src/pages/drama/DramaContentSyncSection.test.tsx` - 验证无任务、四种状态、轮询停止、手动刷新和错误行为。
- Modify: `kasi-admin-web/src/pages/drama/DramaCatalogPage.tsx` - 接入行级、勾选批量、全平台入口和详情任务区段。
- Modify: `kasi-admin-web/src/pages/drama/DramaCatalogPage.test.tsx` - 验证页面入口、最多 100 部、选择清理、单部/批量提交和详情成功刷新。
- Modify: `kasi-admin-web/src/pages/drama/drama-catalog-page.css` - 补充工具栏结果、同步状态区段和窄屏布局。
- Modify: `kasi-admin-web/README.md` - 把管理端免费剧集同步写入当前已实现能力和 API 清单。
- Modify: `kasi-admin-web/docs/superpowers/specs/2026-08-29-goodshort-free-content-sync-admin-design.md` - 实施完成后将状态从“待实施”更新为“已实施”，不改动已批准边界。

### Task 1: Add The Free Content Sync API Contract

**Files:**

- Modify: `kasi-admin-web/src/features/drama/dramaCatalogTypes.ts`
- Modify: `kasi-admin-web/src/features/drama/dramaCatalogApi.ts`
- Test: `kasi-admin-web/src/features/drama/dramaCatalogApi.test.ts`

- [ ] **Step 1: Write failing API tests for all four endpoints**

Extend the imports in `dramaCatalogApi.test.ts` and add one focused test. The test records all request bodies and verifies that `6017` is represented as `null` instead of a thrown global error:

```tsx
import {
  getDramaCatalogDetail,
  getDramaContentSyncStatus,
  listDramaCatalog,
  listDramaSyncStatuses,
  requestAllDramaContentSync,
  requestDramaCatalogSync,
  requestDramaContentBatchSync,
  requestDramaContentSync,
  updateDramaLocalStatus,
} from './dramaCatalogApi'

it('calls all free content sync endpoints and maps missing status to null', async () => {
  let singleRequested = false
  let batchBody: unknown
  let allBody: unknown
  let statusRequestCount = 0
  const task = {
    id: 51,
    dramaId: 8,
    status: 'REQUESTED',
    requestedAt: '2026-08-29T08:00:00',
    nextRunAt: '2026-08-29T08:00:03',
    retryCount: 0,
    totalFetched: 0,
    insertedCount: 0,
    updatedCount: 0,
    lastErrorCode: null,
    lastErrorMessage: null,
  }

  server.use(
    http.post('/api/admin/drama/catalog/8/contents/sync', () => {
      singleRequested = true
      return HttpResponse.json({ code: 0, message: 'ok', data: task })
    }),
    http.post('/api/admin/drama/catalog/contents/sync', async ({ request }) => {
      batchBody = await request.json()
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: {
          requestedCount: 2,
          queuedCount: 1,
          skippedCount: 1,
          invalidCount: 0,
          tasks: [task],
        },
      })
    }),
    http.post(
      '/api/admin/drama/catalog/contents/sync/all',
      async ({ request }) => {
        allBody = await request.json()
        return HttpResponse.json({
          code: 0,
          message: 'ok',
          data: {
            requestedCount: 24,
            queuedCount: 20,
            skippedCount: 3,
            invalidCount: 1,
            tasks: [],
          },
        })
      },
    ),
    http.get('/api/admin/drama/catalog/8/contents/sync/status', () => {
      statusRequestCount += 1
      return statusRequestCount === 1
        ? HttpResponse.json({ code: 0, message: 'ok', data: task })
        : HttpResponse.json({
            code: 6017,
            message: '短剧剧集同步任务不存在',
            data: null,
          })
    }),
  )

  await expect(requestDramaContentSync(8)).resolves.toEqual(task)
  await expect(requestDramaContentBatchSync([8, 9])).resolves.toEqual(
    expect.objectContaining({ requestedCount: 2, skippedCount: 1 }),
  )
  await expect(
    requestAllDramaContentSync({
      providerId: 1,
      language: 'ENGLISH',
      missingOnly: false,
    }),
  ).resolves.toEqual(expect.objectContaining({ requestedCount: 24 }))
  await expect(getDramaContentSyncStatus(8)).resolves.toEqual(task)
  await expect(getDramaContentSyncStatus(8)).resolves.toBeNull()

  expect(singleRequested).toBe(true)
  expect(batchBody).toEqual({ dramaIds: [8, 9] })
  expect(allBody).toEqual({
    providerId: 1,
    language: 'ENGLISH',
    missingOnly: false,
  })
})

it('uses the approved message when a single content task is already running', async () => {
  server.use(
    http.post('/api/admin/drama/catalog/8/contents/sync', () =>
      HttpResponse.json({
        code: 6016,
        message: '短剧剧集同步任务正在执行',
        data: null,
      }),
    ),
  )

  await expect(requestDramaContentSync(8)).rejects.toThrow(
    '该短剧的剧集同步任务正在执行',
  )
})
```

- [ ] **Step 2: Run the API test and verify it fails**

Run:

```powershell
cd kasi-admin-web
pnpm test -- src/features/drama/dramaCatalogApi.test.ts
```

Expected: FAIL because the four content-sync exports and their types do not exist.

- [ ] **Step 3: Add the exact TypeScript contract**

Append these declarations to `dramaCatalogTypes.ts`:

```ts
export type DramaContentSyncStatus =
  'REQUESTED' | 'RUNNING' | 'SUCCESS' | 'FAILED'

export interface DramaContentSyncTask {
  id: number
  dramaId: number
  status: DramaContentSyncStatus
  requestedAt: string
  nextRunAt: string | null
  retryCount: number
  totalFetched: number
  insertedCount: number
  updatedCount: number
  lastErrorCode: string | null
  lastErrorMessage: string | null
}

export interface DramaContentSyncBatchResult {
  requestedCount: number
  queuedCount: number
  skippedCount: number
  invalidCount: number
  tasks: DramaContentSyncTask[]
}

export interface RequestAllDramaContentSync {
  providerId: number
  language?: string
  missingOnly: boolean
}
```

- [ ] **Step 4: Implement the four API functions**

Add the new types to `dramaCatalogApi.ts` imports, then add:

```ts
export async function requestDramaContentSync(
  id: number,
): Promise<DramaContentSyncTask> {
  const response = await httpClient.post<ApiResponse<DramaContentSyncTask>>(
    `${basePath}/${id}/contents/sync`,
  )
  if (response.data.code === 6016) {
    throw new Error('该短剧的剧集同步任务正在执行')
  }
  return unwrapApiResponse(response.data)
}

export async function requestDramaContentBatchSync(
  dramaIds: number[],
): Promise<DramaContentSyncBatchResult> {
  const response = await httpClient.post<
    ApiResponse<DramaContentSyncBatchResult>
  >(`${basePath}/contents/sync`, { dramaIds })
  return unwrapApiResponse(response.data)
}

export async function requestAllDramaContentSync(
  request: RequestAllDramaContentSync,
): Promise<DramaContentSyncBatchResult> {
  const response = await httpClient.post<
    ApiResponse<DramaContentSyncBatchResult>
  >(`${basePath}/contents/sync/all`, request)
  return unwrapApiResponse(response.data)
}

export async function getDramaContentSyncStatus(
  id: number,
): Promise<DramaContentSyncTask | null> {
  const response = await httpClient.get<ApiResponse<DramaContentSyncTask>>(
    `${basePath}/${id}/contents/sync/status`,
  )
  if (response.data.code === 6017) return null
  return unwrapApiResponse(response.data)
}
```

Do not modify the shared Axios interceptor or `unwrapApiResponse`: only this status endpoint treats `6017` as an expected empty state. Other `401/403/503` and business errors keep the existing behavior.

- [ ] **Step 5: Run the API test and verify it passes**

Run:

```powershell
cd kasi-admin-web
pnpm test -- src/features/drama/dramaCatalogApi.test.ts
```

Expected: the API test file passes with all four new endpoint assertions.

- [ ] **Step 6: Commit the API contract**

Run from the monorepo root:

```powershell
git add kasi-admin-web/src/features/drama/dramaCatalogTypes.ts kasi-admin-web/src/features/drama/dramaCatalogApi.ts kasi-admin-web/src/features/drama/dramaCatalogApi.test.ts
git diff --cached --check
git commit -m "feat(admin): add free episode sync api"
```

Expected: one commit containing only the API types, wrapper functions, and API tests.

### Task 2: Build The All-Online Free Content Sync Modal

**Files:**

- Create: `kasi-admin-web/src/pages/drama/DramaContentSyncModal.tsx`
- Create: `kasi-admin-web/src/pages/drama/DramaContentSyncModal.test.tsx`

- [ ] **Step 1: Write failing modal tests**

Create `DramaContentSyncModal.test.tsx` with MSW and Ant Design `App`. Cover both range values, optional language, result text, callback, and retry behavior:

```tsx
import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App as AntdApp } from 'antd'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import {
  afterAll,
  afterEach,
  beforeAll,
  describe,
  expect,
  it,
  vi,
} from 'vitest'
import { DramaContentSyncModal } from './DramaContentSyncModal'

const server = setupServer()
const providers = [
  {
    id: 1,
    providerCode: 'GOODSHORT',
    providerName: 'GoodShort',
    status: 1,
    capabilities: ['FREE_CONTENT_PREVIEW' as const],
    connection: {
      id: 11,
      connectionName: 'GoodShort production',
      baseUrl: 'https://example.com',
      partnerId: 'pid',
      currency: 'USD',
      status: 1,
      credentialConfigured: true,
      createdAt: '2026-08-20T08:00:00',
      updatedAt: '2026-08-20T08:00:00',
    },
  },
]

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

function renderModal(onSubmitted = vi.fn()) {
  render(
    <AntdApp>
      <DramaContentSyncModal
        open
        providers={providers}
        preferredProviderId={1}
        onClose={vi.fn()}
        onSubmitted={onSubmitted}
      />
    </AntdApp>,
  )
  return onSubmitted
}

describe('DramaContentSyncModal', () => {
  it('submits all online dramas by default with no language', async () => {
    let body: unknown
    server.use(
      http.post(
        '/api/admin/drama/catalog/contents/sync/all',
        async ({ request }) => {
          body = await request.json()
          return HttpResponse.json({
            code: 0,
            message: 'ok',
            data: {
              requestedCount: 24,
              queuedCount: 20,
              skippedCount: 3,
              invalidCount: 1,
              tasks: [],
            },
          })
        },
      ),
    )
    const onSubmitted = renderModal()
    const user = userEvent.setup()
    const modal = await screen.findByTestId('drama-content-sync-modal')

    expect(
      within(modal).getByText('当前仅同步 GoodShort 免费剧集'),
    ).toBeInTheDocument()
    expect(
      within(modal)
        .getByText('同步全部在线短剧')
        .closest('.ant-segmented-item'),
    ).toHaveClass('ant-segmented-item-selected')
    await user.click(within(modal).getByTestId('drama-content-sync-submit'))

    await waitFor(() =>
      expect(body).toEqual({ providerId: 1, missingOnly: false }),
    )
    expect(
      await screen.findByText(
        '匹配 24 部，排队 20 部，运行中跳过 3 部，无效 1 部',
      ),
    ).toBeInTheDocument()
    expect(onSubmitted).toHaveBeenCalledWith(1)
  })

  it('submits missing-only with a selected language and preserves values on failure', async () => {
    let body: unknown
    let attempts = 0
    server.use(
      http.post(
        '/api/admin/drama/catalog/contents/sync/all',
        async ({ request }) => {
          attempts += 1
          body = await request.json()
          if (attempts === 1) {
            return HttpResponse.json({
              code: 6016,
              message: '短剧剧集同步任务正在执行',
              data: null,
            })
          }
          return HttpResponse.json({
            code: 0,
            message: 'ok',
            data: {
              requestedCount: 8,
              queuedCount: 8,
              skippedCount: 0,
              invalidCount: 0,
              tasks: [],
            },
          })
        },
      ),
    )
    renderModal()
    const user = userEvent.setup()
    const modal = await screen.findByTestId('drama-content-sync-modal')

    await user.click(within(modal).getByText('仅补齐缺失视频地址'))
    await user.click(within(modal).getByRole('combobox', { name: '语言' }))
    await user.click(await screen.findByText('英语'))
    await user.click(within(modal).getByTestId('drama-content-sync-submit'))

    expect(
      await screen.findByText('短剧剧集同步任务正在执行'),
    ).toBeInTheDocument()
    expect(
      within(modal)
        .getByText('仅补齐缺失视频地址')
        .closest('.ant-segmented-item'),
    ).toHaveClass('ant-segmented-item-selected')
    await user.click(within(modal).getByTestId('drama-content-sync-submit'))
    await waitFor(() =>
      expect(body).toEqual({
        providerId: 1,
        language: 'ENGLISH',
        missingOnly: true,
      }),
    )
  })
})
```

- [ ] **Step 2: Run the modal test and verify it fails**

Run:

```powershell
cd kasi-admin-web
pnpm test -- src/pages/drama/DramaContentSyncModal.test.tsx
```

Expected: FAIL because `DramaContentSyncModal.tsx` does not exist.

- [ ] **Step 3: Implement the modal**

Create `DramaContentSyncModal.tsx`. Use `Alert`, `Form`, `Modal`, `Segmented`, and `Select`; use this request construction so an empty language is omitted rather than sent as an empty string:

```tsx
import {
  Alert,
  App as AntdApp,
  Button,
  Form,
  Modal,
  Segmented,
  Select,
} from 'antd'
import { useEffect, useState } from 'react'
import { isUnauthorizedError } from '../../api/http'
import { requestAllDramaContentSync } from '../../features/drama/dramaCatalogApi'
import { dramaLanguageLabels } from '../../features/drama/dramaCatalogLocale'
import type { RequestAllDramaContentSync } from '../../features/drama/dramaCatalogTypes'
import type { DramaProvider } from '../../features/provider/providerTypes'

type ContentSyncRange = 'ALL' | 'MISSING'

interface ContentSyncFormValues {
  providerId: number
  language?: string
  syncRange: ContentSyncRange
}

interface DramaContentSyncModalProps {
  open: boolean
  providers: DramaProvider[]
  preferredProviderId: number | null
  onClose: () => void
  onSubmitted: (providerId: number) => void
}

const languageOptions = Object.entries(dramaLanguageLabels).map(
  ([value, label]) => ({ value, label }),
)

export function DramaContentSyncModal({
  open,
  providers,
  preferredProviderId,
  onClose,
  onSubmitted,
}: DramaContentSyncModalProps) {
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<ContentSyncFormValues>()
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!open) return
    const providerId =
      preferredProviderId !== null &&
      providers.some((provider) => provider.id === preferredProviderId)
        ? preferredProviderId
        : providers[0]?.id
    form.setFieldsValue({
      providerId: providerId ?? undefined,
      language: undefined,
      syncRange: 'ALL',
    })
  }, [form, open, preferredProviderId, providers])

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      setSubmitting(true)
      const request: RequestAllDramaContentSync = {
        providerId: values.providerId,
        missingOnly: values.syncRange === 'MISSING',
        ...(values.language ? { language: values.language } : {}),
      }
      const result = await requestAllDramaContentSync(request)
      message.success(
        `匹配 ${result.requestedCount} 部，排队 ${result.queuedCount} 部，运行中跳过 ${result.skippedCount} 部，无效 ${result.invalidCount} 部`,
      )
      onSubmitted(values.providerId)
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return
      if (isUnauthorizedError(error)) return
      message.error(
        error instanceof Error ? error.message : '免费剧集同步任务提交失败',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title="同步免费剧集"
      open={open}
      width={540}
      closable={!submitting}
      maskClosable={!submitting}
      keyboard={!submitting}
      data-testid="drama-content-sync-modal"
      footer={
        <div className="drama-catalog-page__modal-footer">
          <Button disabled={submitting} onClick={onClose}>
            取消
          </Button>
          <Button
            type="primary"
            loading={submitting}
            data-testid="drama-content-sync-submit"
            onClick={() => void handleSubmit()}
          >
            提交任务
          </Button>
        </div>
      }
      onCancel={submitting ? undefined : onClose}
    >
      <Alert type="info" showIcon message="当前仅同步 GoodShort 免费剧集" />
      <Form form={form} layout="vertical" preserve>
        <Form.Item
          label="短剧平台"
          name="providerId"
          rules={[{ required: true, message: '请选择短剧平台' }]}
        >
          <Select
            options={providers.map((provider) => ({
              value: provider.id,
              label: provider.providerName,
            }))}
          />
        </Form.Item>
        <Form.Item label="语言" name="language">
          <Select
            allowClear
            placeholder="留空同步该平台全部已同步语言"
            options={languageOptions}
          />
        </Form.Item>
        <Form.Item label="同步范围" name="syncRange">
          <Segmented<ContentSyncRange>
            block
            options={[
              { value: 'ALL', label: '同步全部在线短剧' },
              { value: 'MISSING', label: '仅补齐缺失视频地址' },
            ]}
          />
        </Form.Item>
      </Form>
    </Modal>
  )
}
```

- [ ] **Step 4: Run the modal test and verify it passes**

Run:

```powershell
cd kasi-admin-web
pnpm test -- src/pages/drama/DramaContentSyncModal.test.tsx
```

Expected: both modal tests pass, including the selected segmented option assertions.

- [ ] **Step 5: Commit the modal**

Run from the monorepo root:

```powershell
git add kasi-admin-web/src/pages/drama/DramaContentSyncModal.tsx kasi-admin-web/src/pages/drama/DramaContentSyncModal.test.tsx
git diff --cached --check
git commit -m "feat(admin): add free episode sync modal"
```

Expected: one commit containing only the modal and its focused tests.

### Task 3: Build The Per-Drama Sync Status Section

**Files:**

- Create: `kasi-admin-web/src/pages/drama/DramaContentSyncSection.tsx`
- Create: `kasi-admin-web/src/pages/drama/DramaContentSyncSection.test.tsx`

- [ ] **Step 1: Write failing status-section tests**

Create `DramaContentSyncSection.test.tsx`. Use `vi.useFakeTimers()` only in the polling test and restore real timers afterward. The test data must cover `REQUESTED`, `RUNNING`, `SUCCESS`, and `FAILED`; the essential assertions are:

```tsx
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App as AntdApp } from 'antd'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import {
  afterAll,
  afterEach,
  beforeAll,
  describe,
  expect,
  it,
  vi,
} from 'vitest'
import type { ComponentProps } from 'react'
import type { DramaContentSyncStatus } from '../../features/drama/dramaCatalogTypes'
import { DramaContentSyncSection } from './DramaContentSyncSection'

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
  vi.useRealTimers()
})
afterAll(() => server.close())

function contentTask(status: DramaContentSyncStatus) {
  return {
    id: 51,
    dramaId: 8,
    status,
    requestedAt: '2026-08-29T08:00:00',
    nextRunAt:
      status === 'SUCCESS' || status === 'FAILED'
        ? null
        : '2026-08-29T08:00:03',
    retryCount: status === 'FAILED' ? 3 : 0,
    totalFetched: 12,
    insertedCount: 10,
    updatedCount: 2,
    lastErrorCode: status === 'FAILED' ? 'PROVIDER_REMOTE_UNAVAILABLE' : null,
    lastErrorMessage: status === 'FAILED' ? 'GoodShort 暂时不可用' : null,
  }
}

function statusHandler(status: DramaContentSyncStatus) {
  return http.get('/api/admin/drama/catalog/8/contents/sync/status', () =>
    HttpResponse.json({ code: 0, message: 'ok', data: contentTask(status) }),
  )
}

function sectionElement(
  overrides: Partial<ComponentProps<typeof DramaContentSyncSection>> = {},
) {
  return (
    <AntdApp>
      <DramaContentSyncSection
        dramaId={8}
        active
        refreshKey={0}
        onSucceeded={vi.fn()}
        {...overrides}
      />
    </AntdApp>
  )
}

function renderSection(
  overrides: Partial<ComponentProps<typeof DramaContentSyncSection>> = {},
) {
  return render(sectionElement(overrides))
}

it('shows the expected empty state for business code 6017', async () => {
  server.use(
    http.get('/api/admin/drama/catalog/8/contents/sync/status', () =>
      HttpResponse.json({
        code: 6017,
        message: '短剧剧集同步任务不存在',
        data: null,
      }),
    ),
  )
  renderSection()
  expect(await screen.findByText('尚未提交剧集同步任务')).toBeInTheDocument()
  expect(screen.queryByText('短剧剧集同步任务不存在')).not.toBeInTheDocument()
})

it.each([
  ['REQUESTED', '等待执行'],
  ['RUNNING', '运行中'],
  ['SUCCESS', '同步成功'],
  ['FAILED', '同步失败'],
] as const)('renders %s as %s', async (status, label) => {
  server.use(statusHandler(status))
  renderSection()
  expect(await screen.findByText(label)).toBeInTheDocument()
  expect(screen.getByText(/获取 12.*新增 10.*更新 2/)).toBeInTheDocument()
  if (status === 'FAILED') {
    expect(screen.getByText('PROVIDER_REMOTE_UNAVAILABLE')).toBeInTheDocument()
    expect(screen.getByText('GoodShort 暂时不可用')).toBeInTheDocument()
  }
})

it('polls every three seconds and stops after success', async () => {
  vi.useFakeTimers({ shouldAdvanceTime: true })
  let requests = 0
  const onSucceeded = vi.fn()
  server.use(
    http.get('/api/admin/drama/catalog/8/contents/sync/status', () => {
      requests += 1
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: contentTask(requests === 1 ? 'RUNNING' : 'SUCCESS'),
      })
    }),
  )
  renderSection({ onSucceeded })
  await screen.findByText('运行中')
  await vi.advanceTimersByTimeAsync(3_000)
  expect(await screen.findByText('同步成功')).toBeInTheDocument()
  expect(onSucceeded).toHaveBeenCalledTimes(1)
  await vi.advanceTimersByTimeAsync(6_000)
  expect(requests).toBe(2)
  vi.useRealTimers()
})

it('stops polling when inactive and allows a manual refresh', async () => {
  let requests = 0
  server.use(
    http.get('/api/admin/drama/catalog/8/contents/sync/status', () => {
      requests += 1
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: contentTask('RUNNING'),
      })
    }),
  )
  const view = renderSection()
  await screen.findByText('运行中')
  await userEvent.click(
    screen.getByRole('button', { name: '刷新剧集同步状态' }),
  )
  await waitFor(() => expect(requests).toBe(2))
  view.rerender(sectionElement({ active: false }))
  expect(
    screen.queryByTestId('drama-content-sync-section'),
  ).not.toBeInTheDocument()
})

it('stops a running poll after sixty seconds', async () => {
  vi.useFakeTimers({ shouldAdvanceTime: true })
  let requests = 0
  server.use(
    http.get('/api/admin/drama/catalog/8/contents/sync/status', () => {
      requests += 1
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: contentTask('RUNNING'),
      })
    }),
  )
  renderSection()
  await screen.findByText('运行中')
  await vi.advanceTimersByTimeAsync(60_000)
  const requestsAtLimit = requests
  await vi.advanceTimersByTimeAsync(6_000)
  expect(requests).toBe(requestsAtLimit)
})

it.each([
  [403, 'Request failed with status code 403'],
  [503, 'Request failed with status code 503'],
] as const)(
  'keeps the section visible for HTTP %s',
  async (status, expected) => {
    server.use(
      http.get('/api/admin/drama/catalog/8/contents/sync/status', () =>
        HttpResponse.json(
          { code: status, message: expected, data: null },
          { status },
        ),
      ),
    )
    renderSection()
    expect(await screen.findByText(expected)).toBeInTheDocument()
  },
)

it('shows an ordinary business error locally', async () => {
  server.use(
    http.get('/api/admin/drama/catalog/8/contents/sync/status', () =>
      HttpResponse.json({ code: 6008, message: '短剧不存在', data: null }),
    ),
  )
  renderSection()
  expect(await screen.findByText('短剧不存在')).toBeInTheDocument()
})

it('does not render a duplicate local error for HTTP 401', async () => {
  server.use(
    http.get('/api/admin/drama/catalog/8/contents/sync/status', () =>
      HttpResponse.json(
        { code: 2001, message: '未登录', data: null },
        { status: 401 },
      ),
    ),
  )
  renderSection()
  await waitFor(() =>
    expect(screen.queryByText('未登录')).not.toBeInTheDocument(),
  )
})
```

- [ ] **Step 2: Run the section test and verify it fails**

Run:

```powershell
cd kasi-admin-web
pnpm test -- src/pages/drama/DramaContentSyncSection.test.tsx
```

Expected: FAIL because the status component does not exist.

- [ ] **Step 3: Implement status loading, display, and bounded polling**

Create `DramaContentSyncSection.tsx` with this public contract:

```tsx
interface DramaContentSyncSectionProps {
  dramaId: number
  active: boolean
  refreshKey: number
  onSucceeded: () => void
}

export function DramaContentSyncSection({
  dramaId,
  active,
  refreshKey,
  onSucceeded,
}: DramaContentSyncSectionProps) {
  const [task, setTask] = useState<DramaContentSyncTask | null>(null)
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const pollStartedAt = useRef(0)
  const lastNotifiedSuccessTaskId = useRef<number | null>(null)
  const onSucceededRef = useRef(onSucceeded)

  useEffect(() => {
    onSucceededRef.current = onSucceeded
  }, [onSucceeded])

  const loadStatus = useCallback(async () => {
    setLoading(true)
    try {
      const result = await getDramaContentSyncStatus(dramaId)
      setTask(result)
      setErrorMessage(null)
      if (
        result?.status === 'SUCCESS' &&
        lastNotifiedSuccessTaskId.current !== result.id
      ) {
        lastNotifiedSuccessTaskId.current = result.id
        onSucceededRef.current()
      }
    } catch (error) {
      if (isUnauthorizedError(error)) return
      setErrorMessage(
        error instanceof Error ? error.message : '剧集同步状态加载失败',
      )
    } finally {
      setLoading(false)
    }
  }, [dramaId])
```

Continue inside the same component with these effects. The loader calls `getDramaContentSyncStatus(dramaId)`, stores `null` as the no-task state, suppresses only HTTP 401, and stores all other error messages locally in the section:

```ts
const POLL_INTERVAL_MS = 3_000
const MAX_POLL_DURATION_MS = 60_000

useEffect(() => {
  if (!active) return
  pollStartedAt.current = Date.now()
  void loadStatus()
}, [active, dramaId, loadStatus, refreshKey])

useEffect(() => {
  if (
    !active ||
    !task ||
    (task.status !== 'REQUESTED' && task.status !== 'RUNNING')
  )
    return
  if (Date.now() - pollStartedAt.current >= MAX_POLL_DURATION_MS) return
  const timer = window.setTimeout(() => void loadStatus(), POLL_INTERVAL_MS)
  return () => window.clearTimeout(timer)
}, [active, loadStatus, task])
```

Import `Descriptions`, `Spin`, `Tag`, `Tooltip`, and `Button` from Ant Design, `RefreshCw` from Lucide, the API/type dependencies above, and React hooks used by the snippet. Return `null` when `active` is false. Use this render body after the effects:

```tsx
const statusLabels: Record<DramaContentSyncStatus, string> = {
  REQUESTED: '等待执行',
  RUNNING: '运行中',
  SUCCESS: '同步成功',
  FAILED: '同步失败',
}

const refresh = () => {
  pollStartedAt.current = Date.now()
  void loadStatus()
}

if (!active) return null

return (
  <section
    className="drama-catalog-page__section"
    data-testid="drama-content-sync-section"
  >
    <div className="drama-catalog-page__section-heading">
      <h3>剧集同步</h3>
      <Tooltip title="刷新剧集同步状态">
        <Button
          aria-label="刷新剧集同步状态"
          icon={<RefreshCw size={16} />}
          loading={loading}
          onClick={refresh}
        />
      </Tooltip>
    </div>
    <Spin spinning={loading}>
      {errorMessage ? (
        <div className="drama-catalog-page__content-sync-error">
          {errorMessage}
        </div>
      ) : task ? (
        <>
          <div className="drama-catalog-page__content-sync-status">
            <Tag
              color={
                task.status === 'SUCCESS'
                  ? 'success'
                  : task.status === 'FAILED'
                    ? 'error'
                    : 'processing'
              }
            >
              {statusLabels[task.status]}
            </Tag>
          </div>
          <Descriptions column={2} size="small">
            <Descriptions.Item label="请求时间">
              {formatDate(task.requestedAt)}
            </Descriptions.Item>
            <Descriptions.Item label="下次执行时间">
              {formatDate(task.nextRunAt)}
            </Descriptions.Item>
            <Descriptions.Item label="重试次数">
              {task.retryCount}
            </Descriptions.Item>
            <Descriptions.Item label="本次统计">
              获取 {task.totalFetched} / 新增 {task.insertedCount} / 更新{' '}
              {task.updatedCount}
            </Descriptions.Item>
          </Descriptions>
          {task.lastErrorCode || task.lastErrorMessage ? (
            <div className="drama-catalog-page__content-sync-error">
              {task.lastErrorCode ? <code>{task.lastErrorCode}</code> : null}
              {task.lastErrorMessage ? <div>{task.lastErrorMessage}</div> : null}
            </div>
          ) : null}
        </>
      ) : (
        <span>尚未提交剧集同步任务</span>
      )}
    </Spin>
  </section>
)
}
```

Define the local formatter exactly as the existing page does:

```ts
function formatDate(value: string | null | undefined) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}
```

When a returned task is `SUCCESS`, call `onSucceeded()` once per task ID by comparing `lastNotifiedSuccessTaskId.current`. Render an unframed `<section className="drama-catalog-page__section" data-testid="drama-content-sync-section">` containing:

- Heading “剧集同步” and an icon-only `RefreshCw` button wrapped in `Tooltip`, with `aria-label="刷新剧集同步状态"`.
- `Spin` while loading.
- “尚未提交剧集同步任务” when `task === null` and no error.
- A status `Tag` using labels `等待执行/运行中/同步成功/同步失败`.
- `Descriptions` for `requestedAt`, `nextRunAt`, `retryCount`, and statistics text `获取 N / 新增 N / 更新 N`.
- A visible local error line for failed requests, and `lastErrorCode` plus `lastErrorMessage` only when supplied by the task.

- [ ] **Step 4: Run the section test and verify it passes**

Run:

```powershell
cd kasi-admin-web
pnpm test -- src/pages/drama/DramaContentSyncSection.test.tsx
```

Expected: empty, status, polling, terminal, refresh, and error tests all pass without pending fake timers.

- [ ] **Step 5: Commit the detail status section**

Run from the monorepo root:

```powershell
git add kasi-admin-web/src/pages/drama/DramaContentSyncSection.tsx kasi-admin-web/src/pages/drama/DramaContentSyncSection.test.tsx
git diff --cached --check
git commit -m "feat(admin): show free episode sync status"
```

Expected: one commit containing only the detail status component and tests.

### Task 4: Integrate Single, Selected, And All Sync Actions

**Files:**

- Modify: `kasi-admin-web/src/pages/drama/DramaCatalogPage.tsx`
- Modify: `kasi-admin-web/src/pages/drama/DramaCatalogPage.test.tsx`
- Modify: `kasi-admin-web/src/pages/drama/drama-catalog-page.css`

- [ ] **Step 1: Extend the ProTable test double for row selection and search/page changes**

Update the mocked `ProTable` in `DramaCatalogPage.test.tsx` to accept `rowSelection`, `onSubmit`, `onReset`, and `pagination`. Render a checkbox for each row and test-only buttons that invoke search/reset/page change callbacks:

```tsx
const ProTable = ({
  actionRef,
  columns,
  request,
  toolBarRender,
  rowSelection,
  onSubmit,
  onReset,
  pagination,
}: any) => {
  const [rows, setRows] = React.useState<any[]>([])
  const load = React.useCallback(async () => {
    const result = await request({ current: 1, pageSize: 20 })
    setRows(result.data ?? [])
  }, [request])

  React.useEffect(() => {
    void load()
  }, [load])

  React.useEffect(() => {
    if (actionRef) actionRef.current = { reload: load }
  }, [actionRef, load])

  return (
    <div data-testid="mock-pro-table">
      <div>{typeof toolBarRender === 'function' ? toolBarRender() : null}</div>
      <button
        data-testid="mock-search-submit"
        onClick={() => onSubmit?.({ title: 'Reborn' })}
      >
        查询
      </button>
      <button data-testid="mock-search-reset" onClick={() => onReset?.()}>
        重置
      </button>
      <button
        data-testid="mock-page-change"
        onClick={() => pagination?.onChange?.(2, 20)}
      >
        下一页
      </button>
      {rows.map((row) => {
        const checked = rowSelection?.selectedRowKeys?.includes(row.id) ?? false
        const disabled =
          rowSelection?.getCheckboxProps?.(row)?.disabled ?? false
        return (
          <div key={row.id} data-testid={`mock-row-${row.id}`}>
            {rowSelection ? (
              <input
                type="checkbox"
                aria-label={`选择短剧 ${row.id}`}
                checked={checked}
                disabled={disabled}
                onChange={() => {
                  const keys = checked
                    ? rowSelection.selectedRowKeys.filter(
                        (key: number) => key !== row.id,
                      )
                    : [...rowSelection.selectedRowKeys, row.id]
                  rowSelection.onChange(
                    keys,
                    rows.filter((item) => keys.includes(item.id)),
                  )
                }}
              />
            ) : null}
            {columns
              .filter((column: any) => !column.hideInTable)
              .map((column: any, index: number) => {
                const value = column.dataIndex
                  ? row[column.dataIndex]
                  : undefined
                const rendered = column.render
                  ? column.render(value, row, index, undefined)
                  : column.renderText
                    ? column.renderText(value, row, index, undefined)
                    : value
                return (
                  <span key={column.title?.toString() ?? index}>
                    {rendered}
                  </span>
                )
              })}
          </div>
        )
      })}
    </div>
  )
}
```

- [ ] **Step 2: Write failing page tests for single and selected batch submission**

Add `FREE_CONTENT_PREVIEW` to the existing GoodShort provider fixture capabilities, define this page-local task helper, and add these tests:

```tsx
function contentTask(
  dramaId: number,
  status: 'REQUESTED' | 'RUNNING' | 'SUCCESS' | 'FAILED',
) {
  return {
    id: 50 + dramaId,
    dramaId,
    status,
    requestedAt: '2026-08-29T08:00:00',
    nextRunAt:
      status === 'SUCCESS' || status === 'FAILED'
        ? null
        : '2026-08-29T08:00:03',
    retryCount: 0,
    totalFetched: 0,
    insertedCount: 0,
    updatedCount: 0,
    lastErrorCode: null,
    lastErrorMessage: null,
  }
}

it('confirms and submits a single free content sync task', async () => {
  useCatalogHandlers()
  let requestedId: string | undefined
  server.use(
    http.post('/api/admin/drama/catalog/:id/contents/sync', ({ params }) => {
      requestedId = String(params.id)
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: contentTask(8, 'REQUESTED'),
      })
    }),
  )
  const user = userEvent.setup()
  renderPage()
  const row = await screen.findByTestId('mock-row-8')
  await user.click(within(row).getByTestId('drama-content-sync-8'))
  expect(
    await screen.findByText('当前操作只同步 GoodShort 免费剧集。'),
  ).toBeInTheDocument()
  await user.click(await screen.findByTestId('drama-content-sync-confirm-8'))
  await waitFor(() => expect(requestedId).toBe('8'))
  expect(await screen.findByText('免费剧集同步任务已提交')).toBeInTheDocument()
})

it('submits selected dramas, reports counts, and clears selection only on success', async () => {
  useCatalogHandlers()
  let body: unknown
  server.use(
    http.post('/api/admin/drama/catalog/contents/sync', async ({ request }) => {
      body = await request.json()
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: {
          requestedCount: 2,
          queuedCount: 1,
          skippedCount: 1,
          invalidCount: 0,
          tasks: [],
        },
      })
    }),
  )
  const user = userEvent.setup()
  renderPage()
  await screen.findByTestId('mock-row-8')
  expect(
    screen.getByRole('button', { name: '同步所选剧集（0）' }),
  ).toBeDisabled()
  await user.click(screen.getByRole('checkbox', { name: '选择短剧 8' }))
  await user.click(screen.getByRole('checkbox', { name: '选择短剧 9' }))
  await user.click(screen.getByRole('button', { name: '同步所选剧集（2）' }))
  await user.click(await screen.findByTestId('drama-content-batch-confirm'))
  await waitFor(() => expect(body).toEqual({ dramaIds: [8, 9] }))
  expect(
    await screen.findByText('请求 2 部，排队 1 部，运行中跳过 1 部，无效 0 部'),
  ).toBeInTheDocument()
  expect(
    screen.getByRole('button', { name: '同步所选剧集（0）' }),
  ).toBeDisabled()
})

it('keeps selected dramas when batch submission fails', async () => {
  useCatalogHandlers()
  server.use(
    http.post('/api/admin/drama/catalog/contents/sync', () =>
      HttpResponse.json({ code: 6008, message: '短剧不存在', data: null }),
    ),
  )
  const user = userEvent.setup()
  renderPage()
  await screen.findByTestId('mock-row-8')
  await user.click(screen.getByRole('checkbox', { name: '选择短剧 8' }))
  await user.click(screen.getByRole('checkbox', { name: '选择短剧 9' }))
  await user.click(screen.getByRole('button', { name: '同步所选剧集（2）' }))
  await user.click(await screen.findByTestId('drama-content-batch-confirm'))
  expect(await screen.findByText('短剧不存在')).toBeInTheDocument()
  expect(
    screen.getByRole('button', { name: '同步所选剧集（2）' }),
  ).toBeEnabled()
})

it('limits selected dramas to one hundred', async () => {
  const dramas = Array.from({ length: 101 }, (_, index) => ({
    ...publishedDrama,
    id: index + 1,
    externalDramaId: `book-${index + 1}`,
    title: `Drama ${index + 1}`,
    titleZh: null,
    originalTitle: null,
  }))
  server.use(
    http.get('/api/admin/drama/providers', () =>
      HttpResponse.json({ code: 0, message: 'ok', data: [provider] }),
    ),
    http.get('/api/admin/drama/catalog', () =>
      HttpResponse.json({
        code: 0,
        message: 'ok',
        data: { list: dramas, page: 1, size: 101, total: 101 },
      }),
    ),
  )
  const user = userEvent.setup()
  renderPage()
  await screen.findByTestId('mock-row-101')
  for (let id = 1; id <= 100; id += 1) {
    await user.click(screen.getByRole('checkbox', { name: `选择短剧 ${id}` }))
  }
  expect(
    screen.getByRole('button', { name: '同步所选剧集（100）' }),
  ).toBeEnabled()
  expect(screen.getByRole('checkbox', { name: '选择短剧 101' })).toBeDisabled()
})

it('clears selected dramas after search, reset, and page changes', async () => {
  useCatalogHandlers()
  const user = userEvent.setup()
  renderPage()
  await screen.findByTestId('mock-row-8')

  for (const trigger of [
    'mock-search-submit',
    'mock-search-reset',
    'mock-page-change',
  ]) {
    await user.click(screen.getByRole('checkbox', { name: '选择短剧 8' }))
    expect(
      screen.getByRole('button', { name: '同步所选剧集（1）' }),
    ).toBeEnabled()
    await user.click(screen.getByTestId(trigger))
    expect(
      screen.getByRole('button', { name: '同步所选剧集（0）' }),
    ).toBeDisabled()
  }
})
```

- [ ] **Step 3: Write failing page tests for the all-sync modal and detail refresh**

Keep the existing test `submits an incremental English sync task and opens its status` unchanged as the directory-sync regression guard. Add these observable integration tests:

```tsx
it('opens the separate free content sync modal for capable enabled providers', async () => {
  useCatalogHandlers()
  const user = userEvent.setup()
  renderPage()
  await screen.findByTestId('mock-row-8')
  await user.click(screen.getByRole('button', { name: '同步剧集' }))
  const modal = await screen.findByTestId('drama-content-sync-modal')
  expect(within(modal).getByText('GoodShort')).toBeInTheDocument()
  expect(within(modal).getByText('同步全部在线短剧')).toBeInTheDocument()
  expect(screen.queryByTestId('drama-sync-modal')).not.toBeInTheDocument()
})

it('refreshes the open detail after its content task succeeds', async () => {
  useCatalogHandlers()
  let detailRequests = 0
  let statusRequests = 0
  server.use(
    http.get('/api/admin/drama/catalog/8', () => {
      detailRequests += 1
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: {
          ...publishedDrama,
          description: null,
          createdAt: '2026-08-20T10:35:00',
          contents:
            detailRequests === 1
              ? []
              : [
                  {
                    id: 88,
                    externalContentId: 'episode-new',
                    sequenceNo: 1,
                    title: 'New Episode',
                    free: true,
                    durationSeconds: 90,
                    remoteUpdatedAt: '2026-08-29T08:01:00',
                  },
                ],
        },
      })
    }),
    http.get('/api/admin/drama/catalog/8/contents/sync/status', () => {
      statusRequests += 1
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: contentTask(8, 'SUCCESS'),
      })
    }),
  )
  const user = userEvent.setup()
  renderPage()
  await screen.findByTestId('mock-row-8')
  await user.click(screen.getByTestId('drama-detail-8'))
  expect(await screen.findByText('New Episode')).toBeInTheDocument()
  expect(detailRequests).toBe(2)
  expect(statusRequests).toBe(1)
})

it('refreshes the current detail status after a row sync and deactivates it on close', async () => {
  useCatalogHandlers()
  let statusRequests = 0
  server.use(
    http.get('/api/admin/drama/catalog/8', () =>
      HttpResponse.json({
        code: 0,
        message: 'ok',
        data: {
          ...publishedDrama,
          description: null,
          createdAt: null,
          contents: [],
        },
      }),
    ),
    http.get('/api/admin/drama/catalog/8/contents/sync/status', () => {
      statusRequests += 1
      return HttpResponse.json({
        code: 0,
        message: 'ok',
        data: contentTask(8, 'REQUESTED'),
      })
    }),
    http.post('/api/admin/drama/catalog/8/contents/sync', () =>
      HttpResponse.json({
        code: 0,
        message: 'ok',
        data: contentTask(8, 'REQUESTED'),
      }),
    ),
  )
  const user = userEvent.setup()
  renderPage()
  await screen.findByTestId('mock-row-8')
  await user.click(screen.getByTestId('drama-detail-8'))
  await screen.findByText('等待执行')
  await user.click(screen.getByTestId('drama-content-sync-8'))
  await user.click(await screen.findByTestId('drama-content-sync-confirm-8'))
  await waitFor(() => expect(statusRequests).toBe(2))
  await user.click(screen.getByRole('button', { name: 'Close' }))
  await waitFor(() =>
    expect(
      screen.queryByTestId('drama-content-sync-section'),
    ).not.toBeInTheDocument(),
  )
})
```

- [ ] **Step 4: Implement the page orchestration**

In `DramaCatalogPage.tsx`:

```tsx
import type { Key, ReactNode } from 'react'
import {
  Activity,
  Clapperboard,
  ListChecks,
  RefreshCw,
  Video,
} from 'lucide-react'
import {
  requestDramaContentBatchSync,
  requestDramaContentSync,
} from '../../features/drama/dramaCatalogApi'
import { DramaContentSyncModal } from './DramaContentSyncModal'
import { DramaContentSyncSection } from './DramaContentSyncSection'
```

Add state:

```ts
const [selectedDramaIds, setSelectedDramaIds] = useState<number[]>([])
const [contentSyncModalOpen, setContentSyncModalOpen] = useState(false)
const [contentSyncingId, setContentSyncingId] = useState<number | null>(null)
const [batchContentSyncing, setBatchContentSyncing] = useState(false)
const [detailContentRefreshKey, setDetailContentRefreshKey] = useState(0)
```

Derive providers that are enabled and advertise the content capability:

```ts
const contentSyncProviders = useMemo(
  () =>
    syncProviders.filter((provider) =>
      provider.capabilities.includes('FREE_CONTENT_PREVIEW'),
    ),
  [syncProviders],
)
```

Add handlers with these exact success/failure rules:

```ts
const handleSingleContentSync = async (record: DramaCatalogListItem) => {
  setContentSyncingId(record.id)
  try {
    await requestDramaContentSync(record.id)
    message.success('免费剧集同步任务已提交')
    if (detail?.id === record.id) {
      setDetailContentRefreshKey((value) => value + 1)
    }
  } catch (error) {
    if (isUnauthorizedError(error)) return
    message.error(
      error instanceof Error ? error.message : '免费剧集同步任务提交失败',
    )
  } finally {
    setContentSyncingId(null)
  }
}

const handleBatchContentSync = async () => {
  setBatchContentSyncing(true)
  try {
    const result = await requestDramaContentBatchSync(selectedDramaIds)
    message.success(
      `请求 ${result.requestedCount} 部，排队 ${result.queuedCount} 部，运行中跳过 ${result.skippedCount} 部，无效 ${result.invalidCount} 部`,
    )
    setSelectedDramaIds([])
  } catch (error) {
    if (isUnauthorizedError(error)) return
    message.error(
      error instanceof Error ? error.message : '批量免费剧集同步任务提交失败',
    )
  } finally {
    setBatchContentSyncing(false)
  }
}

const refreshDetail = async () => {
  if (!detail) return
  try {
    setDetail(await getDramaCatalogDetail(detail.id))
  } catch (error) {
    if (isUnauthorizedError(error)) return
    message.error(error instanceof Error ? error.message : '短剧详情刷新失败')
  }
}
```

Increase the operation column width from `140` to `210` and add this action after “详情” and before the existing local status action:

```tsx
<Popconfirm
  title="确认同步这部短剧的剧集？"
  description="当前操作只同步 GoodShort 免费剧集。"
  okText="确认"
  cancelText="取消"
  okButtonProps={{
    'data-testid': `drama-content-sync-confirm-${record.id}`,
  }}
  onConfirm={() => void handleSingleContentSync(record)}
>
  <Button
    type="link"
    size="small"
    loading={contentSyncingId === record.id}
    data-testid={`drama-content-sync-${record.id}`}
  >
    同步剧集
  </Button>
</Popconfirm>
```

Configure `ProTable` selection and reset boundaries:

```tsx
rowSelection={{
  selectedRowKeys: selectedDramaIds,
  preserveSelectedRowKeys: false,
  onChange: (keys: Key[]) => setSelectedDramaIds(keys.map(Number).slice(0, 100)),
  getCheckboxProps: (record) => ({
    disabled: selectedDramaIds.length >= 100 && !selectedDramaIds.includes(record.id),
  }),
}}
onSubmit={() => setSelectedDramaIds([])}
onReset={() => setSelectedDramaIds([])}
pagination={{
  defaultPageSize: 20,
  showSizeChanger: true,
  onChange: () => setSelectedDramaIds([]),
}}
```

Add toolbar buttons before the existing directory buttons:

```tsx
<Popconfirm
  key="sync-selected-content"
  title={`确认同步所选 ${selectedDramaIds.length} 部短剧？`}
  description="当前操作只同步 GoodShort 免费剧集。"
  okButtonProps={{ 'data-testid': 'drama-content-batch-confirm' }}
  onConfirm={() => void handleBatchContentSync()}
>
  <Button
    icon={<ListChecks size={16} />}
    disabled={selectedDramaIds.length === 0}
    loading={batchContentSyncing}
  >
    同步所选剧集（{selectedDramaIds.length}）
  </Button>
</Popconfirm>,
<Button
  key="sync-content"
  icon={<Video size={16} />}
  disabled={contentSyncProviders.length === 0}
  onClick={() => setContentSyncModalOpen(true)}
>
  同步剧集
</Button>,
```

Render `DramaContentSyncModal` beside the existing directory modal. Its callback closes only the content modal and preserves the current catalog filters/page:

```tsx
<DramaContentSyncModal
  open={contentSyncModalOpen}
  providers={contentSyncProviders}
  preferredProviderId={syncProviderId}
  onClose={() => setContentSyncModalOpen(false)}
  onSubmitted={(providerId) => {
    setSyncProviderId(providerId)
    setContentSyncModalOpen(false)
  }}
/>
```

Change `DramaDetail` props to accept the sync section:

```tsx
function DramaDetail({
  detail,
  contentSyncSection,
}: {
  detail: DramaCatalogDetail
  contentSyncSection: ReactNode
})
```

Render `{contentSyncSection}` between “基本信息” and “剧集”. Change the drawer body to pass:

```tsx
{
  detail ? (
    <DramaDetail
      detail={detail}
      contentSyncSection={
        <DramaContentSyncSection
          dramaId={detail.id}
          active={detailOpen}
          refreshKey={detailContentRefreshKey}
          onSucceeded={() => void refreshDetail()}
        />
      }
    />
  ) : null
}
```

When `openDetail` starts for a new row, reset `detailContentRefreshKey` to zero. Closing the drawer sets `detailOpen` false; this makes the status component cancel its timeout.

- [ ] **Step 5: Add minimal responsive styles**

Append to `drama-catalog-page.css`:

```css
.drama-catalog-page__content-sync-status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}

.drama-catalog-page__content-sync-error {
  margin-top: 10px;
  color: var(--ant-color-error-text);
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.drama-catalog-page__section-heading .ant-btn {
  flex: 0 0 32px;
}

@media (max-width: 720px) {
  .drama-catalog-page__content-sync-status {
    align-items: flex-start;
    flex-direction: column;
  }
}
```

Do not add a card around the status section and do not expose `content_url` in the episode table.

- [ ] **Step 6: Run the focused page and component tests**

Run:

```powershell
cd kasi-admin-web
pnpm test -- src/pages/drama/DramaCatalogPage.test.tsx src/pages/drama/DramaContentSyncModal.test.tsx src/pages/drama/DramaContentSyncSection.test.tsx
```

Expected: single, selected batch, all-online modal, status polling, detail refresh, and existing catalog-sync tests all pass.

- [ ] **Step 7: Commit the page integration**

Run from the monorepo root:

```powershell
git add kasi-admin-web/src/pages/drama/DramaCatalogPage.tsx kasi-admin-web/src/pages/drama/DramaCatalogPage.test.tsx kasi-admin-web/src/pages/drama/drama-catalog-page.css
git diff --cached --check
git commit -m "feat(admin): manage free episode synchronization"
```

Expected: one commit containing only page orchestration, regression tests, and scoped styles.

### Task 5: Document And Verify The Completed Frontend

**Files:**

- Modify: `kasi-admin-web/README.md`
- Modify: `kasi-admin-web/docs/superpowers/specs/2026-08-29-goodshort-free-content-sync-admin-design.md`

- [ ] **Step 1: Update current-behavior documentation**

Replace the short-drama paragraph in `kasi-admin-web/README.md` with text that records both separate workflows and all four new endpoints:

```markdown
左侧“短剧管理”一级菜单下提供“短剧目录”二级菜单，页面路由为 `/drama/catalog`，普通管理员和超级管理员均可访问。当前只对接 GoodShort：管理员可按平台、名称、语言、远端状态和本地状态查询目录，在右侧抽屉查看短剧与剧集元数据，确认上架或下架，并通过工具栏分别管理短剧目录同步和免费剧集同步。目录同步保留全量/增量任务及各语言状态；免费剧集同步支持单部、最多 100 部勾选批量、全部在线短剧，以及仅补齐缺失视频地址。详情抽屉显示单部剧集同步任务状态、统计和错误，并在任务运行期间按 3 秒轮询、最多持续 60 秒。所有同步请求只创建后端任务，不在页面请求中等待 GoodShort 完成；当前不支持收费剧集同步，也不展示永久视频 URL。页面除原有目录 API 外，对接 `POST /api/admin/drama/catalog/{id}/contents/sync`、`POST /api/admin/drama/catalog/contents/sync`、`POST /api/admin/drama/catalog/contents/sync/all` 和 `GET /api/admin/drama/catalog/{id}/contents/sync/status`；不展示连接 ID、PID、KEY、凭据或租约字段。
```

In the approved design document, change only:

```text
状态：已确认，已实施
```

Then update section 12 so the frontend buttons, selection, modal, status section, and tests are listed under “当前已实现”; retain “收费剧集同步仍没有 GoodShort 数据来源” as the explicit gap.

- [ ] **Step 2: Run the full frontend verification gate**

Run each command separately from `kasi-admin-web` so a timeout or baseline issue is attributable:

```powershell
pnpm test
pnpm lint
pnpm typecheck
pnpm format:check
pnpm build
```

Expected: every command exits `0`. Record exact test file/test counts from the fresh `pnpm test` output. If `format:check` reports only a pre-existing unrelated file, do not rewrite it; report the baseline mismatch and still format every file changed by this plan.

- [ ] **Step 3: Check the complete diff from the monorepo root**

Run:

```powershell
git status --short --branch
git diff --check
git diff --stat 4141624..HEAD
git diff -- kasi-admin-web
```

Expected: no whitespace errors; only the planned admin frontend and documentation files differ from the plan baseline. Confirm no backend, user frontend, credentials, `node_modules`, logs, or build artifacts are included.

- [ ] **Step 4: Commit the documentation**

Run from the monorepo root:

```powershell
git add kasi-admin-web/README.md kasi-admin-web/docs/superpowers/specs/2026-08-29-goodshort-free-content-sync-admin-design.md
git diff --cached --check
git commit -m "docs(admin): document free episode synchronization"
```

Expected: one documentation-only commit. Do not push; publication requires a separate explicit user instruction.

- [ ] **Step 5: Report the implementation boundary**

Report these facts with fresh command evidence:

```text
当前已实现：管理端免费剧集单部、勾选批量、全部在线/补齐缺失同步，以及单部任务状态轮询。
保持不变：原有短剧目录全量/增量同步、目录同步状态、上下架和详情元数据。
明确不支持：收费剧集同步、视频 URL 展示或编辑、独立剧集任务列表页。
发布状态：本地 codex/admin-free-content-sync 分支已提交，未推送。
```
