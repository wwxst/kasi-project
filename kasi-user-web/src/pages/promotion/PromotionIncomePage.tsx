import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button, DatePicker, Statistic, Table, Tag } from 'tdesign-react'
import { DownloadIcon } from 'tdesign-icons-react'
import {
  downloadPromotionOrders,
  fetchMonthlyCommission,
  fetchPromotionOrders,
} from '../../features/promotion/api/promotionOrderApi'
import './promotion-income.css'

function money(value?: number | null) {
  return `$${Number(value ?? 0).toFixed(2)}`
}

export function PromotionIncomePage() {
  const [month, setMonth] = useState(() => new Date().toISOString().slice(0, 7))
  const summary = useQuery({
    queryKey: ['promotion-income', month],
    queryFn: () => fetchMonthlyCommission(month),
  })
  const orders = useQuery({
    queryKey: ['promotion-orders', month],
    queryFn: () => fetchPromotionOrders(month),
  })
  return (
    <section
      className="promotion-income-page"
      aria-labelledby="promotion-income-title"
    >
      <div className="promotion-link-heading">
        <p className="page-eyebrow">COMMISSION DETAILS</p>
        <h1 id="promotion-income-title">佣金明细</h1>
      </div>
      <div className="promotion-income-toolbar">
        <DatePicker
          mode="month"
          value={month}
          onChange={(value) => setMonth(String(value).slice(0, 7))}
        />
        <Button
          theme="primary"
          icon={<DownloadIcon />}
          onClick={() => void downloadPromotionOrders(month)}
        >
          导出 CSV
        </Button>
      </div>
      <div className="account-overview-grid promotion-income-summary">
        <Statistic
          title="净佣金"
          value={summary.data?.netCommission ?? 0}
          prefix="$"
          decimalPlaces={2}
        />
        <Statistic
          title="已支付订单"
          value={summary.data?.paidOrderCount ?? 0}
        />
        <Statistic
          title="订单金额"
          value={summary.data?.grossOrderAmount ?? 0}
          prefix="$"
          decimalPlaces={2}
        />
        <Statistic
          title="退款冲销"
          value={summary.data?.reversedCommission ?? 0}
          prefix="$"
          decimalPlaces={2}
        />
      </div>
      <div className="promotion-income-table">
        <Table
          rowKey="id"
          loading={orders.isLoading}
          data={orders.data?.list ?? []}
          columns={[
            { colKey: 'externalOrderId', title: '订单ID' },
            {
              colKey: 'paidAt',
              title: '支付时间',
              cell: ({ row }) => row.paidAt?.replace('T', ' ') || '-',
            },
            {
              colKey: 'orderAmount',
              title: '订单金额',
              cell: ({ row }) => money(row.orderAmount),
            },
            {
              colKey: 'commissionAmount',
              title: '佣金',
              cell: ({ row }) => money(row.commissionAmount),
            },
            {
              colKey: 'status',
              title: '状态',
              cell: ({ row }) => (
                <Tag theme={row.status === 'REFUNDED' ? 'danger' : 'success'}>
                  {row.status}
                </Tag>
              ),
            },
          ]}
          empty="本月暂无佣金明细"
        />
      </div>
    </section>
  )
}
