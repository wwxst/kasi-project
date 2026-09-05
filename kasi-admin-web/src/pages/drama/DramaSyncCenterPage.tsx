import {
  App as AntdApp,
  Button,
  Descriptions,
  Drawer,
  Empty,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { RefreshCw } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { isUnauthorizedError } from '../../api/http'
import {
  getDramaContentSyncRecordDetails,
  getDramaSyncRecordDetails,
  listDramaLanguageOptions,
  listDramaContentSyncRecords,
  listDramaSyncRecords,
  requestDramaCatalogSync,
  requestDramaContentBatchSync,
} from '../../features/drama/dramaCatalogApi'
import type {
  DramaContentSyncRecordDetail,
  DramaSyncRecord,
  DramaSyncRecordDetail,
  DramaSyncTaskType,
  SyncRecordStatus,
  SyncTriggerSource,
} from '../../features/drama/dramaCatalogTypes'
import { listProviders } from '../../features/provider/providerApi'
import type { DramaProvider as Provider } from '../../features/provider/providerTypes'
import { DramaContentSyncModal } from './DramaContentSyncModal'
import { DramaSyncModal } from './DramaSyncModal'
import './drama-sync-center-page.css'

const taskTypeLabels: Record<DramaSyncTaskType, string> = {
  FULL: '全量同步',
  INCREMENTAL: '增量同步',
  MIXED: '混合同步',
  SINGLE: '单部同步',
  BATCH: '批量同步',
  ALL: '全部同步',
  MISSING: '补齐缺失',
  CATALOG_AUTO: '目录自动同步',
}

const statusLabels: Record<SyncRecordStatus, string> = {
  WAITING: '等待中',
  RUNNING: '同步中',
  SUCCESS: '成功',
  PARTIAL_FAILED: '部分失败',
  FAILED: '失败',
}

const triggerLabels: Record<SyncTriggerSource, string> = {
  MANUAL: '手动',
  SCHEDULED: '定时',
}

type Domain = 'catalog' | 'content'

export function DramaSyncCenterPage() {
  const location = useLocation()
  return (
    <SyncRecordsPage
      domain={location.pathname.endsWith('/content') ? 'content' : 'catalog'}
    />
  )
}

export function DramaCatalogSyncPage() {
  return <SyncRecordsPage domain="catalog" />
}

export function DramaContentSyncPage() {
  return <SyncRecordsPage domain="content" />
}

function SyncRecordsPage({ domain }: { domain: Domain }) {
  const { message } = AntdApp.useApp()
  const [providers, setProviders] = useState<Provider[]>([])
  const [providerId, setProviderId] = useState<number | null>(null)
  const [records, setRecords] = useState<DramaSyncRecord[]>([])
  const [loading, setLoading] = useState(false)
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [selectedRecord, setSelectedRecord] = useState<DramaSyncRecord | null>(
    null,
  )
  const [catalogDetails, setCatalogDetails] = useState<DramaSyncRecordDetail[]>(
    [],
  )
  const [contentDetails, setContentDetails] = useState<
    DramaContentSyncRecordDetail[]
  >([])
  const [catalogModalOpen, setCatalogModalOpen] = useState(false)
  const [contentModalOpen, setContentModalOpen] = useState(false)
  const [languageOptions, setLanguageOptions] = useState<
    Awaited<ReturnType<typeof listDramaLanguageOptions>>
  >([])
  useEffect(() => {
    void listDramaLanguageOptions()
      .then(setLanguageOptions)
      .catch((error) => {
        if (isUnauthorizedError(error)) return
        message.error(
          error instanceof Error ? error.message : '语言列表加载失败',
        )
      })
  }, [message])

  const availableProviders = useMemo(
    () => providers.filter((provider) => provider.connection),
    [providers],
  )
  const contentProviders = useMemo(
    () =>
      availableProviders.filter((provider) =>
        provider.capabilities.includes('FREE_CONTENT_PREVIEW'),
      ),
    [availableProviders],
  )
  const catalogProviders = useMemo(
    () =>
      availableProviders.filter((provider) =>
        provider.capabilities.includes('FULL_DRAMA_SYNC'),
      ),
    [availableProviders],
  )
  const pageProviders = availableProviders

  useEffect(() => {
    void listProviders()
      .then((items) => setProviders(items))
      .catch((error) => {
        if (!isUnauthorizedError(error))
          message.error(
            error instanceof Error ? error.message : '短剧平台加载失败',
          )
      })
  }, [message])

  useEffect(() => {
    setProviderId((current) =>
      current !== null &&
      pageProviders.some((provider) => provider.id === current)
        ? current
        : (pageProviders[0]?.id ?? null),
    )
  }, [pageProviders])

  const loadRecords = useCallback(async () => {
    if (providerId === null) {
      setRecords([])
      return
    }
    setLoading(true)
    try {
      setRecords(
        domain === 'catalog'
          ? await listDramaSyncRecords(providerId)
          : await listDramaContentSyncRecords(providerId),
      )
    } catch (error) {
      if (!isUnauthorizedError(error))
        message.error(
          error instanceof Error ? error.message : '同步记录加载失败',
        )
    } finally {
      setLoading(false)
    }
  }, [domain, message, providerId])

  useEffect(() => {
    void loadRecords()
  }, [loadRecords])

  const openDetails = async (record: DramaSyncRecord) => {
    if (providerId === null) return
    setSelectedRecord(record)
    setDetailOpen(true)
    setDetailLoading(true)
    try {
      if (domain === 'catalog') {
        setCatalogDetails(
          await getDramaSyncRecordDetails(providerId, record.id),
        )
        setContentDetails([])
      } else {
        setContentDetails(
          await getDramaContentSyncRecordDetails(providerId, record.id),
        )
        setCatalogDetails([])
      }
    } catch (error) {
      if (!isUnauthorizedError(error))
        message.error(
          error instanceof Error ? error.message : '同步详情加载失败',
        )
    } finally {
      setDetailLoading(false)
    }
  }

  const summary = useMemo(
    () => ({
      total: records.length,
      success: records.filter((record) => record.status === 'SUCCESS').length,
      running: records.filter(
        (record) => record.status === 'RUNNING' || record.status === 'WAITING',
      ).length,
      failed: records.filter(
        (record) =>
          record.status === 'FAILED' || record.status === 'PARTIAL_FAILED',
      ).length,
    }),
    [records],
  )

  const retryCatalog = async (detail: DramaSyncRecordDetail) => {
    if (providerId === null || !detail.language) return
    await requestDramaCatalogSync({
      providerId,
      syncType: detail.syncType,
      languages: [detail.language],
    })
    message.success('重试任务已提交')
    await loadRecords()
  }

  const retryContent = async (detail: DramaContentSyncRecordDetail) => {
    await requestDramaContentBatchSync([detail.dramaId])
    message.success('重试任务已提交')
    await loadRecords()
  }

  return (
    <section
      className="drama-sync-center-page"
      data-testid={`drama-sync-${domain}-page`}
    >
      <div className="drama-sync-center-page__heading">
        <div>
          <h1>{domain === 'catalog' ? '短剧同步' : '剧集同步'}</h1>
          <p>
            {domain === 'catalog'
              ? '按一次目录同步触发聚合展示各语言任务。'
              : '按一次剧集同步触发聚合展示各短剧任务。'}
          </p>
        </div>
        <Space>
          <Select
            aria-label={`${domain === 'catalog' ? '短剧' : '剧集'}同步平台`}
            value={providerId ?? undefined}
            placeholder="请选择短剧平台"
            options={pageProviders.map((provider) => ({
              value: provider.id,
              label: provider.providerName,
            }))}
            onChange={setProviderId}
          />
          <Tooltip title="刷新同步记录">
            <Button
              aria-label="刷新同步记录"
              icon={<RefreshCw size={16} />}
              loading={loading}
              onClick={() => void loadRecords()}
            />
          </Tooltip>
          <Button
            type="primary"
            onClick={() =>
              domain === 'catalog'
                ? setCatalogModalOpen(true)
                : setContentModalOpen(true)
            }
          >
            {domain === 'catalog' ? '发起短剧同步' : '同步免费剧集'}
          </Button>
        </Space>
      </div>
      <div className="drama-sync-center-page__summary">
        <span>记录 {summary.total}</span>
        <span className="is-success">成功 {summary.success}</span>
        <span className="is-processing">进行中 {summary.running}</span>
        <span className="is-error">失败 {summary.failed}</span>
      </div>
      <Table<DramaSyncRecord>
        rowKey="id"
        size="small"
        loading={loading}
        columns={recordColumns(openDetails)}
        dataSource={records}
        pagination={false}
        scroll={{ x: 900 }}
        locale={{
          emptyText: `暂无${domain === 'catalog' ? '短剧' : '剧集'}同步记录`,
        }}
      />

      <Drawer
        title="同步详情"
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        size={760}
      >
        {selectedRecord ? (
          <Descriptions column={2} size="small" bordered>
            <Descriptions.Item label="创建时间">
              {formatDate(selectedRecord.createdAt)}
            </Descriptions.Item>
            <Descriptions.Item label="任务类型">
              {taskTypeLabels[selectedRecord.taskType]}
            </Descriptions.Item>
            <Descriptions.Item label="触发方式">
              {triggerLabels[selectedRecord.triggerSource]}
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              <StatusTag status={selectedRecord.status} />
            </Descriptions.Item>
          </Descriptions>
        ) : null}
        {domain === 'catalog' ? (
          <Table<DramaSyncRecordDetail>
            className="drama-sync-center-page__detail-table"
            rowKey="taskId"
            size="small"
            loading={detailLoading}
            pagination={false}
            dataSource={catalogDetails}
            columns={catalogDetailColumns(retryCatalog, languageOptions)}
            locale={{
              emptyText: (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="暂无子任务详情"
                />
              ),
            }}
            scroll={{ x: 620 }}
          />
        ) : (
          <Table<DramaContentSyncRecordDetail>
            className="drama-sync-center-page__detail-table"
            rowKey="taskId"
            size="small"
            loading={detailLoading}
            pagination={false}
            dataSource={contentDetails}
            columns={contentDetailColumns(retryContent, languageOptions)}
            locale={{
              emptyText: (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="暂无子任务详情"
                />
              ),
            }}
            scroll={{ x: 620 }}
          />
        )}
      </Drawer>

      <DramaSyncModal
        open={catalogModalOpen}
        providers={catalogProviders}
        preferredProviderId={providerId}
        languageOptions={languageOptions}
        onClose={() => setCatalogModalOpen(false)}
        onSubmitted={() => {
          setCatalogModalOpen(false)
          void loadRecords()
        }}
      />
      <DramaContentSyncModal
        open={contentModalOpen}
        providers={contentProviders}
        preferredProviderId={providerId}
        languageOptions={languageOptions}
        onClose={() => setContentModalOpen(false)}
        onSubmitted={() => {
          setContentModalOpen(false)
          void loadRecords()
        }}
      />
    </section>
  )
}

function recordColumns(
  onDetails: (record: DramaSyncRecord) => void,
): ColumnsType<DramaSyncRecord> {
  return [
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 150,
      render: (value: string) => formatDate(value),
    },
    {
      title: '触发方式',
      dataIndex: 'triggerSource',
      width: 100,
      render: (value: SyncTriggerSource) => triggerLabels[value],
    },
    {
      title: '任务类型',
      dataIndex: 'taskType',
      width: 130,
      render: (value: DramaSyncTaskType) => taskTypeLabels[value],
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (value: SyncRecordStatus) => <StatusTag status={value} />,
    },
    { title: '新增数', dataIndex: 'insertedCount', width: 90 },
    { title: '更新数', dataIndex: 'updatedCount', width: 90 },
    { title: '总处理数', dataIndex: 'totalProcessed', width: 100 },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => onDetails(record)}>
          查看详情
        </Button>
      ),
    },
  ]
}

