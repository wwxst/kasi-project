import { PageContainer } from '@ant-design/pro-components'
import {
  App as AntdApp,
  Button,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
} from 'antd'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import { RefreshCw, Search } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { listProviders } from '../../features/provider/providerApi'
import type { DramaProvider } from '../../features/provider/providerTypes'
import {
  listPromotionLinks,
  syncPromotionAnalyticalReports,
} from '../../features/promotion/promotionLinkApi'
import type {
  AdminPromotionLink,
  PromotionLinkQuery,
} from '../../features/promotion/promotionLinkTypes'
import './promotion-link-page.css'

interface FilterValues {
  userNo?: string
  providerId?: number
  externalCode?: string
  trackingNo?: string
}

interface SyncValues {
  providerId: number
  startDate: string
  endDate: string
}

export function PromotionLinkPage() {
  const { message } = AntdApp.useApp()
  const [filterForm] = Form.useForm<FilterValues>()
  const [syncForm] = Form.useForm<SyncValues>()
  const [providers, setProviders] = useState<DramaProvider[]>([])
  const [links, setLinks] = useState<AdminPromotionLink[]>([])
  const [query, setQuery] = useState<PromotionLinkQuery>({ page: 1, size: 20 })
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [syncOpen, setSyncOpen] = useState(false)
  const [syncing, setSyncing] = useState(false)

  const analyticsProviders = useMemo(
    () =>
      providers.filter((provider) =>
        provider.capabilities.includes('ANALYTICS_SYNC'),
      ),
    [providers],
  )

  const providerNames = useMemo(
    () =>
      new Map(
        providers.map((provider) => [provider.id, provider.providerName]),
      ),
    [providers],
  )

  const loadLinks = useCallback(
    async (nextQuery: PromotionLinkQuery) => {
      setLoading(true)
      try {
        const result = await listPromotionLinks(nextQuery)
        setLinks(result.list)
        setTotal(result.total)
      } catch (error) {
        message.error(
          error instanceof Error ? error.message : '推广任务加载失败',
        )
      } finally {
        setLoading(false)
      }
    },
    [message],
  )

  useEffect(() => {
    void listProviders()
      .then(setProviders)
      .catch((error) =>
        message.error(error instanceof Error ? error.message : '平台加载失败'),
      )
  }, [message])

  useEffect(() => {
    void loadLinks(query)
  }, [loadLinks, query])

  const applyFilters = (values: FilterValues) => {
    setQuery({
      page: 1,
      size: query.size,
      userNo: trimOrUndefined(values.userNo),
      providerId: values.providerId,
      externalCode: trimOrUndefined(values.externalCode),
      trackingNo: trimOrUndefined(values.trackingNo),
    })
  }

  const columns: ColumnsType<AdminPromotionLink> = [
    { title: '用户编号', dataIndex: 'userNo', fixed: 'left', width: 150 },
    {
      title: '用户',
      key: 'user',
      width: 120,
      render: (_, record) => record.nickname || record.realName || '-',
    },
    {
      title: '短剧平台',
      dataIndex: 'providerId',
      width: 120,
      render: (value: number, record) =>
        record.providerName || providerNames.get(value) || `#${value}`,
    },
    { title: '短剧', dataIndex: 'dramaTitle', width: 180, render: emptyText },
    {
      title: '推广名称',
      dataIndex: 'campaignName',
      width: 160,
      render: emptyText,
    },
    { title: '媒体', dataIndex: 'mediaType', width: 110 },
    { title: '口令', dataIndex: 'externalCode', width: 160 },
    { title: '追踪号', dataIndex: 'trackingNo', width: 170 },
    {
      title: '推广链接',
      dataIndex: 'shareUrl',
      width: 220,
      render: (value: string) => (
        <a href={value} target="_blank" rel="noreferrer">
          {value}
        </a>
      ),
    },
    { title: '点击数', dataIndex: 'clickCount', width: 90 },
    { title: '归因用户数', dataIndex: 'attributedUserCount', width: 110 },
    { title: '新注册人数', dataIndex: 'newRegisteredUserCount', width: 110 },
    { title: '新充值人数', dataIndex: 'newPaidUserCount', width: 110 },
    { title: '新会员人数', dataIndex: 'newMemberUserCount', width: 110 },
    { title: '充值用户数', dataIndex: 'paidUserCount', width: 110 },
    { title: '订单数', dataIndex: 'orderCount', width: 90 },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 170,
      render: formatDate,
    },
  ]

  const handleTableChange = (pagination: TablePaginationConfig) => {
    setQuery((current) => ({
      ...current,
      page: pagination.current ?? 1,
      size: pagination.pageSize ?? 20,
    }))
  }

  const openSync = () => {
    if (analyticsProviders.length === 1) {
      syncForm.setFieldValue('providerId', analyticsProviders[0].id)
    }
    setSyncOpen(true)
  }

  const handleSync = async () => {
    try {
      const values = await syncForm.validateFields()
      setSyncing(true)
      const result = await syncPromotionAnalyticalReports(values)
      message.success(
        `同步完成：获取 ${result.fetchedCount} 条，写入 ${result.upsertedCount} 条`,
      )
      setSyncOpen(false)
      syncForm.resetFields()
      await loadLinks(query)
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return
      message.error(error instanceof Error ? error.message : '转化日报同步失败')
    } finally {
      setSyncing(false)
    }
  }

  return (
    <PageContainer
      title="推广任务"
      content="按推广链接查看用户、短剧、口令及累计转化数据，并可手动补拉转化日报。"
    >
      <div className="promotion-link-page__toolbar">
        <Form
          name="promotionLinkFilter"
          form={filterForm}
          layout="inline"
          onFinish={applyFilters}
        >
          <Form.Item name="userNo" label="用户编号">
            <Input aria-label="用户编号" placeholder="请输入用户编号" />
          </Form.Item>
          <Form.Item name="providerId" label="短剧平台">
            <Select
              allowClear
              placeholder="全部平台"
              options={providers.map((provider) => ({
                value: provider.id,
                label: provider.providerName,
              }))}
            />
          </Form.Item>
          <Form.Item name="externalCode" label="口令">
            <Input aria-label="口令" placeholder="请输入口令" />
          </Form.Item>
          <Form.Item name="trackingNo" label="追踪号">
            <Input aria-label="追踪号" placeholder="请输入追踪号" />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button
                htmlType="submit"
                type="primary"
                icon={<Search size={16} />}
              >
                查询
              </Button>
              <Button
                onClick={() => {
                  filterForm.resetFields()
                  setQuery({ page: 1, size: query.size })
                }}
              >
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>
        <Button
          type="primary"
          icon={<RefreshCw size={16} />}
          onClick={openSync}
        >
          手动同步转化
        </Button>
      </div>

      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={links}
        scroll={{ x: 2_200 }}
        pagination={{
          current: query.page,
          pageSize: query.size,
          total,
          showSizeChanger: true,
        }}
        onChange={handleTableChange}
      />

      <Modal
        title="手动同步转化日报"
        open={syncOpen}
        okText="开始同步"
        cancelText="取消"
        confirmLoading={syncing}
        onOk={() => void handleSync()}
        onCancel={() => setSyncOpen(false)}
      >
        <Form
          name="promotionAnalyticalReportSync"
          form={syncForm}
          layout="vertical"
          preserve={false}
        >
          <Form.Item
            name="providerId"
            label="短剧平台"
            rules={[{ required: true, message: '请选择短剧平台' }]}
          >
            <Select
              placeholder="请选择平台"
              options={analyticsProviders.map((provider) => ({
                value: provider.id,
                label: provider.providerName,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="startDate"
            label="同步开始日期"
            rules={[{ required: true, message: '请输入开始日期' }]}
          >
            <Input
              type="date"
              aria-label="同步开始日期"
              placeholder="YYYY-MM-DD"
            />
          </Form.Item>
          <Form.Item
            name="endDate"
            label="同步结束日期"
            rules={[{ required: true, message: '请输入结束日期' }]}
          >
            <Input
              type="date"
              aria-label="同步结束日期"
              placeholder="YYYY-MM-DD"
            />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  )
}

function trimOrUndefined(value: string | undefined) {
  return value?.trim() || undefined
}

function emptyText(value: string | null | undefined) {
  return value || '-'
}

function formatDate(value: string | null | undefined) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}
