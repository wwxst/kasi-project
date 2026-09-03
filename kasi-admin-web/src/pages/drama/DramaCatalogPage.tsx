import type { ActionType, ProColumns } from '@ant-design/pro-components'
import { PageContainer, ProTable } from '@ant-design/pro-components'
import {
  App as AntdApp,
  Button,
  Descriptions,
  Drawer,
  Image,
  Popconfirm,
  Space,
  Spin,
  Table,
  Tag,
} from 'antd'
import {
  Activity,
  Clapperboard,
  ListChecks,
  RefreshCw,
  Video,
} from 'lucide-react'
import type { Key, ReactNode } from 'react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { isUnauthorizedError } from '../../api/http'
import {
  getDramaCatalogDetail,
  listDramaCatalog,
  requestDramaContentBatchSync,
  requestDramaContentSync,
  updateDramaLocalStatus,
} from '../../features/drama/dramaCatalogApi'
import type {
  DramaCatalogDetail,
  DramaCatalogListItem,
  DramaCatalogPageQuery,
  DramaContent,
  DramaLocalStatus,
} from '../../features/drama/dramaCatalogTypes'
import { formatDramaLanguage } from '../../features/drama/dramaCatalogLocale'
import { listProviders } from '../../features/provider/providerApi'
import type { DramaProvider } from '../../features/provider/providerTypes'
import { DramaContentSyncModal } from './DramaContentSyncModal'
import { DramaContentSyncSection } from './DramaContentSyncSection'
import { DramaSyncModal } from './DramaSyncModal'
import { DramaSyncStatusDrawer } from './DramaSyncStatusDrawer'
import './drama-catalog-page.css'

const localStatusLabels: Record<DramaLocalStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已上架',
  OFFLINE: '已下架',
}

const localStatusColors: Record<DramaLocalStatus, string> = {
  DRAFT: 'default',
  PUBLISHED: 'success',
  OFFLINE: 'warning',
}

