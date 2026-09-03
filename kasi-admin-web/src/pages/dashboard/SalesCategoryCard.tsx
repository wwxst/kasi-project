import Pie from '@ant-design/plots/es/components/pie'
import { Card, Segmented } from 'antd'
import { Ellipsis } from 'lucide-react'
import { useState } from 'react'
import { salesCategories } from './dashboardData'

type SalesType = 'all' | 'online' | 'stores'

export function SalesCategoryCard() {
  const [salesType, setSalesType] = useState<SalesType>('all')

  return (
    <Card
      className="analysis-detail-card analysis-category-card"
      title="销售额类别占比"
      extra={<Ellipsis size={18} aria-label="更多销售额操作" />}
    >
      <Segmented<SalesType>
        value={salesType}
        onChange={setSalesType}
        options={[
          { label: '全部渠道', value: 'all' },
          { label: '线上', value: 'online' },
          { label: '门店', value: 'stores' },
        ]}
      />
      <span className="analysis-category-card__label">销售额</span>
      <Pie
        height={340}
        data={salesCategories[salesType]}
        angleField="value"
        colorField="type"
        innerRadius={0.55}
        radius={0.82}
        legend={false}
        label={{
          position: 'spider',
          text: (item: { type: string; value: number }) =>
            `${item.type}: ${item.value.toLocaleString('zh-CN')}`,
        }}
      />
    </Card>
  )
}
