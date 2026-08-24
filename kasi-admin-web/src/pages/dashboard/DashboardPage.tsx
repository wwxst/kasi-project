import { Col, Row } from 'antd'
import { AnalysisMetricCards } from './AnalysisMetricCards'
import { SalesCategoryCard } from './SalesCategoryCard'
import { SalesOverview } from './SalesOverview'
import { StoreOverview } from './StoreOverview'
import { TopSearchCard } from './TopSearchCard'
import './dashboard-page.css'

export function DashboardPage() {
  return (
    <div className="analysis-page">
      <AnalysisMetricCards />
      <SalesOverview />
      <Row className="analysis-details" gutter={[24, 24]}>
        <Col xs={24} xl={12}>
          <TopSearchCard />
        </Col>
        <Col xs={24} xl={12}>
          <SalesCategoryCard />
        </Col>
      </Row>
      <StoreOverview />
    </div>
  )
}