export function DramaCatalogPage() {
  const { message } = AntdApp.useApp()
  const actionRef = useRef<ActionType | undefined>(undefined)
  const [providers, setProviders] = useState<DramaProvider[]>([])
  const [detail, setDetail] = useState<DramaCatalogDetail | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [statusUpdatingId, setStatusUpdatingId] = useState<number | null>(null)
  const [syncModalOpen, setSyncModalOpen] = useState(false)
  const [syncStatusOpen, setSyncStatusOpen] = useState(false)
  const [syncProviderId, setSyncProviderId] = useState<number | null>(null)
  const [selectedDramaIds, setSelectedDramaIds] = useState<number[]>([])
  const [contentSyncModalOpen, setContentSyncModalOpen] = useState(false)
  const [contentSyncingId, setContentSyncingId] = useState<number | null>(null)
  const [batchContentSyncing, setBatchContentSyncing] = useState(false)
  const [detailContentRefreshKey, setDetailContentRefreshKey] = useState(0)

  useEffect(() => {
    void listProviders()
      .then((items) => setProviders(items.filter(hasCatalogConnection)))
      .catch((error) => {
        if (isUnauthorizedError(error)) return
        message.error(
          error instanceof Error ? error.message : '短剧平台加载失败',
        )
      })
  }, [message])

  const providerValueEnum = useMemo(
    () =>
      Object.fromEntries(
        providers.map((provider) => [
          provider.id,
          { text: provider.providerName },
        ]),
      ),
    [providers],
  )

  const currentProviderName = providers[0]?.providerName ?? '-'
  const syncProviders = useMemo(
    () => providers.filter(isSyncEnabledProvider),
    [providers],
  )
  const contentSyncProviders = useMemo(
    () =>
      syncProviders.filter((provider) =>
        provider.capabilities.includes('FREE_CONTENT_PREVIEW'),
      ),
    [syncProviders],
  )

  useEffect(() => {
    if (syncProviderId === null && providers.length > 0) {
      setSyncProviderId(providers[0].id)
    }
  }, [providers, syncProviderId])

  const openDetail = async (record: DramaCatalogListItem) => {
    setDetailOpen(true)
    setDetailLoading(true)
    setDetailContentRefreshKey(0)
    try {
      setDetail(await getDramaCatalogDetail(record.id))
    } catch (error) {
      if (isUnauthorizedError(error)) return
      message.error(error instanceof Error ? error.message : '短剧详情加载失败')
      setDetailOpen(false)
    } finally {
      setDetailLoading(false)
    }
  }

  const handleStatusChange = async (record: DramaCatalogListItem) => {
    const localStatus = nextLocalStatus(record.localStatus)
    setStatusUpdatingId(record.id)
    try {
      const updated = await updateDramaLocalStatus(record.id, { localStatus })
      if (detail?.id === record.id) setDetail(updated)
      actionRef.current?.reload()
      message.success(localStatus === 'PUBLISHED' ? '短剧已上架' : '短剧已下架')
    } catch (error) {
      if (isUnauthorizedError(error)) return
      message.error(error instanceof Error ? error.message : '短剧状态更新失败')
    } finally {
      setStatusUpdatingId(null)
    }
  }

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

  const refreshDetail = async (id: number) => {
    try {
      setDetail(await getDramaCatalogDetail(id))
    } catch (error) {
      if (isUnauthorizedError(error)) return
      message.error(error instanceof Error ? error.message : '短剧详情刷新失败')
    }
  }

  const columns: ProColumns<DramaCatalogListItem>[] = [
    {
      title: '短剧名称',
      dataIndex: 'title',
      fieldProps: { placeholder: '请输入短剧名称' },
      search: false,
      hideInTable: true,
    },
    {
      title: '短剧信息',
      dataIndex: 'id',
      search: false,
      width: 360,
      render: (_, record) => <DramaInformation drama={record} />,
    },
    {
      title: '短剧平台',
      dataIndex: 'providerId',
      valueType: 'select',
      valueEnum: providerValueEnum,
      width: 120,
      render: () => currentProviderName,
    },
    {
      title: '语言',
      dataIndex: 'language',
      width: 110,
      fieldProps: { placeholder: '如 ENGLISH' },
      renderText: (value) => formatDramaLanguage(value),
    },
    {
      title: '分类',
      dataIndex: 'categoryName',
      search: false,
      width: 120,
      render: (_, record) => (
        <span data-testid={`drama-category-${record.id}`}>
          {record.categoryName || '-'}
        </span>
      ),
    },
    {
      title: '远端状态',
      dataIndex: 'remoteShowStatus',
      width: 110,
      fieldProps: { placeholder: '请输入原始状态' },
      render: (_, record) => (
        <RemoteStatusTag status={record.remoteShowStatus} />
      ),
    },
    {
      title: '本地状态',
      dataIndex: 'localStatus',
      valueEnum: Object.fromEntries(
        Object.entries(localStatusLabels).map(([value, text]) => [
          value,
          { text },
        ]),
      ),
      width: 110,
      render: (_, record) => <LocalStatusTag status={record.localStatus} />,
    },
    {
      title: '远端更新时间',
      dataIndex: 'remoteUpdatedAt',
      search: false,
      width: 160,
      renderText: formatDate,
    },
    {
      title: '发布时间',
      dataIndex: 'remoteCreatedAt',
      search: false,
      width: 160,
      renderText: formatDate,
    },
    {
      title: '操作',
      valueType: 'option',
      fixed: 'right',
      width: 210,
      render: (_, record) => {
        const publishing = record.localStatus !== 'PUBLISHED'
        return (
          <Space size={4}>
            <Button
              type="link"
              size="small"
              data-testid={`drama-detail-${record.id}`}
              onClick={() => void openDetail(record)}
            >
              详情
            </Button>
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
            <Popconfirm
              title={publishing ? '确认上架这部短剧？' : '确认下架这部短剧？'}
              description={
                publishing
                  ? '上架后可供后续推广业务使用。'
                  : '下架后将停止新的推广使用。'
              }
              okText="确认"
              cancelText="取消"
              okButtonProps={{
                'data-testid': `drama-status-confirm-${record.id}`,
              }}
              onConfirm={() => void handleStatusChange(record)}
            >
              <Button
                type="link"
                size="small"
                danger={!publishing}
                loading={statusUpdatingId === record.id}
                data-testid={`drama-status-${record.id}`}
              >
                {publishing ? '上架' : '下架'}
              </Button>
            </Popconfirm>
          </Space>
        )
      },
    },
  ]

  const loadPage = async (params: Record<string, unknown>) => {
    const query: DramaCatalogPageQuery = {
      page: Number(params.current ?? 1),
      size: Number(params.pageSize ?? 20),
      providerId: numberValue(params.providerId),
      title: stringValue(params.title),
      language: stringValue(params.language),
      remoteShowStatus: stringValue(params.remoteShowStatus),
      localStatus: params.localStatus as DramaLocalStatus | undefined,
    }
    try {
      const result = await listDramaCatalog(query)
      return { data: result.list, total: result.total, success: true }
    } catch (error) {
      if (isUnauthorizedError(error)) {
        return { data: [], total: 0, success: false }
      }
      throw error
    }
  }

  const handleSyncSubmitted = (providerId: number) => {
    setSyncProviderId(providerId)
    setSyncModalOpen(false)
    setSyncStatusOpen(true)
  }

  return (
    <PageContainer
      className="drama-catalog-page"
      title="短剧目录"
      content="管理 GoodShort 已同步短剧、剧集信息和本地上下架状态"
      data-testid="drama-catalog-page"
    >
      <ProTable<DramaCatalogListItem>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={loadPage}
        rowSelection={{
          selectedRowKeys: selectedDramaIds,
          preserveSelectedRowKeys: false,
          onChange: (keys: Key[]) =>
            setSelectedDramaIds(keys.map(Number).slice(0, 100)),
          getCheckboxProps: (record) => ({
            disabled:
              selectedDramaIds.length >= 100 &&
              !selectedDramaIds.includes(record.id),
          }),
        }}
        toolBarRender={() => [
          <Popconfirm
            key="sync-selected-content"
            title={`确认同步所选 ${selectedDramaIds.length} 部短剧？`}
            description="当前操作只同步 GoodShort 免费剧集。"
            okText="确认"
            cancelText="取消"
            okButtonProps={{
              'data-testid': 'drama-content-batch-confirm',
            }}
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
            data-testid="drama-content-sync-all"
            onClick={() => setContentSyncModalOpen(true)}
          >
            同步剧集
          </Button>,
          <Button
            key="sync-status"
            icon={<Activity size={16} />}
            onClick={() => setSyncStatusOpen(true)}
          >
            同步状态
          </Button>,
          <Button
            key="sync-catalog"
            type="primary"
            icon={<RefreshCw size={16} />}
            disabled={syncProviders.length === 0}
            onClick={() => setSyncModalOpen(true)}
          >
            同步目录
          </Button>,
        ]}
        onSubmit={() => setSelectedDramaIds([])}
        onReset={() => setSelectedDramaIds([])}
        search={{ labelWidth: 88 }}
        options={{
          density: true,
          fullScreen: true,
          reload: true,
          setting: true,
        }}
        pagination={{
          defaultPageSize: 20,
          showSizeChanger: true,
          onChange: () => setSelectedDramaIds([]),
        }}
        scroll={{ x: 1370 }}
      />

      <Drawer
        title="短剧详情"
        open={detailOpen}
        size={880}
        rootClassName="drama-catalog-page__drawer"
        onClose={() => setDetailOpen(false)}
      >
        <div data-testid="drama-detail-drawer">
          <Spin spinning={detailLoading}>
            {detail ? (
              <DramaDetail
                detail={detail}
                contentSyncSection={
                  <DramaContentSyncSection
                    dramaId={detail.id}
                    active={detailOpen}
                    refreshKey={detailContentRefreshKey}
                    onSucceeded={() => void refreshDetail(detail.id)}
                  />
                }
              />
            ) : null}
          </Spin>
        </div>
      </Drawer>

      <DramaSyncModal
        open={syncModalOpen}
        providers={syncProviders}
        preferredProviderId={syncProviderId}
        onClose={() => setSyncModalOpen(false)}
        onSubmitted={handleSyncSubmitted}
      />

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

      <DramaSyncStatusDrawer
        open={syncStatusOpen}
        providers={providers}
        providerId={syncProviderId}
        onProviderChange={setSyncProviderId}
        onClose={() => setSyncStatusOpen(false)}
      />
    </PageContainer>
  )
}

