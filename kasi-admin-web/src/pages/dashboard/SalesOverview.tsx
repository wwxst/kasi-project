import Column from '@ant-design/plots/es/components/column'
import { Button, Card, Col, DatePicker, Row, Tabs } from 'antd'
import dayjs from 'dayjs'
import { rankingData, salesData } from './dashboardData'

const { RangePicker } = DatePicker

export function SalesOverview() {
  const chart = (
    <Row>
      <Col xs={24} lg={16}>
        <div className="analysis-sales-chart">
          <Column
            height={300}
            data={salesData}
            xField="x"
            yField="y"
            scale={{ x: { paddingInner: 0.4 } }}
            axis={{ x: { title: false }, y: { title: false } }}
            tooltip={{ name: '销售量', channel: 'y' }}
          />
        </div>
      </Col>
      <Col xs={24} lg={8}>
        <RankingList />
      </Col>
    </Row>
  )

  return (
    <Card
      className="analysis-sales-card"
      data-testid="analysis-sales-card"
      style={{ marginTop: 24 }}
      styles={{ body: { padding: 0 } }}
    >
      <Tabs
        size="large"
        items={[
          { key: 'sales', label: '销售额', children: chart },
          { key: 'visits', label: '访问量', children: chart },
        ]}
        tabBarExtraContent={
          <div className="analysis-sales-extra">
            <div className="analysis-sales-periods">
              <Button type="text">今日</Button>
              <Button type="text">本周</Button>
              <Button type="text">本月</Button>
              <Button type="text" className="is-active">
                本年
              </Button>
            </div>
            <RangePicker
              defaultValue={[dayjs().startOf('year'), dayjs().endOf('year')]}
              variant="filled"
            />
          </div>
        }
      />
    </Card>
  )
}

function RankingList() {
  return (
    <section className="analysis-ranking">
      <h2>门店销售额排名</h2>
      <ol>
        {rankingData.map((item, index) => (
          <li key={item.title}>
            <span className={index < 3 ? 'is-leading' : undefined}>
              {index + 1}
            </span>
            <span title={item.title}>{item.title}</span>
            <strong>{item.total.toLocaleString('zh-CN')}</strong>
          </li>
        ))}
      </ol>
    </section>
  )
}
