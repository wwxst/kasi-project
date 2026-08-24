import Line from '@ant-design/plots/es/components/line'
import { Card, Progress, Tabs } from 'antd'
import { storeTrendData, stores } from './dashboardData'

export function StoreOverview() {
  return (
    <Card className="analysis-store-card" styles={{ body: { padding: 0 } }}>
      <Tabs
        items={stores.map((store) => ({
          key: store.name,
          label: (
            <div className="analysis-store-tab">
              <div>
                <strong>{store.name}</strong>
                <span>转化率</span>
                <b>{store.conversion}%</b>
              </div>
              <Progress
                type="circle"
                size={48}
                percent={store.conversion}
                showInfo={false}
              />
            </div>
          ),
          children: (
            <div className="analysis-store-chart">
              <Line
                height={360}
                data={storeTrendData}
                xField="time"
                yField="value"
                colorField="type"
                axis={{ x: { title: false }, y: { title: false } }}
                legend={{ color: { layout: { justifyContent: 'center' } } }}
              />
            </div>
          ),
        }))}
      />
    </Card>
  )
}
