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
  PromotionProject,
  SavePromotionProjectRequest,
} from '../../features/promotionProject/promotionProjectTypes'

const apiMocks = vi.hoisted(() => ({
  createPromotionProject: vi.fn(),
  deletePromotionProject: vi.fn(),
  listPromotionProjects: vi.fn(),
  updatePromotionProject: vi.fn(),
}))

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({
    title,
    children,
  }: React.PropsWithChildren<{ title: React.ReactNode }>) => (
    <section>
      <h1>{title}</h1>
      {children}
    </section>
  ),
}))

vi.mock('../../features/promotionProject/promotionProjectApi', () => apiMocks)

import { PromotionProjectPage } from './PromotionProjectPage'

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('PromotionProjectPage', () => {
  it('requires a cover when creating a project', async () => {
    apiMocks.listPromotionProjects.mockResolvedValue(emptyPage())
    renderPage()

    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: '新增项目' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText('项目名称'), '项目B')
    await user.type(
      within(dialog).getByLabelText('项目文档 URL'),
      'https://example.com/b',
    )
    await user.click(within(dialog).getByRole('button', { name: '确认新增' }))

    expect(
      await within(dialog).findByText('请选择项目封面'),
    ).toBeInTheDocument()
    expect(apiMocks.createPromotionProject).not.toHaveBeenCalled()
  })

  it('creates, edits, and physically deletes promotion projects', async () => {
    const records: PromotionProject[] = [
      {
        id: 1,
        name: '项目A',
        coverImageUrl: '/uploads/promotion-projects/a.png',
        projectDocumentUrl: 'https://example.com/a',
        status: 'ENABLED',
        sortOrder: 10,
        createdAt: '2026-09-09T10:00:00',
        updatedAt: '2026-09-09T10:00:00',
      },
    ]
    apiMocks.listPromotionProjects.mockImplementation(async () => ({
      list: [...records],
      page: 1,
      size: 20,
      total: records.length,
    }))
    apiMocks.createPromotionProject.mockImplementation(
      async (request: SavePromotionProjectRequest) => {
        const created: PromotionProject = {
          ...records[0],
          id: 2,
          name: request.name,
          coverImageUrl: '/uploads/promotion-projects/b.png',
          projectDocumentUrl: request.projectDocumentUrl,
          status: request.status,
          sortOrder: request.sortOrder,
        }
        records.push(created)
        return created
      },
    )
    apiMocks.updatePromotionProject.mockImplementation(
      async (_id: number, request: SavePromotionProjectRequest) => {
        const record = records[0]
        record.name = request.name
        record.projectDocumentUrl = request.projectDocumentUrl
        record.status = request.status
        record.sortOrder = request.sortOrder
        return record
      },
    )
    apiMocks.deletePromotionProject.mockImplementation(async (id: number) => {
      records.splice(
        records.findIndex((item) => item.id === id),
        1,
      )
    })

    renderPage()
    expect(await screen.findByText('项目A')).toBeInTheDocument()

    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: '新增项目' }))
    const createDialog = await screen.findByRole('dialog')
    await user.type(within(createDialog).getByLabelText('项目名称'), '项目B')
    await user.type(
      within(createDialog).getByLabelText('项目文档 URL'),
      'https://example.com/b',
    )
    await user.clear(within(createDialog).getByLabelText('显示顺序'))
    await user.type(within(createDialog).getByLabelText('显示顺序'), '20')
    const cover = new File(['cover'], 'cover.png', { type: 'image/png' })
    await user.upload(within(createDialog).getByLabelText('项目封面'), cover)
    await user.click(
      within(createDialog).getByRole('button', { name: '确认新增' }),
    )

    await waitFor(() =>
      expect(apiMocks.createPromotionProject).toHaveBeenCalledWith({
        name: '项目B',
        projectDocumentUrl: 'https://example.com/b',
        status: 'ENABLED',
        sortOrder: 20,
        coverFile: cover,
      }),
    )
    expect(await screen.findByText('项目B')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '编辑 项目A' }))
    const editDialog = await screen.findByRole('dialog')
    const nameInput = within(editDialog).getByLabelText('项目名称')
    await user.clear(nameInput)
    await user.type(nameInput, '项目A修改')
    await user.click(
      within(editDialog).getByRole('button', { name: '保存修改' }),
    )

    await waitFor(() =>
      expect(apiMocks.updatePromotionProject).toHaveBeenCalledWith(
        1,
        expect.objectContaining({ name: '项目A修改', coverFile: undefined }),
      ),
    )
    expect(await screen.findByText('项目A修改')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '删除 项目A修改' }))
    await user.click(await screen.findByRole('button', { name: '确认删除' }))
    await waitFor(() =>
      expect(apiMocks.deletePromotionProject.mock.calls[0]?.[0]).toBe(1),
    )
    await waitFor(() => expect(screen.queryByText('项目A修改')).toBeNull())
  })
})

function emptyPage() {
  return { list: [], page: 1, size: 20, total: 0 }
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <AntdApp>
        <PromotionProjectPage />
      </AntdApp>
    </QueryClientProvider>,
  )
}
