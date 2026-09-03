import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { DatePicker, MessagePlugin, Table, Tag } from 'tdesign-react'
import type { TableProps } from 'tdesign-react'
import { fetchPromotionOrders } from '../../features/orders/ordersApi'
import type {
  PromotionOrder,
  PromotionOrderStatus,
} from '../../features/orders/types'
import { isHandledRequestError } from '../../shared/api/httpClient'
import Style from './OrdersPage.module.less'

const orderStatusView: Record<
  PromotionOrderStatus,
  { label: string; theme: 'default' | 'success' | 'danger' | 'warning' }
> = {
  UNPAID: { label: '未支付', theme: 'warning' },
  PAID: { label: '已支付', theme: 'success' },
  REFUNDED: { label: '已退款', theme: 'danger' },
}

function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function formatMoney(value: number | null | undefined, currency: string) {
  if (value == null) return '-'
  const prefix = currency === 'USD' ? '$' : `${currency} `
  return `${prefix}${Number(value).toFixed(2)}`
}

function formatTime(value: string | null | undefined) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

export default function OrdersPage({ title: _title }: { title: string }) {
  const [month, setMonth] = useState(currentMonth)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const ordersQuery = useQuery({
    queryKey: ['user', 'promotion-orders', month, page, pageSize],
    queryFn: () => fetchPromotionOrders(month, page, pageSize),
  })

  useEffect(() => {
    if (ordersQuery.isError && !isHandledRequestError(ordersQuery.error)) {
      void MessagePlugin.error('订单加载失败，请稍后重试')
    }
  }, [ordersQuery.error, ordersQuery.isError])

  const columns: TableProps<PromotionOrder>['columns'] = [
    {
      colKey: 'externalOrderId',
      title: '订单 ID',
      width: 190,
    },
    {
      colKey: 'status',
      title: '订单状态',
      width: 110,
      cell: ({ row }) => {
        const view = orderStatusView[row.status]
        return <Tag theme={view.theme}>{view.label}</Tag>
      },
    },
    {
      colKey: 'paidAt',
      title: '支付时间',
      width: 180,
      cell: ({ row }) => formatTime(row.paidAt),
    },
    {
      colKey: 'trackingNo',
      title: '推广跟踪号',
      width: 200,
      cell: ({ row }) => row.trackingNo || '-',
    },
    {
      colKey: 'commissionAmount',
      title: '我的收益',
      width: 110,
      cell: ({ row }) => formatMoney(row.commissionAmount, row.currency),
    },
  ]

  return (
    <section className={Style.page} aria-label="订单列表">
      <div className={Style.toolbar}>
        <label className={Style.monthField}>
          <span>订单月份</span>
          <DatePicker
            mode="month"
            value={month}
            clearable={false}
            onChange={(value) => {
              setMonth(String(value).slice(0, 7))
              setPage(1)
            }}
          />
        </label>
      </div>

      <div className={Style.table}>
        <Table
          rowKey="externalOrderId"
          data={ordersQuery.data?.list ?? []}
          columns={columns}
          loading={ordersQuery.isLoading}
          hover
          tableLayout="fixed"
          empty="暂无订单"
          pagination={{
            current: page,
            pageSize,
            total: ordersQuery.data?.total ?? 0,
            showJumper: true,
            onCurrentChange: setPage,
            onPageSizeChange: (size) => {
              setPageSize(size)
              setPage(1)
            },
          }}
        />
      </div>
    </section>
  )
}
