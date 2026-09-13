import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App as AntdApp } from 'antd'
import React from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type {
  PromotionProjectType,
  PromotionProjectTypeFormValues,
} from '../../features/promotionProjectType/promotionProjectTypeTypes'

const apiMocks = vi.hoisted(() => ({
  createPromotionProjectType: vi.fn(),
  deletePromotionProjectType: vi.fn(),
  listPromotionProjectTypes: vi.fn(),
  updatePromotionProjectType: vi.fn(),
}))

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: React.PropsWithChildren) => (
    <section>{children}</section>
  ),
}))

vi.mock(
  '../../features/promotionProjectType/promotionProjectTypeApi',
  () => apiMocks,
)

import { PromotionProjectTypePage } from './PromotionProjectTypePage'

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('PromotionProjectTypePage', () => {
  it('creates, edits, and deletes project types', async () => {
    const records: PromotionProjectType[] = [projectType()]
    apiMocks.listPromotionProjectTypes.mockImplementation(async () => [
      ...records,
    ])
    apiMocks.createPromotionProjectType.mockImplementation(
      async (values: PromotionProjectTypeFormValues) => {
        const created = { ...projectType(), ...values, id: 2 }
        records.push(created)
        return created
      },
    )
    apiMocks.updatePromotionProjectType.mockImplementation(
      async (id: number, values: PromotionProjectTypeFormValues) => {
        const index = records.findIndex((item) => item.id === id)
        records[index] = { ...records[index], ...values }
        return records[index]
      },
    )
    apiMocks.deletePromotionProjectType.mockImplementation(
      async (id: number) => {
        records.splice(
          records.findIndex((item) => item.id === id),
          1,
        )
      },
    )

    renderPage()
    expect(await screen.findByText('CPA')).toBeInTheDocument()

    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: '新增项目类型' }))
    const createDialog = await screen.findByRole('dialog')
    await user.type(within(createDialog).getByLabelText('类型编码'), 'cpm')
    await user.type(
      within(createDialog).getByLabelText('类型名称'),
      '按千次展示付费',
    )
    await user.clear(within(createDialog).getByLabelText('显示顺序'))
    await user.type(within(createDialog).getByLabelText('显示顺序'), '2')
    await user.click(
      within(createDialog).getByRole('button', { name: '确认新增' }),
    )

    await waitFor(() =>
      expect(apiMocks.createPromotionProjectType).toHaveBeenCalledWith({
        code: 'cpm',
        name: '按千次展示付费',
        status: 'ENABLED',
        sortOrder: 2,
      }),
    )
    expect(await screen.findByText('cpm')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '编辑 CPA' }))
    const editDialog = await screen.findByRole('dialog')
    const nameInput = within(editDialog).getByLabelText('类型名称')
    await user.clear(nameInput)
    await user.type(nameInput, '行动计费')
    await user.click(
      within(editDialog).getByRole('button', { name: '保存修改' }),
    )
    await waitFor(() =>
      expect(apiMocks.updatePromotionProjectType).toHaveBeenCalledWith(
        1,
        expect.objectContaining({ name: '行动计费' }),
      ),
    )
    expect(await screen.findByText('行动计费')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '删除 CPA' }))
    await user.click(await screen.findByRole('button', { name: '确认删除' }))
    await waitFor(() =>
      expect(apiMocks.deletePromotionProjectType).toHaveBeenCalledWith(
        1,
        expect.anything(),
      ),
    )
    await waitFor(() => expect(screen.queryByText('行动计费')).toBeNull())
  })
})

function projectType(): PromotionProjectType {
  return {
    id: 1,
    code: 'CPA',
    name: '按行动付费',
    status: 'ENABLED',
    sortOrder: 1,
    createdAt: null,
    updatedAt: null,
  }
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <AntdApp>
        <PromotionProjectTypePage />
      </AntdApp>
    </QueryClientProvider>,
  )
}
