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
  Tag,
} from 'antd'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import { Download, RefreshCw, Search } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { listProviders } from '../../features/provider/providerApi'
import type { DramaProvider } from '../../features/provider/providerTypes'
import {
  exportPromotionOrders,
  listPromotionOrders,
  syncPromotionOrders,
} from '../../features/promotion/promotionOrderApi'
import type {
  PromotionAttributionStatus,
  PromotionOrder,
  PromotionOrderQuery,
  PromotionOrderStatus,
} from '../../features/promotion/promotionOrderTypes'
import './promotion-order-page.css'

interface FilterValues {
  providerId?: number
  status?: PromotionOrderStatus
  attributionStatus?: PromotionAttributionStatus
  paidStart?: string
  paidEnd?: string
}

interface SyncValues {
  providerId: number
  startDate: string
  endDate: string
}

const orderStatusLabels: Record<PromotionOrderStatus, string> = {
  UNPAID: '未支付',
  PAID: '已支付',
  REFUNDED: '已退款',
  UNKNOWN: '未知',
}

const attributionLabels: Record<PromotionAttributionStatus, string> = {
  ATTRIBUTED: '已归因',
  UNATTRIBUTED: '未归因',
}

export function PromotionOrderPage() {
  const { message } = AntdApp.useApp()
  const [filterForm] = Form.useForm<FilterValues>()
  const [syncForm] = Form.useForm<SyncValues>()
  const [providers, setProviders] = useState<DramaProvider[]>([])
  const [orders, setOrders] = useState<PromotionOrder[]>([])
  const [query, setQuery] = useState<PromotionOrderQuery>({ page: 1, size: 20 })
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [syncOpen, setSyncOpen] = useState(false)
  const [syncing, setSyncing] = useState(false)

  const providerNames = useMemo(
    () =>
      new Map(
        providers.map((provider) => [provider.id, provider.providerName]),
      ),
    [providers],
  )

  const loadOrders = useCallback(
    async (nextQuery: PromotionOrderQuery) => {
      setLoading(true)
      try {
        const result = await listPromotionOrders(nextQuery)
        setOrders(result.list)
        setTotal(result.total)
      } catch (error) {
        message.error(error instanceof Error ? error.message : '订单加载失败')
      } finally {
        setLoading(false)
      }
    },
    [message],
  )

  useEffect(() => {
    void listProviders()
      .then((items) =>
        setProviders(
          items.filter((item) => item.capabilities.includes('ORDER_SYNC')),
        ),
      )
      .catch((error) =>
        message.error(error instanceof Error ? error.message : '平台加载失败'),
      )
  }, [message])

  useEffect(() => {
    void loadOrders(query)
  }, [loadOrders, query])

  const applyFilters = (values: FilterValues) => {
    setQuery({
      page: 1,
      size: query.size,
      providerId: values.providerId,
      status: values.status,
      attributionStatus: values.attributionStatus,
      startDate: values.paidStart?.replace(' ', 'T'),
      endDate: values.paidEnd?.replace(' ', 'T'),
    })
  }

  const handleTableChange = (pagination: TablePaginationConfig) => {
    setQuery((current) => ({
      ...current,
      page: pagination.current ?? 1,
      size: pagination.pageSize ?? 20,
    }))
  }

  const openSync = () => {
    if (providers.length === 1) {
      syncForm.setFieldValue('providerId', providers[0].id)
    }
    setSyncOpen(true)
  }

  const handleSync = async () => {
    try {
      const values = await syncForm.validateFields()
      setSyncing(true)
      const result = await syncPromotionOrders({
        providerId: values.providerId,
        startDate: values.startDate.replace(' ', 'T'),
        endDate: values.endDate.replace(' ', 'T'),
      })
      message.success(
        `同步完成：获取 ${result.fetchedCount} 条，新增 ${result.insertedCount} 条，更新 ${result.updatedCount} 条，未归因 ${result.unattributedCount} 条`,
      )
      setSyncOpen(false)
      syncForm.resetFields()
      await loadOrders(query)
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return
      message.error(error instanceof Error ? error.message : '订单同步失败')
    } finally {
      setSyncing(false)
    }
  }

  const handleExport = async () => {
    try {
      const blob = await exportPromotionOrders(query)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = 'promotion-orders.csv'
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (error) {
      message.error(error instanceof Error ? error.message : '订单导出失败')
    }
  }

  const columns: ColumnsType<PromotionOrder> = [
    {
      title: '订单 ID',
      dataIndex: 'externalOrderId',
      fixed: 'left',
      width: 170,
    },
    {
      title: '短剧平台',
      dataIndex: 'providerId',
      width: 120,
      render: (value: number) => providerNames.get(value) ?? `#${value}`,
    },
    {
      title: '订单金额',
      dataIndex: 'orderAmount',
      width: 120,
      render: (value: number, record) => formatMoney(value, record.currency),
    },
    {
      title: '订单状态',
      dataIndex: 'status',
      width: 110,
      render: (value: PromotionOrderStatus) => (
        <Tag
          color={
            value === 'REFUNDED'
              ? 'error'
              : value === 'PAID'
                ? 'success'
                : 'default'
          }
        >
          {orderStatusLabels[value]}
        </Tag>
      ),
    },
    { title: '支付时间', dataIndex: 'paidAt', width: 170, render: formatDate },
    { title: '追踪号', dataIndex: 'trackingNo', width: 180, render: emptyText },
    {
      title: '归因状态',
      dataIndex: 'attributionStatus',
      width: 110,
      render: (value: PromotionAttributionStatus) => (
        <Tag color={value === 'ATTRIBUTED' ? 'blue' : 'warning'}>
          {attributionLabels[value]}
        </Tag>
      ),
    },
    {
      title: '佣金',
      dataIndex: 'commissionAmount',
      width: 120,
      render: (value: number | null, record) =>
        value === null ? '-' : formatMoney(value, record.currency),
    },
    { title: '佣金状态', dataIndex: 'commissionStatus', width: 130 },
    {
      title: '最后同步',
      dataIndex: 'lastSyncedAt',
      width: 170,
      render: formatDate,
    },
  ]

  return (
    <PageContainer
      title="推广订单"
      content="手动同步 GoodShort 订单并核对归因与 CPS 佣金。"
    >
      <div className="promotion-order-page__toolbar">
        <Form
          name="promotionOrderFilter"
          form={filterForm}
          layout="inline"
          onFinish={applyFilters}
        >
          <Form.Item name="providerId" label="短剧平台">
            <Select
              allowClear
              placeholder="全部平台"
              options={providerOptions(providers)}
            />
          </Form.Item>
          <Form.Item name="status" label="订单状态">
            <Select
              allowClear
              placeholder="全部状态"
              options={labelOptions(orderStatusLabels)}
            />
          </Form.Item>
          <Form.Item name="attributionStatus" label="归因状态">
            <Select
              allowClear
              placeholder="全部归因"
              options={labelOptions(attributionLabels)}
            />
          </Form.Item>
          <Form.Item name="paidStart" label="支付开始时间">
            <Input
              aria-label="支付开始时间"
              placeholder="YYYY-MM-DD HH:mm:ss"
            />
          </Form.Item>
          <Form.Item name="paidEnd" label="支付结束时间">
            <Input
              aria-label="支付结束时间"
              placeholder="YYYY-MM-DD HH:mm:ss"
            />
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
        <Space>
          <Button
            icon={<Download size={16} />}
            onClick={() => void handleExport()}
          >
            导出 CSV
          </Button>
          <Button
            type="primary"
            icon={<RefreshCw size={16} />}
            onClick={openSync}
          >
            手动同步
          </Button>
        </Space>
      </div>

      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={orders}
        scroll={{ x: 1_450 }}
        pagination={{
          current: query.page,
          pageSize: query.size,
          total,
          showSizeChanger: true,
        }}
        onChange={handleTableChange}
      />

      <Modal
        title="手动同步订单"
        open={syncOpen}
        okText="开始同步"
        cancelText="取消"
        confirmLoading={syncing}
        onOk={() => void handleSync()}
        onCancel={() => setSyncOpen(false)}
      >
        <Form
          name="promotionOrderSync"
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
              options={providerOptions(providers)}
            />
          </Form.Item>
          <Form.Item
            name="startDate"
            label="同步开始时间"
            rules={[{ required: true, message: '请选择开始时间' }]}
          >
            <Input
              aria-label="同步开始时间"
              placeholder="YYYY-MM-DD HH:mm:ss"
            />
          </Form.Item>
          <Form.Item
            name="endDate"
            label="同步结束时间"
            rules={[{ required: true, message: '请选择结束时间' }]}
          >
            <Input
              aria-label="同步结束时间"
              placeholder="YYYY-MM-DD HH:mm:ss"
            />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  )
}

function providerOptions(providers: DramaProvider[]) {
  return providers.map((provider) => ({
    value: provider.id,
    label: provider.providerName,
  }))
}

function labelOptions<T extends string>(labels: Record<T, string>) {
  return Object.entries(labels).map(([value, label]) => ({ value, label }))
}

function formatMoney(value: number, currency: string) {
  const symbol = currency === 'USD' ? '$' : `${currency} `
  return `${symbol}${Number(value).toFixed(2)}`
}

function formatDate(value: string | null | undefined) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function emptyText(value: string | null | undefined) {
  return value || '-'
}
