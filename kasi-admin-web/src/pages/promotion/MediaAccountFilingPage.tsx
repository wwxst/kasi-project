import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { PageContainer, ProTable } from '@ant-design/pro-components'
import type { MenuProps } from 'antd'
import {
  App as AntdApp,
  Avatar,
  Button,
  Descriptions,
  Drawer,
  Dropdown,
  Space,
  Spin,
  Tag,
} from 'antd'
import { DownOutlined } from '@ant-design/icons'
import type { Key } from 'react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { isUnauthorizedError } from '../../api/http'
import { Check, Download, X } from 'lucide-react'
import {
  exportAdminMediaAccounts,
  getAdminMediaAccount,
  listAdminMediaAccounts,
  listDramaProviderOptions,
  deleteAdminMediaAccount,
  resolveMediaFilingSubmission,
  retryMediaFiling,
  updateManualMediaFilingStatus,
} from '../../features/promotion/mediaAccountApi'
import type {
  AdminMediaAccountDetail,
  AdminMediaAccountListItem,
  DramaProviderOption,
  FilingMethod,
  FilingStatus,
  MediaAccountPageQuery,
  MediaType,
} from '../../features/promotion/mediaAccountTypes'
import './media-account-filing-page.css'

const mediaTypeLabels: Record<MediaType, string> = {
  FACEBOOK: 'Facebook',
  TIKTOK: 'TikTok',
  YOUTUBE: 'YouTube',
  INSTAGRAM: 'Instagram',
}

const filingStatusLabels: Record<FilingStatus, string> = {
  NOT_SUBMITTED: '待提交',
  PENDING: '审核中',
  APPROVED: '已加白',
  REJECTED: '未通过',
  SUBMIT_FAILED: '提交失败',
}

const filingMethodLabels: Record<FilingMethod, string> = {
  API: 'API',
  MANUAL: '人工',
}

type ManualFilingStatus = Extract<FilingStatus, 'APPROVED' | 'REJECTED'>

