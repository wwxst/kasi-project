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
import { Clapperboard } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import {
  getDramaCatalogDetail,
  listDramaCatalog,
  updateDramaLocalStatus,
} from '../../features/drama/dramaCatalogApi'
import type {
  DramaCatalogDetail,
  DramaCatalogListItem,
  DramaCatalogPageQuery,
  DramaContent,
  DramaLocalStatus,
} from '../../features/drama/dramaCatalogTypes'
import { listProviders } from '../../features/provider/providerApi'
import type { DramaProvider } from '../../features/provider/providerTypes'
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

  useEffect(() => {
    void listProviders()
      .then((items) => setProviders(items.filter(isCatalogProvider)))
      .catch((error) =>
        message.error(
          error instanceof Error ? error.message : '短剧平台加载失败',
        ),
      )
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

  const openDetail = async (record: DramaCatalogListItem) => {
    setDetailOpen(true)
    setDetailLoading(true)
    try {
      setDetail(await getDramaCatalogDetail(record.id))
    } catch (error) {
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
      message.error(error instanceof Error ? error.message : '短剧状态更新失败')
    } finally {
      setStatusUpdatingId(null)
    }
  }

  const columns: ProColumns<DramaCatalogListItem>[] = [
    {
      title: '封面',
      dataIndex: 'coverUrl',
      search: false,
      width: 76,
      render: (_, record) => (
        <DramaCover coverUrl={record.coverUrl} title={record.title} />
      ),
    },
    {
      title: '短剧名称',
      dataIndex: 'title',
      fieldProps: { placeholder: '请输入短剧名称' },
      width: 220,
      ellipsis: true,
      render: (_, record) => (
        <div className="drama-catalog-page__title-cell">
          <strong>{record.title || '-'}</strong>
          {record.originalTitle && record.originalTitle !== record.title ? (
            <span>{record.originalTitle}</span>
          ) : null}
        </div>
      ),
    },
    {
      title: '外部短剧 ID',
      dataIndex: 'externalDramaId',
      search: false,
      width: 170,
      ellipsis: true,
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
      renderText: (value) => value || '-',
    },
    {
      title: '类型',
      dataIndex: 'dramaType',
      search: false,
      width: 110,
      renderText: (value) => value || '-',
    },
    {
      title: '远端状态',
      dataIndex: 'remoteShowStatus',
      width: 110,
      fieldProps: { placeholder: '请输入原始状态' },
      render: (_, record) => <Tag>{record.remoteShowStatus || '-'}</Tag>,
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
      title: '操作',
      valueType: 'option',
      fixed: 'right',
      width: 140,
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
    const result = await listDramaCatalog(query)
    return { data: result.list, total: result.total, success: true }
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
        toolBarRender={false}
        search={{ labelWidth: 88 }}
        options={{
          density: true,
          fullScreen: true,
          reload: true,
          setting: true,
        }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        scroll={{ x: 1430 }}
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
            {detail ? <DramaDetail detail={detail} /> : null}
          </Spin>
        </div>
      </Drawer>
    </PageContainer>
  )
}

function DramaDetail({ detail }: { detail: DramaCatalogDetail }) {
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
            {detail.language || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="类型">
            {detail.dramaType || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="远端状态">
            {detail.remoteShowStatus || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="远端更新时间">
            {formatDate(detail.remoteUpdatedAt)}
          </Descriptions.Item>
          <Descriptions.Item label="最近同步可见">
            {formatDate(detail.lastSeenAt)}
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

function DramaCover({
  coverUrl,
  title,
  large = false,
}: {
  coverUrl: string | null
  title: string | null
  large?: boolean
}) {
  return (
    <div
      className={
        large
          ? 'drama-catalog-page__cover drama-catalog-page__cover--large'
          : 'drama-catalog-page__cover'
      }
    >
      {coverUrl ? (
        <Image src={coverUrl} alt={title || '短剧封面'} preview={false} />
      ) : (
        <Clapperboard size={large ? 28 : 20} strokeWidth={1.6} />
      )}
    </div>
  )
}

function LocalStatusTag({ status }: { status: DramaLocalStatus }) {
  return (
    <Tag color={localStatusColors[status]}>{localStatusLabels[status]}</Tag>
  )
}

function isCatalogProvider(provider: DramaProvider) {
  return (
    provider.status === 1 &&
    provider.connection?.status === 1 &&
    provider.capabilities.some(
      (capability) =>
        capability === 'FULL_DRAMA_SYNC' ||
        capability === 'INCREMENTAL_DRAMA_SYNC',
    )
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
