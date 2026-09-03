import { useEffect, useMemo, useState } from 'react'
import { Button, MessagePlugin, Table, Tooltip } from 'tdesign-react'
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

function formatDateTime(value: string) {
  return value.slice(0, 19).replace('T', ' ')
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
        row.campaignName || <span className={Style.emptyCell}>未填写</span>,
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
      width: 120,
      cell: ({ row }) => mediaLabels[row.mediaType],
    },
    {
      title: '链接类型',
      colKey: 'linkVariant',
      width: 120,
      cell: ({ row }) => (row.linkVariant === 'ONELINK' ? 'OneLink' : '落地页'),
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
      title: '分享链接',
      colKey: 'shareUrl',
      width: 280,
      cell: ({ row }) => (
        <CopyableCell
          value={row.shareUrl}
          href
          copyLabel="复制分享链接"
          onCopy={copy}
        />
      ),
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
