import { useMemo } from 'react'
import type { PrimaryTableCol } from 'tdesign-react'
import { Card, Col, Row, Table, Tag } from 'tdesign-react'
import {
  FileIcon,
  LinkIcon,
  UserIcon,
  UsergroupIcon,
} from 'tdesign-icons-react'
import { useCurrentUser } from '../../features/account/api/accountApi'
import type { MediaAccount } from '../../features/promotion/api/mediaAccountTypes'
import { useMediaAccounts } from '../../features/promotion/model/mediaAccountQueries'
import {
  formatDateTime,
  formatMediaType,
  getFilingView,
} from '../../features/promotion/model/mediaAccountPresentation'
import './account-dashboard.css'

function formatDate(value: string | null) {
  if (!value) return '暂无记录'
  return value.replace('T', ' ')
}

function MetricIcon({
  kind,
}: {
  kind: 'user' | 'accounts' | 'pending' | 'approved'
}) {
  if (kind === 'user') return <UserIcon />
  if (kind === 'accounts') return <LinkIcon />
  if (kind === 'pending') return <FileIcon />
  return <UsergroupIcon />
}

function TrendHint({ children }: { children: string }) {
  return <span className="dashboard-trend-hint">{children}</span>
}

function EmptyTrendChart() {
  return (
    <div
      className="dashboard-chart"
      role="img"
      aria-label="暂无订单和佣金趋势数据"
    >
      <svg viewBox="0 0 760 220" preserveAspectRatio="none" aria-hidden="true">
        <path d="M0 170 C80 145 120 170 190 126 S300 88 360 132 S480 176 550 108 S650 72 760 92" />
        <path d="M0 188 C90 190 140 156 220 170 S360 122 430 150 S560 166 640 118 S710 136 760 120" />
      </svg>
      <span>订单与佣金数据接入后展示</span>
    </div>
  )
}

export function AccountPage() {
  const { data: currentUser } = useCurrentUser()
  const { data: mediaAccounts = [], isLoading: mediaAccountsLoading } =
    useMediaAccounts()

  const pendingCount = mediaAccounts.filter(
    (account) => getFilingView(account).status === 'PENDING',
  ).length
  const approvedCount = mediaAccounts.filter(
    (account) => getFilingView(account).status === 'APPROVED',
  ).length

  const recentAccounts = useMemo(
    () => mediaAccounts.slice(0, 5),
    [mediaAccounts],
  )

  const accountColumns: PrimaryTableCol<MediaAccount>[] = [
    {
      colKey: 'mediaType',
      title: '媒体平台',
      cell: ({ row }) => formatMediaType(row.mediaType),
      width: 120,
    },
    {
      colKey: 'externalAccountId',
      title: '账号 ID',
      ellipsis: true,
      width: 180,
    },
    {
      colKey: 'filingStatus',
      title: '报白状态',
      width: 110,
      cell: ({ row }) => {
        const filing = getFilingView(row)
        return <Tag theme={filing.theme}>{filing.label}</Tag>
      },
    },
    {
      colKey: 'updatedAt',
      title: '最近更新',
      width: 170,
      cell: ({ row }) => {
        const filing = getFilingView(row).filing
        return formatDateTime(filing?.lastQueriedAt ?? filing?.filingTime)
      },
    },
  ]

  if (!currentUser) return null

  const metrics = [
    {
      title: '推广收入',
      count: '暂无数据',
      desc: '订单接口接入后统计',
      kind: 'user' as const,
      dark: true,
    },
    {
      title: '账号总数',
      count: mediaAccountsLoading ? '加载中' : String(mediaAccounts.length),
      desc: '已绑定媒体账号',
      kind: 'accounts' as const,
    },
    {
      title: '审核中账号',
      count: mediaAccountsLoading ? '加载中' : String(pendingCount),
      desc: '等待平台审核',
      kind: 'pending' as const,
    },
    {
      title: '已加白账号',
      count: mediaAccountsLoading ? '加载中' : String(approvedCount),
      desc: '可用于推广链接',
      kind: 'approved' as const,
    },
  ]

  return (
    <section className="account-dashboard" aria-labelledby="account-page-title">
      <div className="dashboard-breadcrumb">工作台 / 账户概览</div>
      <div className="dashboard-heading">
        <div>
          <h1 id="account-page-title">账户概览</h1>
          <p>查看推广账号状态和近期业务数据。</p>
        </div>
        <div className="dashboard-user-summary">
          <span className="dashboard-user-avatar" aria-hidden="true">
            {(currentUser.nickname || currentUser.userNo).slice(0, 1)}
          </span>
          <span>{currentUser.nickname || currentUser.userNo}</span>
        </div>
      </div>

      <Row gutter={[16, 16]}>
        {metrics.map((metric) => (
          <Col key={metric.title} xs={12} xl={3}>
            <Card
              className={`dashboard-metric-card${metric.dark ? ' dashboard-metric-card-dark' : ''}`}
              bordered={false}
              title={metric.title}
              footer={
                <div className="dashboard-metric-footer">
                  <span>{metric.desc}</span>
                  <TrendHint>›</TrendHint>
                </div>
              }
            >
              <div className="dashboard-metric-body">
                <strong>{metric.count}</strong>
                <span className="dashboard-metric-icon" aria-hidden="true">
                  <MetricIcon kind={metric.kind} />
                </span>
              </div>
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]} className="dashboard-section-row">
        <Col xs={12} xl={9}>
          <Card title="推广数据" subtitle="订单与佣金趋势" bordered={false}>
            <EmptyTrendChart />
          </Card>
        </Col>
        <Col xs={12} xl={3}>
          <Card title="账号状态" subtitle="当前媒体账号" bordered={false}>
            <div className="dashboard-status-list">
              <div>
                <span>已加白</span>
                <strong>{approvedCount}</strong>
              </div>
              <div>
                <span>审核中</span>
                <strong>{pendingCount}</strong>
              </div>
              <div>
                <span>账号编号</span>
                <strong>{currentUser.userNo}</strong>
              </div>
            </div>
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} className="dashboard-section-row">
        <Col xs={12} xl={8}>
          <Card title="最近绑定账号" bordered={false}>
            <Table
              rowKey="id"
              columns={accountColumns}
              data={recentAccounts}
              loading={mediaAccountsLoading}
              size="medium"
              hover
              empty="暂无媒体账号"
            />
          </Card>
        </Col>
        <Col xs={12} xl={4}>
          <Card title="个人资料" bordered={false}>
            <dl className="dashboard-profile-list">
              <div>
                <dt>昵称</dt>
                <dd>{currentUser.nickname || '未设置'}</dd>
              </div>
              <div>
                <dt>手机号</dt>
                <dd>{currentUser.mobile || '未绑定'}</dd>
              </div>
              <div>
                <dt>注册时间</dt>
                <dd>{formatDate(currentUser.createdAt)}</dd>
              </div>
              <div>
                <dt>最近登录</dt>
                <dd>{formatDate(currentUser.lastLoginAt)}</dd>
              </div>
            </dl>
          </Card>
        </Col>
      </Row>
    </section>
  )
}