function DramaDetail({
  detail,
  contentSyncSection,
}: {
  detail: DramaCatalogDetail
  contentSyncSection: ReactNode
}) {
  const episodeColumns = [
    { title: '集数', dataIndex: 'sequenceNo', width: 72 },
    {
      title: '标题',
      dataIndex: 'title',
      ellipsis: true,
      render: (value: string | null) => value || '-',
    },
    {
      title: '外部剧集 ID',
      dataIndex: 'externalContentId',
      width: 150,
      ellipsis: true,
      render: (value: string | null) => value || '-',
    },
    {
      title: '试看',
      dataIndex: 'free',
      width: 80,
      render: (free: boolean) =>
        free ? <Tag color="success">免费</Tag> : <Tag>付费</Tag>,
    },
    {
      title: '时长',
      dataIndex: 'durationSeconds',
      width: 90,
      render: (value: number | null) => formatDuration(value),
    },
    {
      title: '远端更新时间',
      dataIndex: 'remoteUpdatedAt',
      width: 150,
      render: (value: string | null) => formatDate(value),
    },
  ]

  return (
    <div className="drama-catalog-page__detail">
      <div className="drama-catalog-page__identity">
        <DramaCover coverUrl={detail.coverUrl} title={detail.title} large />
        <div>
          <h2>{detail.title || '-'}</h2>
          {detail.originalTitle && detail.originalTitle !== detail.title ? (
            <p>{detail.originalTitle}</p>
          ) : null}
          <span>{detail.externalDramaId}</span>
        </div>
      </div>

      <section className="drama-catalog-page__section">
        <h3>基本信息</h3>
        <Descriptions column={2} size="small">
          <Descriptions.Item label="短剧平台">GoodShort</Descriptions.Item>
          <Descriptions.Item label="本地状态">
            <LocalStatusTag status={detail.localStatus} />
          </Descriptions.Item>
          <Descriptions.Item label="语言">
            {formatDramaLanguage(detail.language)}
          </Descriptions.Item>
          <Descriptions.Item label="类型">
            {detail.dramaType || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="标签">
            {detail.labelNames?.length ? (
              <Space wrap size={[4, 4]}>
                {detail.labelNames.map((label) => (
                  <Tag key={label}>{label}</Tag>
                ))}
              </Space>
            ) : (
              '-'
            )}
          </Descriptions.Item>
          <Descriptions.Item label="远端状态">
            <Space size={6}>
              <RemoteStatusTag status={detail.remoteShowStatus} />
              {detail.remoteShowStatus ? (
                <span>原始值：{detail.remoteShowStatus}</span>
              ) : null}
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="远端更新时间">
            {formatDate(detail.remoteUpdatedAt)}
          </Descriptions.Item>
          <Descriptions.Item label="发布时间">
            {formatDate(detail.remoteCreatedAt)}
          </Descriptions.Item>
          <Descriptions.Item label="最近同步可见" span={2}>
            {formatDate(detail.lastSeenAt)}
          </Descriptions.Item>
          <Descriptions.Item label="本地创建时间">
            {formatDate(detail.createdAt)}
          </Descriptions.Item>
          <Descriptions.Item label="本地更新时间">
            {formatDate(detail.updatedAt)}
          </Descriptions.Item>
          <Descriptions.Item label="简介" span={2}>
            <span className="drama-catalog-page__description">
              {detail.description || '-'}
            </span>
          </Descriptions.Item>
        </Descriptions>
      </section>

      {contentSyncSection}

      <section className="drama-catalog-page__section">
        <div className="drama-catalog-page__section-heading">
          <h3>剧集</h3>
          <span>共 {detail.contents.length} 集</span>
        </div>
        <Table<DramaContent>
          rowKey="id"
          size="small"
          columns={episodeColumns}
          dataSource={detail.contents}
          pagination={false}
          scroll={{ x: 760 }}
        />
      </section>
    </div>
  )
}

function DramaInformation({ drama }: { drama: DramaCatalogListItem }) {
  const primaryTitle = drama.titleZh || drama.title || '-'
  const secondaryTitle =
    drama.originalTitle && drama.originalTitle !== primaryTitle
      ? drama.originalTitle
      : drama.title && drama.title !== primaryTitle
        ? drama.title
        : null

  return (
    <div
      className="drama-catalog-page__information"
      data-testid={`drama-info-${drama.id}`}
    >
      <DramaCover
        coverUrl={drama.coverUrl}
        title={primaryTitle}
        fallbackTestId={`drama-cover-fallback-${drama.id}`}
      />
      <div className="drama-catalog-page__information-content">
        <div
          className="drama-catalog-page__information-title-row"
          data-testid="drama-info-title-row"
        >
          <strong className="drama-catalog-page__information-title">
            {primaryTitle}
          </strong>
        </div>
        {secondaryTitle ? (
          <div
            className="drama-catalog-page__information-subtitle-row"
            data-testid="drama-info-subtitle-row"
          >
            <span className="drama-catalog-page__information-secondary-title">
              {secondaryTitle}
            </span>
          </div>
        ) : null}
        <div
          className="drama-catalog-page__information-tags-row"
          data-testid="drama-info-tags-row"
        >
          {(drama.labelNames ?? []).map((label) => (
            <Tag key={label}>{label}</Tag>
          ))}
        </div>
      </div>
    </div>
  )
}

function DramaCover({
  coverUrl,
  title,
  large = false,
  fallbackTestId,
}: {
  coverUrl: string | null
  title: string | null
  large?: boolean
  fallbackTestId?: string
}) {
  const [loadFailed, setLoadFailed] = useState(false)

  useEffect(() => setLoadFailed(false), [coverUrl])

  return (
    <div
      className={
        large
          ? 'drama-catalog-page__cover drama-catalog-page__cover--large'
          : 'drama-catalog-page__cover'
      }
    >
      {coverUrl && !loadFailed ? (
        <Image
          src={coverUrl}
          alt={title || '短剧封面'}
          preview={false}
          onError={() => setLoadFailed(true)}
        />
      ) : (
        <span data-testid={fallbackTestId}>
          <Clapperboard size={large ? 28 : 20} strokeWidth={1.6} />
        </span>
      )}
    </div>
  )
}

function LocalStatusTag({ status }: { status: DramaLocalStatus }) {
  return (
    <Tag color={localStatusColors[status]}>{localStatusLabels[status]}</Tag>
  )
}

function RemoteStatusTag({ status }: { status: string | null }) {
  if (!status) return <Tag>未知</Tag>
  return status === '1' ? (
    <Tag color="success">在线</Tag>
  ) : (
    <Tag color="warning">已下架</Tag>
  )
}

function hasCatalogConnection(provider: DramaProvider) {
  return (
    provider.connection !== null &&
    provider.capabilities.some(
      (capability) =>
        capability === 'FULL_DRAMA_SYNC' ||
        capability === 'INCREMENTAL_DRAMA_SYNC',
    )
  )
}

function isSyncEnabledProvider(provider: DramaProvider) {
  return (
    hasCatalogConnection(provider) &&
    provider.status === 1 &&
    provider.connection?.status === 1
  )
}

function nextLocalStatus(status: DramaLocalStatus): DramaLocalStatus {
  return status === 'PUBLISHED' ? 'OFFLINE' : 'PUBLISHED'
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

function formatDuration(value: number | null) {
  if (value === null) return '-'
  const minutes = Math.floor(value / 60)
  const seconds = value % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}
