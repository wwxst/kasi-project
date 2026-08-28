import { useEffect, useMemo, useRef, useState } from 'react'
import {
  Button,
  Dialog,
  Drawer,
  Form,
  Input,
  Loading,
  MessagePlugin,
  Select,
  Table,
} from 'tdesign-react'
import type {
  FormInstanceFunctions,
  FormProps,
  TableProps,
} from 'tdesign-react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import {
  createPromotionLinks,
  getPromotionLinks,
  groupPromotionLinks,
} from '../../features/promotionLinks/promotionLinksApi'
import type {
  CreatePromotionLinksInput,
  MediaType,
  PromotionLink,
} from '../../features/promotionLinks/types'
import {
  getPublishedDramaDetail,
  getPublishedDramaFreeContent,
} from '../../features/dramas/dramasApi'
import type {
  DramaContent,
  DramaContentResource,
  DramaDetail,
} from '../../features/dramas/types'
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

export default function PromotionLinksPage({
  title: _title,
}: {
  title: string
}) {
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [drawerVisible, setDrawerVisible] = useState(false)
  const [createDialogVisible, setCreateDialogVisible] = useState(false)
  const [playingEpisode, setPlayingEpisode] =
    useState<DramaContentResource | null>(null)
  const [loading, setLoading] = useState(false)
  const formRef = useRef<FormInstanceFunctions | null>(null)
  const dramaId = Number(searchParams.get('dramaId') ?? 0)
  const providerId = Number(searchParams.get('providerId') ?? 0)
  const linksQuery = useQuery({
    queryKey: ['user', 'promotion-links', page, pageSize],
    queryFn: () => getPromotionLinks(page, pageSize),
  })
  const grouped = useMemo(
    () => groupPromotionLinks(linksQuery.data?.list ?? []),
    [linksQuery.data?.list],
  )

  const dramaQuery = useQuery({
    queryKey: ['user', 'promotion-drama-detail', dramaId],
    queryFn: () => getPublishedDramaDetail(dramaId),
    enabled: drawerVisible && dramaId > 0,
  })
  const freeContentQuery = useQuery({
    queryKey: ['user', 'promotion-drama-free-content', dramaId],
    queryFn: () => getPublishedDramaFreeContent(dramaId),
    enabled: drawerVisible && dramaId > 0,
  })

  useEffect(() => {
    if (dramaId > 0) setDrawerVisible(true)
  }, [dramaId])

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

  const submit: FormProps['onSubmit'] = async ({ fields, validateResult }) => {
    if (validateResult !== true || !dramaId || !providerId) return
    const values = fields as { mediaTypes?: MediaType[]; campaignName?: string }
    if (!values.mediaTypes?.length) return
    setLoading(true)
    try {
      await createPromotionLinks({
        providerId,
        dramaId,
        mediaTypes: values.mediaTypes,
        campaignName: values.campaignName,
      } as Omit<CreatePromotionLinksInput, 'requestKey'>)
      void MessagePlugin.success('推广链接生成请求已提交')
      setCreateDialogVisible(false)
      formRef.current?.reset()
      await queryClient.invalidateQueries({
        queryKey: ['user', 'promotion-links'],
      })
    } catch (error) {
      if (!isHandledRequestError(error)) {
        void MessagePlugin.error(
          error instanceof Error ? error.message : '生成失败',
        )
      }
    } finally {
      setLoading(false)
    }
  }

  const columns: TableProps<(typeof grouped)[number]>['columns'] = [
    {
      title: '批次',
      colKey: 'batchNo',
      width: 180,
      cell: ({ row }) => row.batchNo,
    },
    {
      title: '短剧',
      colKey: 'dramaTitle',
      width: 260,
      cell: ({ row }) => row.items[0]?.dramaTitle || '未命名短剧',
    },
    {
      title: '媒体平台',
      colKey: 'mediaType',
      width: 120,
      cell: ({ row }) =>
        [...row.byPlatform.keys()].map((key) => mediaLabels[key]).join('、'),
    },
    {
      title: '口令',
      colKey: 'externalCode',
      width: 220,
      cell: ({ row }) => <CodeCell links={row.items} onCopy={copy} />,
    },
    {
      title: '落地页',
      colKey: 'landing',
      width: 280,
      cell: ({ row }) => (
        <LinkCell
          links={row.items.filter((item) => item.linkVariant === 'LANDING')}
          onCopy={copy}
        />
      ),
    },
    {
      title: 'OneLink',
      colKey: 'onelink',
      width: 280,
      cell: ({ row }) => (
        <LinkCell
          links={row.items.filter((item) => item.linkVariant === 'ONELINK')}
          onCopy={copy}
        />
      ),
    },
  ]

  return (
    <div className={Style.page}>
      <Table
        rowKey="batchNo"
        data={grouped}
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
      {dramaId > 0 && (
        <Drawer
          header={dramaQuery.data ? '短剧详情' : '创建推广任务'}
          visible={drawerVisible}
          size="large"
          footer={null}
          onClose={() => setDrawerVisible(false)}
        >
          <DramaDetailContent
            detail={dramaQuery.data}
            loading={dramaQuery.isLoading}
            onCreate={() => setCreateDialogVisible(true)}
            resources={freeContentQuery.data ?? []}
            resourcesLoading={freeContentQuery.isLoading}
            onPlay={setPlayingEpisode}
          />
        </Drawer>
      )}
      <Dialog
        header="创建链接和口令"
        visible={createDialogVisible}
        width={520}
        closeBtn={null}
        closeOnOverlayClick={false}
        closeOnEscKeydown={false}
        confirmBtn="生成链接"
        cancelBtn="取消"
        confirmLoading={loading}
        onConfirm={() => formRef.current?.submit()}
        onCancel={() => {
          setCreateDialogVisible(false)
          formRef.current?.reset()
        }}
        onClose={() => {
          setCreateDialogVisible(false)
          formRef.current?.reset()
        }}
      >
        <CreatePromotionForm formRef={formRef} onSubmit={submit} />
      </Dialog>
      <Dialog
        header={
          playingEpisode?.title || `第${playingEpisode?.sequenceNo ?? ''}集`
        }
        visible={playingEpisode !== null}
        width={840}
        footer={null}
        onClose={() => setPlayingEpisode(null)}
      >
        {playingEpisode?.playUrl && (
          <video
            className={Style.videoPlayer}
            src={playingEpisode.playUrl}
            controls
            autoPlay
            playsInline
          />
        )}
      </Dialog>
    </div>
  )
}

