import { useEffect, useMemo, useState } from 'react'
import { Button, MessagePlugin, Table, Tag } from 'tdesign-react'
import type { TableProps } from 'tdesign-react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createMediaAccount,
  getMediaAccounts,
} from '../../features/mediaAccounts/mediaAccountsApi'
import type {
  FilingStatus,
  MediaAccount,
  MediaType,
} from '../../features/mediaAccounts/types'
import SearchForm, { type MediaAccountFilters } from './components/SearchForm'
import { isHandledRequestError } from '../../shared/api/httpClient'
import {
  filterAndPaginateMediaAccounts,
  getGoodShortFiling,
} from './mediaAccountList'
import AccountFilingDialog from './components/AccountFilingDialog'
import Style from './MediaAccountsPage.module.less'

const mediaTypeLabels: Record<MediaType, string> = {
  TIKTOK: 'TikTok',
  FACEBOOK: 'Facebook',
  YOUTUBE: 'YouTube',
  INSTAGRAM: 'Instagram',
}

const filingStatusLabels: Record<FilingStatus, string> = {
  PENDING: '报白中',
  APPROVED: '已报白',
  FAILED: '报白失败',
}

function filingTag(status: FilingStatus | null) {
  if (!status)
    return (
      <Tag theme="default" variant="light">
        未报备
      </Tag>
    )
  const theme =
    status === 'APPROVED'
      ? 'success'
      : status === 'FAILED'
        ? 'danger'
        : 'warning'
  return (
    <Tag theme={theme} variant="light">
      {filingStatusLabels[status]}
    </Tag>
  )
}

export default function MediaAccountsPage() {
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState<MediaAccountFilters>({ keyword: '' })
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [dialogVisible, setDialogVisible] = useState(false)
  const [mutationLoading, setMutationLoading] = useState(false)
  const query = useQuery({
    queryKey: ['user', 'media-accounts'],
    queryFn: getMediaAccounts,
  })

  const result = useMemo(
    () =>
      filterAndPaginateMediaAccounts(query.data ?? [], filters, page, pageSize),
    [filters, page, pageSize, query.data],
  )

  useEffect(() => {
    if (!query.isError || isHandledRequestError(query.error)) return
    void MessagePlugin.error('账号报白加载失败，请稍后重试')
  }, [query.error, query.isError])

  const refreshAccounts = async () => {
    await queryClient.invalidateQueries({
      queryKey: ['user', 'media-accounts'],
    })
  }

  const showMutationError = (error: unknown, fallback: string) => {
    if (isHandledRequestError(error)) return
    void MessagePlugin.error(error instanceof Error ? error.message : fallback)
  }

  const handleCreate = async (
    values: Parameters<typeof createMediaAccount>[0],
  ) => {
    setMutationLoading(true)
    try {
      await createMediaAccount(values)
      void MessagePlugin.success('账号报白提交成功')
      setDialogVisible(false)
      await refreshAccounts()
    } catch (error) {
      showMutationError(error, '账号报白提交失败，请稍后重试')
    } finally {
      setMutationLoading(false)
    }
  }

  const columns: TableProps<MediaAccount>['columns'] = [
    {
      title: '媒体平台',
      colKey: 'mediaType',
      width: 160,
      fixed: 'left',
      cell: ({ row }) => mediaTypeLabels[row.mediaType],
    },
    {
      title: '账号名称',
      colKey: 'accountName',
      width: 220,
      ellipsis: true,
      cell: ({ row }) => row.accountName || '未填写',
    },
    {
      title: '账号 ID',
      colKey: 'externalAccountId',
      width: 220,
      ellipsis: true,
    },
    {
      title: '账号链接',
      colKey: 'accountLink',
      width: 260,
      ellipsis: true,
      cell: ({ row }) =>
        row.accountLink ? (
          <a href={row.accountLink} target="_blank" rel="noreferrer">
            {row.accountLink}
          </a>
        ) : (
          '未填写'
        ),
    },
    {
      title: 'GoodShort 报白状态',
      colKey: 'filingStatus',
      width: 160,
      cell: ({ row }) => filingTag(getGoodShortFiling(row)?.status ?? null),
    },
  ]

  return (
    <div className={Style.page}>
      <SearchForm
        onSubmit={(nextFilters) => {
          setFilters(nextFilters)
          setPage(1)
        }}
        onReset={() => {
          setFilters({ keyword: '' })
          setPage(1)
        }}
      />
      <div className={Style.toolbar}>
        <Button theme="primary" onClick={() => setDialogVisible(true)}>
          账号报白
        </Button>
      </div>
      <Table
        rowKey="id"
        loading={query.isLoading}
        data={result.items}
        columns={columns}
        hover
        empty="暂无报白账号"
        pagination={{
          current: page,
          pageSize,
          total: result.total,
          showJumper: true,
          onCurrentChange: (current) => setPage(current),
          onPageSizeChange: (size) => {
            setPageSize(size)
            setPage(1)
          },
        }}
      />
      <AccountFilingDialog
        visible={dialogVisible}
        loading={mutationLoading}
        onClose={() => setDialogVisible(false)}
        onSubmit={handleCreate}
      />
    </div>
  )
}
