import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  Button,
  Dialog,
  Drawer,
  Form,
  Input,
  Select,
  Table,
  Tag,
} from 'tdesign-react'
import { CopyIcon, SearchIcon } from 'tdesign-icons-react'
import {
  createPromotionLink,
  fetchPromotionLinks,
  fetchPublishedPromotionDramas,
} from '../../features/promotion/api/promotionLinkApi'
import { fetchMediaAccounts } from '../../features/promotion/api/mediaAccountApi'
import type {
  PromotionDrama,
  PromotionDramaQuery,
} from '../../features/promotion/api/dramaTypes'
import type { PromotionLink } from '../../features/promotion/api/promotionLinkTypes'
import { ApiError } from '../../shared/api/ApiError'
import './promotion-link.css'

function errorMessage(error: unknown) {
  return error instanceof ApiError ? error.message : '请求失败，请稍后重试'
}

function formatDateTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

export function PromotionLinkPage({
  mode = 'create',
}: {
  mode?: 'create' | 'history'
}) {
  const isCreatePage = mode === 'create'

  const queryClient = useQueryClient()
  const [draftFilters, setDraftFilters] = useState<PromotionDramaQuery>({})
  const [filters, setFilters] = useState<PromotionDramaQuery>({})
  const [detailDrama, setDetailDrama] = useState<PromotionDrama | null>(null)
  const [linkDrama, setLinkDrama] = useState<PromotionDrama | null>(null)
  const [mediaAccountId, setMediaAccountId] = useState<number | undefined>()
  const [campaignName, setCampaignName] = useState('')
  const [landingType, setLandingType] = useState<'DEFAULT' | 'ONELINK'>(
    'DEFAULT',
  )
  const [formError, setFormError] = useState('')
  const [latestLink, setLatestLink] = useState<PromotionLink | null>(null)

  const dramasQuery = useQuery({
    queryKey: ['promotion-dramas', filters],
    queryFn: () => fetchPublishedPromotionDramas(filters),
    enabled: isCreatePage,
  })
  const mediaQuery = useQuery({
    queryKey: ['media-accounts'],
    queryFn: fetchMediaAccounts,
    enabled: isCreatePage,
  })
  const linksQuery = useQuery({
    queryKey: ['promotion-links'],
    queryFn: () => fetchPromotionLinks(),
    enabled: !isCreatePage,
  })
  const createMutation = useMutation({
    mutationFn: createPromotionLink,
    onSuccess: async (link) => {
      setLatestLink(link)
      setLinkDrama(null)
      setFormError('')
      await queryClient.invalidateQueries({ queryKey: ['promotion-links'] })
    },
  })

  const dramas = dramasQuery.data?.list ?? []
  const mediaOptions = (mediaQuery.data ?? [])
    .filter(
      (account) =>
        account.status === 1 &&
        linkDrama &&
        account.filings.some(
          (filing) =>
            filing.providerId === linkDrama.providerId &&
            filing.status === 'APPROVED',
        ),
    )
    .map((account) => ({
      value: account.id,
      label: `${account.mediaType === 'TIKTOK' ? 'TikTok' : account.mediaType} / ${account.accountName || account.externalAccountId}`,
    }))

  function openLinkDialog(drama: PromotionDrama) {
    setLinkDrama(drama)
    setMediaAccountId(undefined)
    setCampaignName('')
    setLandingType('DEFAULT')
    setFormError('')
  }

  async function submitLink() {
    if (!linkDrama || !mediaAccountId) {
      setFormError('请选择已报白的媒体账号')
      return
    }
    try {
      await createMutation.mutateAsync({
        providerId: linkDrama.providerId,
        dramaId: linkDrama.id,
        mediaAccountId,
        campaignName: campaignName.trim() || undefined,
        requestKey: crypto.randomUUID(),
        landingType,
      })
    } catch (error) {
      setFormError(errorMessage(error))
    }
  }

  return (
    <section
      className={isCreatePage ? 'promotion-create-page' : 'promotion-link-page'}
      aria-labelledby={
        isCreatePage
          ? 'promotion-create-page-title'
          : 'promotion-link-page-title'
      }
    >
      <div className="promotion-link-breadcrumb">
        推广管理 / <span>{isCreatePage ? '创建推广' : '推广链接记录'}</span>
      </div>
      <div className="promotion-link-heading">
        <p className="page-eyebrow">
          {isCreatePage ? 'CREATE PROMOTION' : 'PROMOTION LINK HISTORY'}
        </p>
        <h1
          id={
            isCreatePage
              ? 'promotion-create-page-title'
              : 'promotion-link-page-title'
          }
        >
          {isCreatePage ? '创建推广' : '推广链接记录'}
        </h1>
        {isCreatePage ? (
          <p>选择短剧和已报白媒体账号，生成 GoodShort 真实口令与推广链接。</p>
        ) : null}
      </div>
      {isCreatePage && dramasQuery.error ? (
        <Alert theme="error" message={errorMessage(dramasQuery.error)} />
      ) : null}
      {isCreatePage && latestLink ? (
        <Alert
          theme="success"
          title="推广链接生成成功"
          message={
            <span>
              追踪号：{latestLink.trackingNo}　口令：{latestLink.externalCode}
              　链接：{latestLink.shareUrl}
            </span>
          }
        />
      ) : null}

      {isCreatePage ? (
        <section
          className="promotion-link-panel promotion-library"
          aria-labelledby="promotion-library-title"
        >
          <div className="promotion-link-panel-heading">
            <div>
              <h2 id="promotion-library-title">短剧库</h2>
              <p>筛选已上架短剧并生成推广链接。</p>
            </div>
            <SearchIcon aria-hidden="true" />
          </div>
          <Form className="promotion-library-filters" layout="inline">
            <Form.FormItem label="短剧名称">
              <Input
                value={draftFilters.title ?? ''}
                placeholder="请输入短剧名称"
                onChange={(title) =>
                  setDraftFilters((value) => ({ ...value, title }))
                }
              />
            </Form.FormItem>
            <Button
              theme="primary"
              icon={<SearchIcon />}
              onClick={() => setFilters({ ...draftFilters, page: 1, size: 20 })}
            >
              查询
            </Button>
            <Button
              variant="outline"
              onClick={() => {
                setDraftFilters({})
                setFilters({})
              }}
            >
              重置
            </Button>
          </Form>
          <Table
            rowKey="id"
            data={dramas}
            loading={dramasQuery.isLoading}
            tableLayout="fixed"
            columns={[
              { colKey: 'title', title: '短剧名称', width: 260 },
              { colKey: 'providerName', title: '平台', width: 120 },
              {
                colKey: 'commissionScopes',
                title: '分佣范围',
                width: 150,
                cell: ({ row }) => (
                  <>
                    {row.commissionScopes?.map((scope) => (
                      <Tag key={scope}>
                        {scope === 'ORDER' ? '订单' : '广告'}
                      </Tag>
                    ))}
                  </>
                ),
              },
              {
                colKey: 'remoteUpdatedAt',
                title: '上线时间',
                width: 170,
                cell: ({ row }) => formatDateTime(row.remoteUpdatedAt),
              },
              {
                colKey: 'promotionDescription',
                title: '推广说明',
                width: 280,
                cell: ({ row }) => row.promotionDescription || '-',
              },
              {
                colKey: 'language',
                title: '语言',
                width: 90,
                cell: ({ row }) =>
                  row.language === 'ENGLISH' ? '英语' : row.language || '-',
              },
              {
                colKey: 'dramaType',
                title: '类型',
                width: 100,
                cell: ({ row }) =>
                  row.dramaType === 'LOCAL_DRAMA'
                    ? '本土剧'
                    : row.dramaType || '-',
              },
              {
                colKey: 'actions',
                title: '操作',
                width: 210,
                cell: ({ row }) => (
                  <div className="promotion-drama-actions">
                    <Button variant="text" onClick={() => setDetailDrama(row)}>
                      查看详情
                    </Button>
                    <Button
                      theme="primary"
                      variant="text"
                      onClick={() => openLinkDialog(row)}
                    >
                      生成推广链接
                    </Button>
                  </div>
                ),
              },
            ]}
            empty={
              <div className="promotion-library-empty">暂无可推广短剧</div>
            }
          />
        </section>
      ) : null}

      {!isCreatePage ? (
        <section
          className="promotion-link-panel"
          aria-labelledby="promotion-link-history-title"
        >
          <div className="promotion-link-panel-heading">
            <div>
              <h2 id="promotion-link-history-title">推广链接记录</h2>
            </div>
          </div>
          <Table
            rowKey="id"
            data={linksQuery.data?.list ?? []}
            loading={linksQuery.isLoading}
            columns={[
              { colKey: 'dramaTitle', title: '短剧' },
              { colKey: 'mediaAccountName', title: '媒体账号' },
              { colKey: 'externalCode', title: '口令' },
              { colKey: 'trackingNo', title: '追踪号' },
              { colKey: 'status', title: '状态' },
              {
                colKey: 'copy',
                title: '操作',
                cell: ({ row }: { row: PromotionLink }) => (
                  <Button
                    shape="square"
                    variant="text"
                    aria-label="复制推广链接"
                    icon={<CopyIcon />}
                    disabled={!row.shareUrl}
                    onClick={() =>
                      void navigator.clipboard.writeText(row.shareUrl || '')
                    }
                  />
                ),
              },
            ]}
          />
        </section>
      ) : null}

      {isCreatePage ? (
        <Drawer
          visible={detailDrama !== null}
          header="短剧详情"
          footer={null}
          onClose={() => setDetailDrama(null)}
        >
          {detailDrama ? (
            <div className="promotion-drama-detail">
              <h3>{detailDrama.title}</h3>
              <p>{detailDrama.description || '暂无简介'}</p>
              <div className="promotion-drama-episodes-empty">
                剧集信息由后端同步后提供
              </div>
              <Button
                theme="primary"
                block
                onClick={() => {
                  const drama = detailDrama
                  setDetailDrama(null)
                  openLinkDialog(drama)
                }}
              >
                生成推广链接
              </Button>
            </div>
          ) : null}
        </Drawer>
      ) : null}

      {isCreatePage ? (
        <Dialog
          visible={linkDrama !== null}
          header="生成链接和口令"
          confirmBtn={{
            content: '生成推广链接',
            loading: createMutation.isPending,
          }}
          onConfirm={() => void submitLink()}
          onClose={() => setLinkDrama(null)}
        >
          <Form labelAlign="top">
            <Form.FormItem label="媒体账号">
              <Select
                inputProps={
                  { 'aria-label': '媒体账号' } as React.ComponentProps<
                    typeof Input
                  > &
                    React.AriaAttributes
                }
                value={mediaAccountId}
                options={mediaOptions}
                placeholder="请选择已报白媒体账号"
                onChange={(value) => setMediaAccountId(Number(value))}
              />
            </Form.FormItem>
            <Form.FormItem label="链接类型">
              <Select
                value={landingType}
                options={[
                  { label: '落地页', value: 'DEFAULT' },
                  { label: 'OneLink', value: 'ONELINK' },
                ]}
                onChange={(value) =>
                  setLandingType(value as 'DEFAULT' | 'ONELINK')
                }
              />
            </Form.FormItem>
            <Form.FormItem label="推广名称">
              <Input
                value={campaignName}
                maxlength={128}
                placeholder="请输入推广名称"
                onChange={setCampaignName}
              />
            </Form.FormItem>
            {mediaOptions.length === 0 ? (
              <Alert theme="warning" message="该平台暂无已报白媒体账号" />
            ) : null}
            {formError ? <Alert theme="error" message={formError} /> : null}
          </Form>
        </Dialog>
      ) : null}
    </section>
  )
}

export type { PromotionDrama }
