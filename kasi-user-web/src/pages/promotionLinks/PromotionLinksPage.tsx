import { useEffect, useMemo, useState } from 'react'
import { Button, MessagePlugin, Table, Tag, Tooltip } from 'tdesign-react'
import type { TableProps } from 'tdesign-react'
import { CopyIcon } from 'tdesign-icons-react'
import { useQuery } from '@tanstack/react-query'
import { getPromotionLinks } from '../../features/promotionLinks/promotionLinksApi'
import type { MediaType } from '../../features/promotionLinks/types'
import { isHandledRequestError } from '../../shared/api/httpClient'
import Style from './PromotionLinksPage.module.less'

const mediaOptions = [
  { label: 'TikTok', value: 'TIKTOK' },
  { label: 'YouTube', value: 'YOUTUBE' },
  { label: 'Facebook', value: 'FACEBOOK' },
  { label: 'Instagram', value: 'INSTAGRAM' },
]

const mediaLabels = Object.fromEntries(
  mediaOptions.map((item) => [item.value, item.label]),
) as Record<MediaType, string>

const CONFLICT_TIP = '同一口令由多个媒体平台共用，无法确定转化归属'

function formatDateTime(value: string) {
  return value.slice(0, 19).replace('T', ' ')
}

function metricCell(value: number | null) {
  if (value === null) {
    return <span className={Style.emptyCell}>—</span>
  }
  return value
}

export default function PromotionLinksPage({
  title: _title,
}: {
  title: string
}) {
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const linksQuery = useQuery({
    queryKey: ['user', 'promotion-links', page, pageSize],
    queryFn: () => getPromotionLinks(page, pageSize),
  })
  const links = useMemo(
    () => linksQuery.data?.list ?? [],
    [linksQuery.data?.list],
  )

  useEffect(() => {
    if (linksQuery.isError && !isHandledRequestError(linksQuery.error)) {
      void MessagePlugin.error('推广任务加载失败，请稍后重试')
    }
  }, [linksQuery.error, linksQuery.isError])

  const copy = async (value: string | null) => {
    if (!value) return
    try {
      await navigator.clipboard.writeText(value)
      void MessagePlugin.success('已复制')
    } catch {
      void MessagePlugin.error('复制失败，请手动复制')
    }
  }

  const columns: TableProps<(typeof links)[number]>['columns'] = [
    {
      title: '创建时间',
      colKey: 'createdAt',
      width: 180,
      cell: ({ row }) => formatDateTime(row.createdAt),
    },
    {
      title: '推广名称',
      colKey: 'campaignName',
      width: 180,
      cell: ({ row }) =>
        row.campaignName || <span className={Style.emptyCell}>—</span>,
    },
    {
      title: '短剧',
      colKey: 'dramaTitle',
      width: 260,
      cell: ({ row }) => row.dramaTitle || '未命名短剧',
    },
    {
      title: '媒体平台',
      colKey: 'mediaType',
      width: 130,
      cell: ({ row }) =>
        row.analyticsConflict ? (
          <Tooltip content={CONFLICT_TIP}>
            <Tag theme="warning" variant="light">
              归因冲突
            </Tag>
          </Tooltip>
        ) : row.mediaType ? (
          mediaLabels[row.mediaType]
        ) : (
          <span className={Style.emptyCell}>暂无</span>
        ),
    },
    {
      title: '口令',
      colKey: 'externalCode',
      width: 180,
      cell: ({ row }) => (
        <CopyableCell
          value={row.externalCode}
          compact
          copyLabel="复制口令"
          onCopy={copy}
        />
      ),
    },
    {
      title: 'OneLink',
      colKey: 'oneLinkUrl',
      width: 320,
      cell: ({ row }) => (
        <PromotionLinkCell
          value={row.oneLinkUrl}
          copyLabel="复制OneLink链接"
          onCopy={copy}
        />
      ),
    },
    {
      title: '落地页',
      colKey: 'landingUrl',
      width: 320,
      cell: ({ row }) => (
        <PromotionLinkCell
          value={row.landingUrl}
          copyLabel="复制落地页链接"
          onCopy={copy}
        />
      ),
    },
    {
      title: '点击数',
      colKey: 'clickCount',
      width: 100,
      cell: ({ row }) => metricCell(row.clickCount),
    },
    {
      title: '归因用户数',
      colKey: 'attributedUserCount',
      width: 120,
      cell: ({ row }) => metricCell(row.attributedUserCount),
    },
    {
      title: '新注册人数',
      colKey: 'newRegisteredUserCount',
      width: 120,
      cell: ({ row }) => metricCell(row.newRegisteredUserCount),
    },
    {
      title: '新充值人数',
      colKey: 'newPaidUserCount',
      width: 120,
      cell: ({ row }) => metricCell(row.newPaidUserCount),
    },
    {
      title: '新会员人数',
      colKey: 'newMemberUserCount',
      width: 120,
      cell: ({ row }) => metricCell(row.newMemberUserCount),
    },
    {
      title: '充值用户数',
      colKey: 'paidUserCount',
      width: 120,
      cell: ({ row }) => metricCell(row.paidUserCount),
    },
    {
      title: '订单数',
      colKey: 'orderCount',
      width: 100,
      cell: ({ row }) => metricCell(row.orderCount),
    },
  ]

  return (
    <div className={Style.page}>
      <Table
        rowKey="id"
        data={links}
        columns={columns}
        loading={linksQuery.isLoading}
        hover
        empty="暂无推广任务"
        pagination={{
          current: page,
          pageSize,
          total: linksQuery.data?.total ?? 0,
          showJumper: true,
          onCurrentChange: setPage,
          onPageSizeChange: (size) => {
            setPageSize(size)
            setPage(1)
          },
        }}
      />
    </div>
  )
}

function PromotionLinkCell({
  value,
  copyLabel,
  onCopy,
}: {
  value: string | null
  copyLabel: string
  onCopy: (value: string | null) => void
}) {
  if (!value) return <span className={Style.emptyCell}>暂无</span>

  return (
    <div className={Style.linkCell}>
      <a
        className={Style.linkValue}
        href={value}
        target="_blank"
        rel="noreferrer"
        title={value}
        style={{ overflowWrap: 'anywhere', whiteSpace: 'normal' }}
      >
        {value}
      </a>
      <Tooltip content="复制">
        <Button
          className={Style.copyButton}
          variant="text"
          shape="square"
          size="small"
          icon={<CopyIcon />}
          aria-label={copyLabel}
          onClick={() => onCopy(value)}
        />
      </Tooltip>
    </div>
  )
}

function CopyableCell({
  value,
  href = false,
  compact = false,
  copyLabel,
  onCopy,
}: {
  value: string | null
  href?: boolean
  compact?: boolean
  copyLabel: string
  onCopy: (value: string | null) => void
}) {
  if (!value) return <span className={Style.emptyCell}>暂无</span>

  return (
    <div
      className={
        compact ? `${Style.copyCell} ${Style.copyCellCompact}` : Style.copyCell
      }
    >
      {href ? (
        <a
          className={Style.copyValue}
          href={value}
          target="_blank"
          rel="noreferrer"
          title={value}
        >
          {value}
        </a>
      ) : (
        <span className={Style.copyValue} title={value}>
          {value}
        </span>
      )}
      <Tooltip content="复制">
        <Button
          className={Style.copyButton}
          variant="text"
          shape="square"
          size="small"
          icon={<CopyIcon />}
          aria-label={copyLabel}
          onClick={() => onCopy(value)}
        />
      </Tooltip>
    </div>
  )
}
