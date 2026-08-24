import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button, Form, Input, Select, Table, Tag } from 'tdesign-react'
import { SearchIcon } from 'tdesign-icons-react'
import { fetchPromotionTasks } from '../../features/promotion/api/promotionTaskApi'
import type {
  PromotionMediaType,
  PromotionTask,
} from '../../features/promotion/api/promotionTaskTypes'

const mediaOptions = [
  { label: 'TikTok', value: 'TIKTOK' },
  { label: 'Facebook', value: 'FACEBOOK' },
  { label: 'YouTube', value: 'YOUTUBE' },
  { label: 'Instagram', value: 'INSTAGRAM' },
]

function statusView(status: PromotionTask['status']) {
  return status === 'SUCCESS'
    ? { label: '已完成', theme: 'success' as const }
    : status === 'FAILED'
      ? { label: '失败', theme: 'danger' as const }
      : { label: '处理中', theme: 'warning' as const }
}

export function PromotionTaskPage() {
  const [filters, setFilters] = useState({
    taskName: '',
    dramaTitle: '',
    mediaType: '',
  })
  const [applied, setApplied] = useState(filters)
  const query = useQuery({
    queryKey: ['promotion-tasks', applied],
    queryFn: () => fetchPromotionTasks(applied),
    staleTime: 15_000,
  })
  const tasks = query.data?.list ?? []
  return (
    <section
      className="promotion-task-page"
      aria-labelledby="promotion-task-title"
    >
      <div className="promotion-task-breadcrumb">
        推广管理 / <span>推广任务</span>
      </div>
      <h1 id="promotion-task-title">推广任务</h1>
      <div className="promotion-task-filter-shell">
        <Form layout="inline">
          <Form.FormItem label="短剧名称">
            <Input
              value={filters.dramaTitle}
              placeholder="请输入短剧名称"
              onChange={(v) => setFilters({ ...filters, dramaTitle: v })}
            />
          </Form.FormItem>
          <Form.FormItem label="推广平台">
            <Select
              value={filters.mediaType}
              placeholder="请选择"
              options={mediaOptions}
              onChange={(v) => setFilters({ ...filters, mediaType: String(v) })}
            />
          </Form.FormItem>
          <Form.FormItem label="推广任务名称">
            <Input
              value={filters.taskName}
              placeholder="请输入任务名称"
              onChange={(v) => setFilters({ ...filters, taskName: v })}
            />
          </Form.FormItem>
          <Button
            theme="primary"
            icon={<SearchIcon />}
            onClick={() => setApplied(filters)}
          >
            查询
          </Button>
          <Button
            variant="outline"
            onClick={() => {
              setFilters({ taskName: '', dramaTitle: '', mediaType: '' })
              setApplied({ taskName: '', dramaTitle: '', mediaType: '' })
            }}
          >
            重置
          </Button>
        </Form>
      </div>
      <Table
        rowKey="id"
        data={tasks}
        loading={query.isLoading}
        bordered
        hover
        tableLayout="fixed"
        columns={[
          { colKey: 'createdAt', title: '创建时间', width: 160 },
          { colKey: 'taskName', title: '推广任务名称', width: 180 },
          { colKey: 'mediaType', title: '推广平台', width: 110 },
          { colKey: 'providerName', title: '剧场', width: 110 },
          { colKey: 'dramaTitle', title: '短剧名称', width: 220 },
          {
            colKey: 'externalCode',
            title: '推广口令',
            width: 150,
            cell: ({ row }) => row.externalCode || '-',
          },
          {
            colKey: 'directUrl',
            title: '直达链接',
            width: 240,
            cell: ({ row }) =>
              row.directUrl ? (
                <a href={row.directUrl} target="_blank" rel="noreferrer">
                  {row.directUrl}
                </a>
              ) : (
                '-'
              ),
          },
          {
            colKey: 'status',
            title: '状态',
            width: 100,
            cell: ({ row }) => (
              <Tag theme={statusView(row.status).theme}>
                {statusView(row.status).label}
              </Tag>
            ),
          },
          { colKey: 'codeSearchCount', title: '口令搜索数', width: 110 },
          { colKey: 'directClickCount', title: '直达链接点击数', width: 130 },
          { colKey: 'appClickCount', title: 'App点击数', width: 110 },
          { colKey: 'leadCount', title: '引流人数', width: 100 },
          {
            colKey: 'orderAmount',
            title: '订单金额',
            width: 110,
            cell: ({ row }) => `¥${row.orderAmount}`,
          },
          { colKey: 'orderCount', title: '订单数', width: 90 },
          {
            colKey: 'adAmount',
            title: '广告金额',
            width: 110,
            cell: ({ row }) => `¥${row.adAmount}`,
          },
        ]}
        empty={<div className="promotion-task-empty">暂无推广任务</div>}
      />
    </section>
  )
}

export type { PromotionMediaType }