export function MediaAccountFilingPage() {
  const { message, modal } = AntdApp.useApp()
  const actionRef = useRef<ActionType | undefined>(undefined)
  const exportQueryRef = useRef<MediaAccountPageQuery>({ page: 1, size: 20 })
  const queryContextRef = useRef({ key: '', version: 0 })
  const [providers, setProviders] = useState<DramaProviderOption[]>([])
  const [detail, setDetail] = useState<AdminMediaAccountDetail | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [retryingProviderId, setRetryingProviderId] = useState<number | null>(
    null,
  )
  const [operatingFiling, setOperatingFiling] = useState<string | null>(null)
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([])
  const [selectedRows, setSelectedRows] = useState<AdminMediaAccountListItem[]>(
    [],
  )
  const [bulkOperatingStatus, setBulkOperatingStatus] =
    useState<ManualFilingStatus | null>(null)

  useEffect(() => {
    void listDramaProviderOptions()
      .then(setProviders)
      .catch((error) => {
        if (isUnauthorizedError(error)) return
        message.error(
          error instanceof Error ? error.message : '短剧平台加载失败',
        )
      })
  }, [message])

  const providerNames = useMemo(
    () =>
      new Map(
        providers.map((provider) => [provider.id, provider.providerName]),
      ),
    [providers],
  )

  const openDetail = async (record: AdminMediaAccountListItem) => {
    setDetailOpen(true)
    setDetailLoading(true)
    try {
      setDetail(await getAdminMediaAccount(record.id))
    } catch (error) {
      if (isUnauthorizedError(error)) return
      message.error(error instanceof Error ? error.message : '详情加载失败')
      setDetailOpen(false)
    } finally {
      setDetailLoading(false)
    }
  }

  const handleRetry = async (providerId: number | null) => {
    if (!detail || providerId === null) return
    setRetryingProviderId(providerId)
    try {
      await retryMediaFiling(detail.id, providerId)
      setDetail(await getAdminMediaAccount(detail.id))
      actionRef.current?.reload()
      message.success('已发起重新提交')
    } catch (error) {
      if (isUnauthorizedError(error)) return
      message.error(error instanceof Error ? error.message : '重试失败')
    } finally {
      setRetryingProviderId(null)
    }
  }

  const refreshDetail = async () => {
    if (!detail) return
    setDetail(await getAdminMediaAccount(detail.id))
    actionRef.current?.reload()
  }

  const handleManualStatus = async (
    accountId: number,
    providerId: number | null,
    status: ManualFilingStatus,
  ) => {
    if (providerId === null) return
    setOperatingFiling(`${accountId}:${providerId}:${status}`)
    try {
      await updateManualMediaFilingStatus(accountId, providerId, status)
      if (detail?.id === accountId) {
        await refreshDetail()
      } else {
        actionRef.current?.reload()
      }
      message.success('人工报白状态已更新')
    } catch (error) {
      if (isUnauthorizedError(error)) return
      message.error(error instanceof Error ? error.message : '状态更新失败')
    } finally {
      setOperatingFiling(null)
    }
  }

  const handleBulkManualStatus = (status: ManualFilingStatus) => {
    const batchQueryContext = queryContextRef.current
    const action = manualStatusActions.find(
      (candidate) => candidate.status === status,
    )
    const actionableRows = selectedRows.filter(
      (record) =>
        isManualFilingSelectable(record) && record.filingStatus !== status,
    )
    if (!action || actionableRows.length === 0) {
      message.info('所选记录已是目标状态')
      return
    }

    modal.confirm({
      title: `确认将 ${actionableRows.length} 条记录设为${action.label}？`,
      content: '每条记录独立更新，失败记录将保留选择。',
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        setBulkOperatingStatus(status)
        try {
          const results = await Promise.allSettled(
            actionableRows.map((record) =>
              updateManualMediaFilingStatus(
                record.id,
                record.providerId as number,
                status,
              ),
            ),
          )
          const failedRows = actionableRows.filter(
            (_, index) => results[index].status === 'rejected',
          )
          const successCount = results.length - failedRows.length
          const queryContextBeforeReload = queryContextRef.current
          await actionRef.current?.reload()
          const queryContextAfterReload = queryContextRef.current
          const canRestoreFailedRows =
            queryContextBeforeReload.version === batchQueryContext.version &&
            queryContextBeforeReload.key === batchQueryContext.key &&
            queryContextAfterReload.version === batchQueryContext.version + 1 &&
            queryContextAfterReload.key === batchQueryContext.key

          if (failedRows.length === 0) {
            setSelectedRowKeys([])
            setSelectedRows([])
            message.success(`已批量更新 ${successCount} 条记录`)
            return
          }

          setSelectedRowKeys(
            canRestoreFailedRows ? failedRows.map((record) => record.id) : [],
          )
          setSelectedRows(canRestoreFailedRows ? failedRows : [])
          const onlyUnauthorizedFailures = results.every(
            (result) =>
              result.status === 'fulfilled' ||
              isUnauthorizedError(result.reason),
          )
          if (!onlyUnauthorizedFailures) {
            message.error(
              `批量更新完成：成功 ${successCount} 条，失败 ${failedRows.length} 条`,
            )
          }
        } finally {
          setBulkOperatingStatus(null)
        }
      },
    })
  }

  const handleSubmissionResolution = async (
    providerId: number | null,
    resolution: 'RECEIVED' | 'NOT_RECEIVED',
  ) => {
    if (!detail || providerId === null) return
    setOperatingFiling(`${detail.id}:${providerId}:${resolution}`)
    try {
      await resolveMediaFilingSubmission(detail.id, providerId, resolution)
      await refreshDetail()
      message.success('提交结果已核实')
    } catch (error) {
      if (isUnauthorizedError(error)) return
      message.error(error instanceof Error ? error.message : '提交结果核实失败')
    } finally {
      setOperatingFiling(null)
    }
  }

  const confirmDelete = (record: AdminMediaAccountListItem) => {
    modal.confirm({
      title: '确认删除这个媒体账号？',
      content: '仅删除本系统账号及报白记录，不会删除甲方记录。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteAdminMediaAccount(record.id)
          if (detail?.id === record.id) setDetailOpen(false)
          actionRef.current?.reload()
          message.success('媒体账号删除成功')
        } catch (error) {
          if (isUnauthorizedError(error)) return
          message.error(error instanceof Error ? error.message : '删除失败')
        }
      },
    })
  }

  const columns: ProColumns<AdminMediaAccountListItem>[] = [
    {
      title: '用户编号',
      dataIndex: 'userNo',
      fieldProps: { placeholder: '请输入用户编号' },
      fixed: 'left',
      width: 150,
    },
    { title: '用户昵称', dataIndex: 'nickname', search: false, width: 130 },
    {
      title: '媒体平台',
      dataIndex: 'mediaType',
      valueEnum: Object.fromEntries(
        Object.entries(mediaTypeLabels).map(([value, text]) => [
          value,
          { text },
        ]),
      ),
      width: 120,
      renderText: (value) => mediaTypeLabels[value as MediaType] ?? value,
    },
    {
      title: '账号 ID',
      dataIndex: 'externalAccountId',
      search: false,
      width: 180,
    },
    { title: '账号名称', dataIndex: 'accountName', search: false, width: 150 },
    {
      title: '账号状态',
      dataIndex: 'status',
      valueEnum: { 1: { text: '启用' }, 0: { text: '禁用' } },
      width: 100,
      render: (_, record) => <AccountStatusTag status={record.status} />,
    },
    {
      title: '报备状态',
      dataIndex: 'filingStatus',
      valueEnum: Object.fromEntries(
        Object.entries(filingStatusLabels).map(([value, text]) => [
          value,
          { text },
        ]),
      ),
      width: 110,
      render: (_, record) => <FilingStatusTag status={record.filingStatus} />,
    },
    {
      title: '报白方式',
      dataIndex: 'filingMethod',
      valueEnum: Object.fromEntries(
        Object.entries(filingMethodLabels).map(([value, text]) => [
          value,
          { text },
        ]),
      ),
      width: 100,
      renderText: (value) =>
        value ? filingMethodLabels[value as FilingMethod] : '-',
    },
    {
      title: '短剧平台',
      dataIndex: 'providerId',
      valueType: 'select',
      valueEnum: Object.fromEntries(
        providers.map((provider) => [
          provider.id,
          { text: provider.providerName },
        ]),
      ),
      width: 120,
      renderText: (value) => providerNames.get(Number(value)) ?? '-',
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      search: false,
      width: 160,
      renderText: formatDate,
    },
    {
      title: '操作',
      valueType: 'option',
      fixed: 'right',
      width: 150,
      render: (_, record) => [
        <Button
          key="detail"
          type="link"
          size="small"
          data-testid={`media-account-detail-${record.id}`}
          onClick={() => void openDetail(record)}
        >
          详情
        </Button>,
        renderMoreAction(record),
      ],
    },
  ]

  const loadPage = useCallback(async (params: Record<string, unknown>) => {
    setSelectedRowKeys([])
    setSelectedRows([])
    const query: MediaAccountPageQuery = {
      page: Number(params.current ?? 1),
      size: Number(params.pageSize ?? 20),
      userNo: stringValue(params.userNo),
      mediaType: params.mediaType as MediaType | undefined,
      accountStatus: numberValue(params.accountStatus),
      providerId: numberValue(params.providerId),
      filingMethod: params.filingMethod as FilingMethod | undefined,
      filingStatus: params.filingStatus as FilingStatus | undefined,
    }
    queryContextRef.current = {
      key: JSON.stringify(query),
      version: queryContextRef.current.version + 1,
    }
    exportQueryRef.current = query
    const result = await listAdminMediaAccounts(query)
    return { data: result.list, total: result.total, success: true }
  }, [])

  const handleExport = async () => {
    try {
      const blob = await exportAdminMediaAccounts(exportQueryRef.current)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = 'media-account-filings.xlsx'
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (error) {
      if (isUnauthorizedError(error)) return
      message.error(error instanceof Error ? error.message : '账号报白导出失败')
    }
  }

  const renderMoreAction = (record: AdminMediaAccountListItem) => {
    const items: MenuProps['items'] = []
    if (record.filingMethod === 'MANUAL' && record.providerId !== null) {
      items.push(
        ...manualStatusActions
          .filter((action) => action.status !== record.filingStatus)
          .map((action) => ({
            key: action.status,
            label: action.label,
          })),
        { type: 'divider' },
      )
    }
    items.push({ key: 'delete', label: '删除', danger: true })

    return (
      <Dropdown
        key="more"
        trigger={['click']}
        menu={{
          items,
          onClick: ({ key }) => {
            if (key === 'delete') {
              confirmDelete(record)
              return
            }
            const action = manualStatusActions.find(
              (candidate) => candidate.status === key,
            )
            if (action) {
              void handleManualStatus(
                record.id,
                record.providerId,
                action.status,
              )
            }
          },
        }}
      >
        <Button
          type="link"
          size="small"
          data-testid={`media-account-more-${record.id}`}
        >
          更多 <DownOutlined />
        </Button>
      </Dropdown>
    )
  }

  return (
    <PageContainer
      className="media-account-filing-page"
      data-testid="media-account-filing-page"
    >
      <ProTable<AdminMediaAccountListItem>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={loadPage}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys, rows) => {
            setSelectedRowKeys(keys)
            setSelectedRows(rows)
          },
          getCheckboxProps: (record) => ({
            disabled: !isManualFilingSelectable(record),
          }),
        }}
        toolBarRender={() => [
          <Button
            key="bulk-approve"
            icon={<Check size={16} />}
            disabled={
              selectedRowKeys.length === 0 || bulkOperatingStatus !== null
            }
            loading={bulkOperatingStatus === 'APPROVED'}
            onClick={() => handleBulkManualStatus('APPROVED')}
          >
            批量通过
          </Button>,
          <Button
            key="bulk-reject"
            danger
            icon={<X size={16} />}
            disabled={
              selectedRowKeys.length === 0 || bulkOperatingStatus !== null
            }
            loading={bulkOperatingStatus === 'REJECTED'}
            onClick={() => handleBulkManualStatus('REJECTED')}
          >
            批量未通过
          </Button>,
          <Button
            key="export"
            icon={<Download size={16} />}
            onClick={() => void handleExport()}
          >
            导出 Excel
          </Button>,
        ]}
        search={{ labelWidth: 88 }}
        options={{
          density: true,
          fullScreen: true,
          reload: true,
          setting: true,
        }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        scroll={{ x: 1250 }}
      />

      <Drawer
        title="媒体账号详情"
        open={detailOpen}
        width={800}
        rootClassName="media-account-filing-page__drawer"
        onClose={() => setDetailOpen(false)}
      >
        <Spin spinning={detailLoading}>
          {detail ? (
            <div>
              <section className="media-account-filing-page__section">
                <div className="media-account-filing-page__section-title">
                  <span className="media-account-filing-page__marker" />
                  推广用户
                </div>
                <div className="media-account-filing-page__identity">
                  <Avatar>{detail.nickname?.slice(0, 1) ?? '用'}</Avatar>
                  <div>
                    <strong>{detail.nickname || '-'}</strong>
                    <span>{detail.userNo}</span>
                  </div>
                </div>
                <Descriptions column={2} size="small">
                  <Descriptions.Item label="姓名">
                    {detail.realName || '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="用户编号">
                    {detail.userNo}
                  </Descriptions.Item>
                </Descriptions>
              </section>

              <section className="media-account-filing-page__section">
                <div className="media-account-filing-page__section-title">
                  <span className="media-account-filing-page__marker" />
                  媒体账号资料
                </div>
                <Descriptions column={2} size="small">
                  <Descriptions.Item label="媒体平台">
                    {mediaTypeLabels[detail.mediaAccount.mediaType]}
                  </Descriptions.Item>
                  <Descriptions.Item label="账号 ID">
                    {detail.mediaAccount.externalAccountId}
                  </Descriptions.Item>
                  <Descriptions.Item label="账号名称">
                    {detail.mediaAccount.accountName || '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="账号状态">
                    <AccountStatusTag status={detail.mediaAccount.status} />
                  </Descriptions.Item>
                  <Descriptions.Item label="主页链接" span={2}>
                    {detail.mediaAccount.accountLink || '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="创建时间">
                    {formatDate(detail.mediaAccount.createdAt)}
                  </Descriptions.Item>
                  <Descriptions.Item label="更新时间">
                    {formatDate(detail.mediaAccount.updatedAt)}
                  </Descriptions.Item>
                </Descriptions>
              </section>

              <section className="media-account-filing-page__section">
                <div className="media-account-filing-page__section-title">
                  <span className="media-account-filing-page__marker" />
                  平台报备
                </div>
                {detail.mediaAccount.filings.length === 0 ? (
                  <span>暂无报备记录</span>
                ) : (
                  detail.mediaAccount.filings.map((filing) => (
                    <div
                      className="media-account-filing-page__filing"
                      key={filing.providerId}
                      data-testid={`filing-${filing.providerId}`}
                    >
                      <div className="media-account-filing-page__filing-header">
                        <Space>
                          <strong>
                            {filing.providerName ||
                              providerNames.get(filing.providerId ?? 0) ||
                              '-'}
                          </strong>
                          <Tag>{filingMethodLabels[filing.filingMethod]}</Tag>
                          <FilingStatusTag status={filing.status} />
                        </Space>
                        <Space>
                          {filing.submissionResolutionAllowed ? (
                            <>
                              <Button
                                type="link"
                                loading={
                                  operatingFiling ===
                                  `${detail.id}:${filing.providerId}:RECEIVED`
                                }
                                onClick={() =>
                                  void handleSubmissionResolution(
                                    filing.providerId,
                                    'RECEIVED',
                                  )
                                }
                              >
                                确认已收到
                              </Button>
                              <Button
                                type="link"
                                loading={
                                  operatingFiling ===
                                  `${detail.id}:${filing.providerId}:NOT_RECEIVED`
                                }
                                onClick={() =>
                                  void handleSubmissionResolution(
                                    filing.providerId,
                                    'NOT_RECEIVED',
                                  )
                                }
                              >
                                确认未收到
                              </Button>
                            </>
                          ) : null}
                          {filing.retryAllowed ? (
                            <Button
                              type="link"
                              loading={retryingProviderId === filing.providerId}
                              onClick={() =>
                                void handleRetry(filing.providerId)
                              }
                            >
                              重新提交
                            </Button>
                          ) : null}
                        </Space>
                      </div>
                      <div className="media-account-filing-page__filing-meta">
                        <span>
                          外部报备编号：{filing.externalFilingId || '-'}
                        </span>
                        <span>
                          下次处理时间：{formatDate(filing.nextActionAt)}
                        </span>
                        <span>
                          提交甲方时间：{formatDate(filing.lastSubmittedAt)}
                        </span>
                        <span>
                          最后查询时间：{formatDate(filing.lastQueriedAt)}
                        </span>
                        <span>
                          甲方审核时间：{formatDate(filing.operateTime)}
                        </span>
                      </div>
                      {filing.lastErrorMessage ? (
                        <div className="media-account-filing-page__filing-error">
                          失败原因：{filing.lastErrorMessage}
                        </div>
                      ) : null}
                    </div>
                  ))
                )}
              </section>
            </div>
          ) : null}
        </Spin>
      </Drawer>
    </PageContainer>
  )
}

function AccountStatusTag({ status }: { status: number }) {
  return status === 1 ? <Tag color="success">启用</Tag> : <Tag>禁用</Tag>
}

function FilingStatusTag({ status }: { status: FilingStatus | null }) {
  const label = status ? filingStatusLabels[status] : '待提交'
  const color =
    label === '已加白'
      ? 'success'
      : label === '未通过' || label === '提交失败'
        ? 'error'
        : label === '审核中'
          ? 'processing'
          : 'default'
  return <Tag color={color}>{label}</Tag>
}

const manualStatusActions: Array<{
  status: ManualFilingStatus
  label: string
}> = [
  { status: 'APPROVED', label: '报白通过' },
  { status: 'REJECTED', label: '报白未通过' },
]

function isManualFilingSelectable(record: AdminMediaAccountListItem) {
  return record.filingMethod === 'MANUAL' && record.providerId !== null
}

function stringValue(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

function numberValue(value: unknown) {
  if (value === undefined || value === null || value === '') return undefined
  return Number(value)
}

function formatDate(value: string | null | undefined) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}