function DramaDetailContent({
  detail,
  loading,
  onCreate,
  resources,
  resourcesLoading,
  onPlay,
}: {
  detail: DramaDetail | undefined
  loading: boolean
  onCreate: () => void
  resources: DramaContentResource[]
  resourcesLoading: boolean
  onPlay: (resource: DramaContentResource) => void
}) {
  if (loading)
    return (
      <div className={Style.detailLoading}>
        <Loading />
      </div>
    )
  if (!detail) return <div className={Style.detailEmpty}>暂无短剧详情</div>
  const displayTitle =
    detail.titleZh || detail.title || detail.originalTitle || '未命名短剧'
  const resourceById = new Map(
    resources.map((resource) => [resource.id, resource]),
  )
  return (
    <div className={Style.detailContent}>
      <div className={Style.detailIntro}>
        {detail.coverUrl ? (
          <img
            className={Style.detailCover}
            src={detail.coverUrl}
            alt={displayTitle}
          />
        ) : (
          <div className={Style.detailCoverPlaceholder}>暂无</div>
        )}
        <div className={Style.detailMeta}>
          <h2>{displayTitle}</h2>
          <p>{detail.description || '暂无简介'}</p>
        </div>
      </div>
      <section className={Style.materialSection}>
        <div className={Style.sectionHeading}>
          <span>创作素材</span>
          <div className={Style.sectionActions}>
            <Button theme="primary" onClick={onCreate}>
              创建链接和口令
            </Button>
          </div>
        </div>
      </section>
      <Table<DramaContent>
        rowKey="id"
        data={detail.contents ?? []}
        columns={[
          {
            title: '剧集',
            colKey: 'episode',
            cell: ({ row }) => `第${row.sequenceNo}集`,
          },
          {
            title: '操作',
            colKey: 'operation',
            width: 120,
            cell: ({ row }) => (
              <Button
                variant="text"
                disabled={!resourceById.get(row.id)?.playUrl}
                onClick={() => {
                  const resource = resourceById.get(row.id)
                  if (resource) onPlay(resource)
                }}
              >
                播放
              </Button>
            ),
          },
        ]}
        hover
        empty="暂无剧集"
      />
      {resourcesLoading && (
        <div className={Style.detailLoading}>
          <Loading />
        </div>
      )}
    </div>
  )
}

function CreatePromotionForm({
  formRef,
  onSubmit,
}: {
  formRef: React.RefObject<FormInstanceFunctions | null>
  onSubmit: FormProps['onSubmit']
}) {
  return (
    <Form
      ref={formRef}
      className={Style.dialogForm}
      onSubmit={onSubmit}
      labelAlign="top"
    >
      <Form.FormItem
        label="媒体平台"
        name="mediaTypes"
        rules={[{ required: true, message: '请选择媒体平台' }]}
      >
        <Select multiple options={mediaOptions} placeholder="请选择媒体平台" />
      </Form.FormItem>
      <Form.FormItem label="推广名称" name="campaignName">
        <Input placeholder="请输入推广名称" maxlength={128} />
      </Form.FormItem>
    </Form>
  )
}

function LinkCell({
  links,
  onCopy,
}: {
  links: PromotionLink[]
  onCopy: (value: string | null) => void
}) {
  return (
    <div className={Style.linkCell}>
      {links.map((link) => (
        <div key={link.id}>
          {link.shareUrl ? (
            <>
              <a href={link.shareUrl} target="_blank" rel="noreferrer">
                {link.shareUrl}
              </a>
              <Button
                variant="text"
                size="small"
                onClick={() => onCopy(link.shareUrl)}
              >
                复制
              </Button>
            </>
          ) : (
            <span className={Style.emptyCell}>暂无</span>
          )}
        </div>
      ))}
    </div>
  )
}

function CodeCell({
  links,
  onCopy,
}: {
  links: PromotionLink[]
  onCopy: (value: string | null) => void
}) {
  const codes = [
    ...new Set(
      links
        .map((link) => link.externalCode)
        .filter((code): code is string => Boolean(code)),
    ),
  ]
  return (
    <div className={Style.linkCell}>
      {codes.length > 0 ? (
        codes.map((code) => (
          <div key={code}>
            <span title={code}>{code}</span>
            <Button variant="text" size="small" onClick={() => onCopy(code)}>
              复制
            </Button>
          </div>
        ))
      ) : (
        <span className={Style.emptyCell}>暂无</span>
      )}
    </div>
  )
}
