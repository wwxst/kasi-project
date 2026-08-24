import { useMemo, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { PrimaryTableCol } from 'tdesign-react'
import {
  Alert,
  Button,
  Drawer,
  Form,
  Input,
  Select,
  Table,
  Tag,
} from 'tdesign-react'
import { AddIcon, SearchIcon } from 'tdesign-icons-react'
import {
  createMediaAccount,
  retryMediaAccountFiling,
  updateMediaAccount,
} from '../../features/promotion/api/mediaAccountApi'
import type {
  CreateMediaAccountRequest,
  MediaAccount,
  MediaAccountDetail,
  MediaType,
  UpdateMediaAccountRequest,
} from '../../features/promotion/api/mediaAccountTypes'
import {
  mediaAccountsQueryKey,
  useMediaAccount,
  useMediaAccounts,
} from '../../features/promotion/model/mediaAccountQueries'
import {
  formatDateTime,
  formatMediaType,
  getFilingView,
  isIdentityEditable,
} from '../../features/promotion/model/mediaAccountPresentation'
import { ApiError } from '../../shared/api/ApiError'
import './media-account-filing.css'

const mediaOptions = [
  { label: 'TikTok', value: 'TIKTOK' },
  { label: 'Facebook', value: 'FACEBOOK' },
  { label: 'YouTube', value: 'YOUTUBE' },
  { label: 'Instagram', value: 'INSTAGRAM' },
]
type DrawerMode = 'detail' | 'create' | 'edit' | null
type FormValues = {
  mediaType: MediaType
  externalAccountId: string
  accountName: string
  accountLink: string
}

const emptyForm: FormValues = {
  mediaType: 'TIKTOK',
  externalAccountId: '',
  accountName: '',
  accountLink: '',
}

function formFromAccount(account: MediaAccount): FormValues {
  return {
    mediaType: account.mediaType,
    externalAccountId: account.externalAccountId,
    accountName: account.accountName ?? '',
    accountLink: account.accountLink ?? '',
  }
}

function getErrorMessage(error: unknown) {
  return error instanceof ApiError ? error.message : '请求失败，请稍后重试'
}

export function MediaAccountFilingPage() {
  const queryClient = useQueryClient()
  const { data: accounts = [], isLoading, isError, error } = useMediaAccounts()
  const [searchText, setSearchText] = useState('')
  const [selectedRowKeys, setSelectedRowKeys] = useState<(string | number)[]>(
    [],
  )
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [drawerMode, setDrawerMode] = useState<DrawerMode>(null)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [form, setForm] = useState<FormValues>(emptyForm)
  const [formError, setFormError] = useState('')
  const [retryProviderId, setRetryProviderId] = useState<number | null>(null)

  const detailQuery = useMediaAccount(
    selectedId,
    drawerMode === 'detail' || drawerMode === 'edit',
  )
  const selectedAccount = detailQuery.data
  const selectedListAccount = accounts.find(
    (account) => account.id === selectedId,
  )
  const selectedForRules = selectedAccount ?? selectedListAccount

  const createMutation = useMutation({
    mutationFn: (request: CreateMediaAccountRequest) =>
      createMediaAccount(request),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: mediaAccountsQueryKey })
      closeDrawer()
    },
  })
  const updateMutation = useMutation({
    mutationFn: ({
      id,
      request,
    }: {
      id: number
      request: UpdateMediaAccountRequest
    }) => updateMediaAccount(id, request),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: mediaAccountsQueryKey })
      if (selectedId !== null) {
        await queryClient.invalidateQueries({
          queryKey: [...mediaAccountsQueryKey, selectedId],
        })
      }
      setDrawerMode('detail')
    },
  })
  const retryMutation = useMutation({
    mutationFn: ({
      accountId,
      providerId,
    }: {
      accountId: number
      providerId: number
    }) => retryMediaAccountFiling(accountId, providerId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: mediaAccountsQueryKey })
      if (selectedId !== null) {
        await queryClient.invalidateQueries({
          queryKey: [...mediaAccountsQueryKey, selectedId],
        })
      }
    },
  })

  const filteredAccounts = useMemo(
    () =>
      accounts.filter((account) => {
        return (
          !searchText ||
          account.externalAccountId
            .toLowerCase()
            .includes(searchText.toLowerCase()) ||
          account.accountName?.toLowerCase().includes(searchText.toLowerCase())
        )
      }),
    [accounts, searchText],
  )

  function exportAccounts() {
    const rows = filteredAccounts.map((account) => {
      const filing = getFilingView(account)
      return [
        formatMediaType(account.mediaType),
        account.accountName || '',
        filing.label,
      ]
        .map((value) => `"${String(value).replaceAll('"', '""')}"`)
        .join(',')
    })
    const csv = ['媒体平台,账号名称,GoodShort', ...rows].join('\n')
    const url = URL.createObjectURL(
      new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' }),
    )
    const link = document.createElement('a')
    link.href = url
    link.download = '媒体账号报白.csv'
    link.click()
    URL.revokeObjectURL(url)
  }

  function closeDrawer() {
    setDrawerMode(null)
    setSelectedId(null)
    setFormError('')
    createMutation.reset()
    updateMutation.reset()
    retryMutation.reset()
  }

  function openCreate() {
    setSelectedId(null)
    setForm(emptyForm)
    setFormError('')
    setDrawerMode('create')
  }

  function openDetail(account: MediaAccount) {
    setSelectedId(account.id)
    setForm(formFromAccount(account))
    setFormError('')
    setDrawerMode('detail')
  }

  function openEdit() {
    if (!selectedForRules) return
    setForm(formFromAccount(selectedForRules))
    setFormError('')
    setDrawerMode('edit')
  }

  async function submitForm() {
    setFormError('')
    if (!form.externalAccountId.trim()) {
      setFormError('请输入账号 ID')
      return
    }
    if (form.accountLink && !form.accountLink.startsWith('https://')) {
      setFormError('主页链接必须使用 HTTPS')
      return
    }

    try {
      if (drawerMode === 'create') {
        await createMutation.mutateAsync({
          ...form,
          externalAccountId: form.externalAccountId.trim(),
          accountName: form.accountName.trim() || undefined,
          accountLink: form.accountLink.trim() || undefined,
        })
      } else if (drawerMode === 'edit' && selectedId !== null) {
        await updateMutation.mutateAsync({
          id: selectedId,
          request: {
            ...form,
            externalAccountId: form.externalAccountId.trim(),
            accountName: form.accountName.trim() || undefined,
            accountLink: form.accountLink.trim() || undefined,
          },
        })
      }
    } catch (submissionError) {
      setFormError(getErrorMessage(submissionError))
    }
  }

  async function retryFiling(providerId: number) {
    setFormError('')
    setRetryProviderId(providerId)
    try {
      if (selectedId === null) return
      await retryMutation.mutateAsync({ accountId: selectedId, providerId })
    } catch (retryError) {
      setFormError(getErrorMessage(retryError))
    } finally {
      setRetryProviderId(null)
    }
  }

  const columns: PrimaryTableCol<MediaAccount>[] = [
    {
      colKey: 'row-select',
      fixed: 'left',
      type: 'multiple',
      width: 50,
    },
    {
      colKey: 'mediaType',
      title: '媒体平台',
      cell: ({ row }) => formatMediaType(row.mediaType),
      width: 130,
    },
    {
      colKey: 'accountName',
      title: '账号名称',
      width: 220,
      cell: ({ row }) => row.accountName || '未设置',
    },
    {
      colKey: 'filingStatus',
      title: 'GoodShort',
      width: 150,
      cell: ({ row }) => {
        const view = getFilingView(row)
        return <Tag theme={view.theme}>{view.label}</Tag>
      },
    },
    {
      colKey: 'actions',
      title: '操作',
      fixed: 'right',
      width: 110,
      cell: ({ row }) => (
        <div className="media-account-actions">
          <Button
            variant="text"
            theme="primary"
            onClick={() => openDetail(row)}
          >
            详情
          </Button>
        </div>
      ),
    },
  ]

  const isFormDrawer = drawerMode === 'create' || drawerMode === 'edit'
  const detail = selectedAccount ?? selectedListAccount
  const filingView = detail ? getFilingView(detail) : null
  const identityEditable = detail ? isIdentityEditable(detail) : true
  const submitting = createMutation.isPending || updateMutation.isPending

  return (
    <section
      className="media-account-page"
      aria-labelledby="media-account-page-title"
    >
      <div className="media-account-breadcrumb">
        推广管理 / <span>账号报白</span>
        <h1 id="media-account-page-title" className="visually-hidden">
          账号报白
        </h1>
      </div>
      <div className="media-account-list-shell">
        <div className="media-account-toolbar">
          <div className="media-account-toolbar-left">
            <Button theme="primary" icon={<AddIcon />} onClick={openCreate}>
              新增账号
            </Button>
            <Button theme="default" variant="outline" onClick={exportAccounts}>
              导出账号
            </Button>
            <span className="media-account-count">
              已选 {selectedRowKeys.length} 项
            </span>
          </div>
          <div className="media-account-toolbar-right">
            <Input
              value={searchText}
              suffixIcon={<SearchIcon />}
              placeholder="请输入你需要搜索的账号"
              aria-label="搜索媒体账号"
              onChange={setSearchText}
            />
          </div>
        </div>

        {isError ? (
          <Alert
            theme="error"
            message={getErrorMessage(error)}
            className="media-account-alert"
          />
        ) : null}

        <Table
          rowKey="id"
          data={filteredAccounts}
          columns={columns}
          bordered
          hover
          verticalAlign="top"
          tableLayout="fixed"
          loading={isLoading}
          selectedRowKeys={selectedRowKeys}
          onSelectChange={(value) => setSelectedRowKeys(value)}
          pagination={{
            current,
            pageSize,
            total: filteredAccounts.length,
            showJumper: true,
            onCurrentChange: (nextPage) => setCurrent(nextPage),
            onPageSizeChange: (nextPageSize) => {
              setPageSize(nextPageSize)
              setCurrent(1)
            },
          }}
          empty={
            <div className="media-account-empty">
              <p>暂无媒体账号</p>
              <Button theme="primary" variant="outline" onClick={openCreate}>
                新增账号
              </Button>
            </div>
          }
        />
      </div>

      <Drawer
        visible={drawerMode !== null}
        placement="right"
        size="min(100vw, 520px)"
        header={
          drawerMode === 'create'
            ? '新增账号'
            : drawerMode === 'edit'
              ? '编辑账号'
              : '账号详情'
        }
        onClose={closeDrawer}
        footer={
          isFormDrawer ? (
            <div className="drawer-footer-actions">
              <Button variant="outline" onClick={closeDrawer}>
                取消
              </Button>
              <Button
                theme="primary"
                loading={submitting}
                onClick={() => void submitForm()}
              >
                提交报白
              </Button>
            </div>
          ) : detail ? (
            <div className="drawer-footer-actions">
              {filingView?.status === 'FAILED' ? (
                <Button
                  theme="primary"
                  loading={retryProviderId === filingView?.filing?.providerId}
                  onClick={() =>
                    filingView?.filing?.providerId !== undefined &&
                    void retryFiling(filingView.filing.providerId)
                  }
                >
                  重试报白
                </Button>
              ) : null}
              <Button variant="outline" onClick={openEdit}>
                编辑
              </Button>
            </div>
          ) : null
        }
      >
        {formError ? (
          <Alert
            theme="error"
            message={formError}
            className="media-account-alert"
          />
        ) : null}
        {drawerMode === 'detail' && detail ? (
          <div className="media-account-detail">
            <dl className="details-grid">
              <div>
                <dt>报白平台</dt>
                <dd>
                  {detail.filings.length
                    ? detail.filings
                        .map(
                          (filing) =>
                            filing.providerName ?? `平台 ${filing.providerId}`,
                        )
                        .join('、')
                    : '暂无平台'}
                </dd>
              </div>
              <div>
                <dt>媒体平台</dt>
                <dd>{formatMediaType(detail.mediaType)}</dd>
              </div>
              <div>
                <dt>账号 ID</dt>
                <dd>{detail.externalAccountId}</dd>
              </div>
              <div>
                <dt>账号名称</dt>
                <dd>{detail.accountName || '未设置'}</dd>
              </div>
              <div>
                <dt>主页链接</dt>
                <dd>{detail.accountLink || '未设置'}</dd>
              </div>
              <div>
                <dt>报白状态</dt>
                <dd>
                  {filingView ? (
                    <Tag theme={filingView.theme}>{filingView.label}</Tag>
                  ) : null}
                </dd>
              </div>
            </dl>
            <div className="media-account-filing-list">
              {detail.filings.map((filing) => (
                <div
                  key={filing.providerId}
                  className="media-account-filing-list__item"
                >
                  <strong>
                    {filing.providerName ?? `平台 ${filing.providerId}`}
                  </strong>
                  <Tag
                    theme={
                      filing.status === 'APPROVED'
                        ? 'success'
                        : filing.status === 'FAILED'
                          ? 'danger'
                          : 'warning'
                    }
                  >
                    {filing.status === 'APPROVED'
                      ? '已加白'
                      : filing.status === 'FAILED'
                        ? '已失败'
                        : '审核中'}
                  </Tag>
                </div>
              ))}
            </div>
            {filingView?.filing?.lastErrorMessage ? (
              <Alert
                theme="error"
                message={filingView.filing.lastErrorMessage}
                className="media-account-alert"
              />
            ) : null}
            <dl className="details-grid details-grid-secondary">
              <div>
                <dt>报备时间</dt>
                <dd>{formatDateTime(filingView?.filing?.filingTime)}</dd>
              </div>
              <div>
                <dt>最近查询时间</dt>
                <dd>{formatDateTime(filingView?.filing?.lastQueriedAt)}</dd>
              </div>
              <div>
                <dt>创建时间</dt>
                <dd>
                  {formatDateTime((detail as MediaAccountDetail).createdAt)}
                </dd>
              </div>
              <div>
                <dt>更新时间</dt>
                <dd>
                  {formatDateTime((detail as MediaAccountDetail).updatedAt)}
                </dd>
              </div>
            </dl>
          </div>
        ) : isFormDrawer ? (
          <Form className="media-account-form" layout="vertical">
            <Form.FormItem label="媒体平台">
              <Select
                aria-label="媒体平台"
                value={form.mediaType}
                options={mediaOptions}
                disabled={drawerMode === 'edit' && !identityEditable}
                onChange={(value) =>
                  setForm((current) => ({
                    ...current,
                    mediaType: value as MediaType,
                  }))
                }
              />
            </Form.FormItem>
            <Form.FormItem label="账号 ID">
              <Input
                aria-label="账号 ID"
                value={form.externalAccountId}
                disabled={drawerMode === 'edit' && !identityEditable}
                onChange={(value) =>
                  setForm((current) => ({
                    ...current,
                    externalAccountId: value,
                  }))
                }
              />
            </Form.FormItem>
            <Form.FormItem label="账号名称">
              <Input
                aria-label="账号名称"
                value={form.accountName}
                onChange={(value) =>
                  setForm((current) => ({ ...current, accountName: value }))
                }
              />
            </Form.FormItem>
            <Form.FormItem label="主页链接">
              <Input
                aria-label="主页链接"
                value={form.accountLink}
                onChange={(value) =>
                  setForm((current) => ({ ...current, accountLink: value }))
                }
              />
            </Form.FormItem>
          </Form>
        ) : detailQuery.isLoading ? (
          <p>正在加载账号详情...</p>
        ) : null}
      </Drawer>
    </section>
  )
}
