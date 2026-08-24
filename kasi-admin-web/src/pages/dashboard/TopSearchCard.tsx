import Area from '@ant-design/plots/es/components/area'
import { Card, Col, Row, Table, Tooltip } from 'antd'
import type { TableColumnsType } from 'antd'
import { ArrowDown, ArrowUp, CircleHelp, Ellipsis } from 'lucide-react'
import { searchData, visitData } from './dashboardData'
import type { SearchDatum } from './dashboardData'

const columns: TableColumnsType<SearchDatum> = [
  { title: '排名', dataIndex: 'index', width: 64 },
  { title: '搜索关键词', dataIndex: 'keyword' },
  { title: '用户数', dataIndex: 'count', sorter: (a, b) => a.count - b.count },
  {
    title: '周涨幅',
    dataIndex: 'range',
    sorter: (a, b) => a.range - b.range,
    render: (value: number, record) => (
      <span className="analysis-search-trend">
        {value}%
        {record.status === 'up' ? (
          <ArrowUp className="analysis-trend--up" size={13} />
        ) : (
          <ArrowDown className="analysis-trend--down" size={13} />
        )}
      </span>
    ),
  },
]

export function TopSearchCard() {
  return (
    <Card
      className="analysis-detail-card"
      title="线上热门搜索"
      extra={<Ellipsis size={18} aria-label="更多热门搜索操作" />}
    >
      <Row gutter={48}>
        <Col xs={24} sm={12}>
          <SearchStat
            label="搜索用户数"
            value="12,321"
            change="17.1"
            direction="up"
          />
        </Col>
        <Col xs={24} sm={12}>
          <SearchStat
            label="人均搜索次数"
            value="2.7"
            change="26.2"
            direction="down"
          />
        </Col>
      </Row>
      <Table<SearchDatum>
        className="analysis-search-table"
        rowKey="index"
        size="small"
        columns={columns}
        dataSource={searchData}
        pagination={{ pageSize: 5, showSizeChanger: false }}
        scroll={{ x: 480 }}
      />
    </Card>
  )
}

function SearchStat({
  label,
  value,
  change,
  direction,
}: {
  label: string
  value: string
  change: string
  direction: 'up' | 'down'
}) {
  return (
    <div className="analysis-search-stat">
      <div className="analysis-search-stat__label">
        {label}
        <Tooltip title="指标说明">
          <CircleHelp size={14} />
        </Tooltip>
      </div>
      <div className="analysis-search-stat__value">
        <strong>{value}</strong>
        <span>
          {change}
          {direction === 'up' ? (
            <ArrowUp className="analysis-trend--up" size={13} />
          ) : (
            <ArrowDown className="analysis-trend--down" size={13} />
          )}
        </span>
      </div>
      <Area
        height={45}
        data={visitData.slice(0, 7)}
        xField="x"
        yField="y"
        shapeField="smooth"
        axis={false}
        padding={-12}
        style={{ fill: '#6294fa', fillOpacity: 0.28 }}
      />
    </div>
  )
}
