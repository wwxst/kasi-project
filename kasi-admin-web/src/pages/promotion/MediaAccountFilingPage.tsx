import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { PageContainer, ProTable } from '@ant-design/pro-components'
import {
  App as AntdApp,
  Avatar,
  Button,
  Descriptions,
  Drawer,
  Popconfirm,
  Space,
  Spin,
  Tag,
} from 'antd'
import { useEffect, useMemo, useRef, useState } from 'react'
import { isUnauthorizedError } from '../../api/http'
import {
  getAdminMediaAccount,
  listAdminMediaAccounts,
  listDramaProviderOptions,
  deleteAdminMediaAccount,
  retryMediaFiling,
} from '../../features/promotion/mediaAccountApi'
import type {
  AdminMediaAccountDetail,
  AdminMediaAccountListItem,
  DramaProviderOption,
  FilingStatus,
  FilingDisplayStatus,
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

const filingStatusLabels: Record<FilingDisplayStatus, string> = {
  NOT_SUBMITTED: '待提交',
  SUBMIT_FAILED: '提交失败',
  PENDING: '审核中',
  APPROVED: '已加白',
  FAILED: '已拒绝',
  QUERY_FAILED: '查询失败',
}

export function MediaAccountFilingPage() {
  const { message } = AntdApp.useApp()
  const actionRef = useRef<ActionType | undefined>(undefined)
  const [providers, setProviders] = useState<DramaProviderOption[]>([])
  const [detail, setDetail] = useState<AdminMediaAccountDetail | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [retryingProviderId, setRetryingProviderId] = useState<number | null>(
    null,
  )

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

  const handleDelete = async () => {
    if (!detail) return
    try {
      await deleteAdminMediaAccount(detail.id)
      setDetailOpen(false)
      actionRef.current?.reload()
      message.success('媒体账号删除成功')
    } catch (error) {
      if (isUnauthorizedError(error)) return
      message.error(error instanceof Error ? error.message : '删除失败')
    }
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
      render: (_, record) => (
        <FilingStatusTag
          status={record.filingStatus}
          lastSubmittedAt={record.filingLastSubmittedAt}
          lastErrorMessage={record.filingLastErrorMessage}
          remoteStatus={record.filingRemoteStatus}
        />
      ),
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
      width: 80,
      render: (_, record) => (
        <Button
          type="link"
          size="small"
          data-testid={`media-account-detail-${record.id}`}
          onClick={() => void openDetail(record)}
        >
          详情
        </Button>
      ),
    },
  ]

  const loadPage = async (params: Record<string, unknown>) => {
    const query: MediaAccountPageQuery = {
      page: Number(params.current ?? 1),
      size: Number(params.pageSize ?? 20),
      userNo: stringValue(params.userNo),
      mediaType: params.mediaType as MediaType | undefined,
      accountStatus: numberValue(params.accountStatus),
      providerId: numberValue(params.providerId),
      filingStatus: params.filingStatus as FilingDisplayStatus | undefined,
    }
    const result = await listAdminMediaAccounts(query)
    return { data: result.list, total: result.total, success: true }
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
        toolBarRender={false}
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
                  {canDeleteAccount(detail.mediaAccount.filings) ? (
                    <Popconfirm
                      title="确认删除这个媒体账号？"
                      description="删除后可重新提交相同平台和账号 ID。"
                      okText="删除"
                      cancelText="取消"
                      onConfirm={() => void handleDelete()}
                    >
                      <Button type="link" danger size="small">
                        删除
                      </Button>
                    </Popconfirm>
                  ) : null}
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
                    >
                      <div className="media-account-filing-page__filing-header">
                        <Space>
                          <strong>
                            {filing.providerName ||
                              providerNames.get(filing.providerId ?? 0) ||
                              '-'}
                          </strong>
                          <FilingStatusTag
                            status={filing.status}
                            lastSubmittedAt={filing.lastSubmittedAt}
                            lastErrorMessage={filing.lastErrorMessage}
                            remoteStatus={filing.remoteStatus}
                          />
                        </Space>
                        {!filing.lastSubmittedAt && filing.lastErrorMessage ? (
                          <Button
                            type="link"
                            loading={retryingProviderId === filing.providerId}
                            onClick={() => void handleRetry(filing.providerId)}
                          >
                            重新提交
                          </Button>
                        ) : null}
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

function FilingStatusTag({
  status,
  lastSubmittedAt,
  lastErrorMessage,
  remoteStatus,
}: {
  status: FilingStatus | null
  lastSubmittedAt?: string | null
  lastErrorMessage?: string | null
  remoteStatus?: string | null
}) {
  const label = getFilingStatusLabel(
    status,
    lastSubmittedAt,
    lastErrorMessage,
    remoteStatus,
  )
  const color =
    label === '已加白'
      ? 'success'
      : label === '已拒绝' || label === '提交失败' || label === '查询失败'
        ? 'error'
        : label === '审核中'
          ? 'processing'
          : 'default'
  return <Tag color={color}>{label}</Tag>
}

function getFilingStatusLabel(
  status: FilingStatus | null,
  lastSubmittedAt?: string | null,
  lastErrorMessage?: string | null,
  remoteStatus?: string | null,
) {
  if (lastSubmittedAt && lastErrorMessage) return '查询失败'
  if (!lastSubmittedAt) {
    if (lastErrorMessage) return '提交失败'
    if (status === 'APPROVED') return '已加白'
    if (status === 'FAILED') return '已拒绝'
    return '待提交'
  }
  if (status === 'APPROVED' || remoteStatus === '1') return '已加白'
  if (remoteStatus === '2') return '已拒绝'
  return '审核中'
}

function canDeleteAccount(
  filings: AdminMediaAccountDetail['mediaAccount']['filings'],
) {
  return (
    filings.length > 0 &&
    filings.every(
      (filing) =>
        (filing.lastSubmittedAt == null &&
          filing.nextActionAt == null &&
          filing.lastErrorMessage != null) ||
        (filing.remoteStatus === '2' && filing.nextActionAt == null),
    )
  )
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
