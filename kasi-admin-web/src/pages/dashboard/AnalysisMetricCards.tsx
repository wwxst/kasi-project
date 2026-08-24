import Area from '@ant-design/plots/es/components/area'
import Column from '@ant-design/plots/es/components/column'
import { Card, Col, Progress, Row, Tooltip } from 'antd'
import { ArrowDown, ArrowUp, CircleHelp } from 'lucide-react'
import { visitData } from './dashboardData'

export function AnalysisMetricCards() {
  return (
    <Row className="analysis-metrics" gutter={[24, 24]}>
      <Col xs={24} sm={12} xl={6}>
        <MetricCard
          title="总销售额"
          value="¥ 126,560"
          footer="日销售额 ¥12,423"
        >
          <Trend direction="up">周同比 12%</Trend>
          <Trend direction="down">日同比 11%</Trend>
        </MetricCard>
      </Col>
      <Col xs={24} sm={12} xl={6}>
        <MetricCard title="访问量" value="8,846" footer="日访问量 1,234">
          <Area
            height={46}
            data={visitData}
            xField="x"
            yField="y"
            shapeField="smooth"
            axis={false}
            padding={-16}
            style={{ fill: '#975fe4', fillOpacity: 0.35 }}
          />
        </MetricCard>
      </Col>
      <Col xs={24} sm={12} xl={6}>
        <MetricCard title="支付笔数" value="6,560" footer="转化率 60%">
          <Column
            height={46}
            data={visitData}
            xField="x"
            yField="y"
            axis={false}
            padding={-16}
            scale={{ x: { paddingInner: 0.45 } }}
          />
        </MetricCard>
      </Col>
      <Col xs={24} sm={12} xl={6}>
        <MetricCard
          title="运营活动效果"
          value="78%"
          footer="周同比 12%　日同比 11%"
        >
          <Progress percent={78} showInfo={false} strokeColor="#1677ff" />
        </MetricCard>
      </Col>
    </Row>
  )
}

function MetricCard({
  title,
  value,
  footer,
  children,
}: {
  title: string
  value: string
  footer: string
  children: React.ReactNode
}) {
  return (
    <Card
      className="analysis-metric-card"
      title={title}
      extra={
        <Tooltip title="指标说明">
          <CircleHelp size={16} />
        </Tooltip>
      }
    >
      <strong className="analysis-metric-card__value">{value}</strong>
      <div className="analysis-metric-card__chart">{children}</div>
      <div className="analysis-metric-card__footer">{footer}</div>
    </Card>
  )
}

function Trend({
  direction,
  children,
}: {
  direction: 'up' | 'down'
  children: React.ReactNode
}) {
  return (
    <span className="analysis-trend">
      {children}
      {direction === 'up' ? (
        <ArrowUp className="analysis-trend--up" size={13} />
      ) : (
        <ArrowDown className="analysis-trend--down" size={13} />
      )}
    </span>
  )
}