function catalogDetailColumns(
  onRetry: (detail: DramaSyncRecordDetail) => Promise<void>,
  languageOptions: { value: string; label: string }[],
): ColumnsType<DramaSyncRecordDetail> {
  return [
    {
      title: '语言',
      dataIndex: 'language',
      render: (value: string | null) =>
        value
          ? (languageOptions.find((option) => option.value === value)?.label ??
            value)
          : '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: (value: string) => (
        <Tag
          color={
            value === 'SUCCESS'
              ? 'success'
              : value === 'FAILED'
                ? 'error'
                : 'processing'
          }
        >
          {value}
        </Tag>
      ),
    },
    { title: '新增数', dataIndex: 'insertedCount' },
    { title: '更新数', dataIndex: 'updatedCount' },
    { title: '总处理数', dataIndex: 'totalProcessed' },
    {
      title: '错误',
      dataIndex: 'lastErrorMessage',
      render: (value: string | null) => value || '-',
    },
    {
      title: '操作',
      key: 'actions',
      render: (_, detail) =>
        detail.status === 'FAILED' ? (
          <Button type="link" size="small" onClick={() => void onRetry(detail)}>
            重试
          </Button>
        ) : null,
    },
  ]
}

function contentDetailColumns(
  onRetry: (detail: DramaContentSyncRecordDetail) => Promise<void>,
  languageOptions: { value: string; label: string }[],
): ColumnsType<DramaContentSyncRecordDetail> {
  return [
    {
      title: '短剧',
      dataIndex: 'dramaTitle',
      render: (value: string | null) => value || '-',
    },
    {
      title: '语言',
      dataIndex: 'language',
      render: (value: string | null) =>
        value
          ? (languageOptions.find((option) => option.value === value)?.label ??
            value)
          : '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      render: (value: string) => (
        <Tag
          color={
            value === 'SUCCESS'
              ? 'success'
              : value === 'FAILED'
                ? 'error'
                : 'processing'
          }
        >
          {value}
        </Tag>
      ),
    },
    { title: '新增数', dataIndex: 'insertedCount' },
    { title: '更新数', dataIndex: 'updatedCount' },
    { title: '总处理数', dataIndex: 'totalProcessed' },
    {
      title: '操作',
      key: 'actions',
      render: (_, detail) =>
        detail.status === 'FAILED' ? (
          <Button type="link" size="small" onClick={() => void onRetry(detail)}>
            重试
          </Button>
        ) : null,
    },
  ]
}

function StatusTag({ status }: { status: SyncRecordStatus }) {
  const color =
    status === 'SUCCESS'
      ? 'success'
      : status === 'FAILED' || status === 'PARTIAL_FAILED'
        ? 'error'
        : status === 'RUNNING'
          ? 'processing'
          : 'default'
  return <Tag color={color}>{statusLabels[status]}</Tag>
}

function formatDate(value: string | null | undefined) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}
