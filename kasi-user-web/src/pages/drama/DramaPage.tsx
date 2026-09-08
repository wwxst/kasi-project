import { useEffect, useRef, useState } from 'react'
import {
  Button,
  Dialog,
  Drawer,
  Form,
  Input,
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
import { useNavigate } from 'react-router-dom'
import Hls from 'hls.js'
import {
  getPublishedDramaFreeContent,
  getPublishedDramas,
} from '../../features/dramas/dramasApi'
import type {
  DramaContentResource,
  DramaListItem,
} from '../../features/dramas/types'
import { createPromotionLinks } from '../../features/promotionLinks/promotionLinksApi'
import type { MediaType } from '../../features/promotionLinks/types'
import { isHandledRequestError } from '../../shared/api/httpClient'
import SearchForm, { type DramaFilters } from './components/SearchForm'
import { formatDramaDate } from './dramaList'
import Style from './DramaPage.module.less'

const mediaOptions = [
  { label: 'TikTok', value: 'TIKTOK' },
  { label: 'YouTube', value: 'YOUTUBE' },
  { label: 'Facebook', value: 'FACEBOOK' },
  { label: 'Instagram', value: 'INSTAGRAM' },
]

async function triggerNativeDownload(url: string, filename: string) {
  const response = await fetch(url, { credentials: 'omit', mode: 'cors' })
  if (!response.ok) {
    throw new Error(`Download request failed with status ${response.status}`)
  }
  const blob = await response.blob()
  const objectUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = filename
  anchor.rel = 'noopener'
  anchor.style.display = 'none'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(objectUrl)
}

function episodeFilename(
  drama: DramaListItem,
  sequenceNo: number,
  resourceUrl: string,
) {
  const title = (drama.titleZh || drama.title || `drama-${drama.id}`)
    .replace(/[\\/:*?"<>|]/g, '_')
    .trim()
  let extension = ''
  try {
    const pathname = new URL(resourceUrl).pathname.toLowerCase()
    if (pathname.endsWith('.mp4')) extension = '.mp4'
    if (pathname.endsWith('.m3u8')) extension = '.m3u8'
  } catch {
    // Keep an unknown resource extension honest instead of claiming MP4.
  }
  return `${title}-第${String(sequenceNo).padStart(2, '0')}集${extension}`
}

function isHlsResource(resourceUrl: string) {
  try {
    return new URL(resourceUrl).pathname.toLowerCase().endsWith('.m3u8')
  } catch {
    return false
  }
}

export default function DramaPage({ title: _title }: { title: string }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState<DramaFilters>({ title: '' })
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [viewDrama, setViewDrama] = useState<DramaListItem | null>(null)
  const [playingEpisode, setPlayingEpisode] =
    useState<DramaContentResource | null>(null)
  const [playbackError, setPlaybackError] = useState<string | null>(null)
  const [createDialogVisible, setCreateDialogVisible] = useState(false)
  const [creatingPromotion, setCreatingPromotion] = useState(false)
  const [videoElement, setVideoElement] = useState<HTMLVideoElement | null>(
    null,
  )
  const promotionFormRef = useRef<FormInstanceFunctions | null>(null)
  const query = useQuery({
    queryKey: ['user', 'dramas', page, pageSize, filters],
    queryFn: () =>
      getPublishedDramas({
        page,
        size: pageSize,
        title: filters.title || undefined,
        language: filters.language,
      }),
  })
  const freeContentQuery = useQuery({
    queryKey: ['user', 'drama-free-content', viewDrama?.id],
    queryFn: () => getPublishedDramaFreeContent(viewDrama!.id),
    enabled: viewDrama !== null,
    retry: false,
  })
  useEffect(() => {
    const video = videoElement
    const source = playingEpisode?.playUrl
    if (!video || !source) return

    setPlaybackError(null)
    if (!isHlsResource(source)) {
      video.src = source
      void video.play().catch(() => undefined)
      return () => {
        video.removeAttribute('src')
        video.load()
      }
    }

    if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = source
      void video.play().catch(() => undefined)
      return () => {
        video.removeAttribute('src')
        video.load()
      }
    }

    if (!Hls.isSupported()) {
      setPlaybackError('当前浏览器不支持播放该视频')
      return
    }

    const hls = new Hls({ enableWorker: true })
    hls.loadSource(source)
    hls.attachMedia(video)
    hls.on(Hls.Events.ERROR, (_event, data) => {
      if (!data.fatal) return
      setPlaybackError('视频加载失败，请稍后重试')
    })
    return () => {
      hls.destroy()
      video.removeAttribute('src')
      video.load()
    }
  }, [playingEpisode, videoElement])

  useEffect(() => {
    if (!playingEpisode) return
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      setPlayingEpisode(null)
      setPlaybackError(null)
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [playingEpisode])

  useEffect(() => {
    if (!query.isError || isHandledRequestError(query.error)) return
    void MessagePlugin.error('短剧加载失败，请稍后重试')
  }, [query.error, query.isError])

  const rows = query.data?.list ?? []

  const downloadEpisodes = async (episodes: DramaContentResource[]) => {
    if (!viewDrama) return
    const availableEpisodes = episodes
      .filter((episode) => episode.free && episode.playUrl)
      .sort((left, right) => left.sequenceNo - right.sequenceNo)

    let startedCount = 0
    for (const episode of availableEpisodes) {
      try {
        await triggerNativeDownload(
          episode.playUrl!,
          episodeFilename(viewDrama, episode.sequenceNo, episode.playUrl!),
        )
        startedCount += 1
      } catch {
        // Continue the batch when one CDN resource rejects the CORS request.
      }
    }

    if (startedCount > 0) {
      void MessagePlugin.success(
        startedCount === 1 ? '已开始下载' : `已开始下载 ${startedCount} 集`,
      )
    } else if (availableEpisodes.length > 0) {
      void MessagePlugin.error('素材下载失败，请稍后重试')
    }
  }

  const submitPromotion: FormProps['onSubmit'] = async ({
    fields,
    validateResult,
  }) => {
    if (validateResult !== true || !viewDrama?.providerId) return
    const values = fields as {
      mediaTypes?: MediaType[]
      linkVariant?: 'LANDING' | 'ONELINK'
      campaignName?: string
    }
    if (!values.mediaTypes?.length) return
    setCreatingPromotion(true)
    try {
      const result = await createPromotionLinks({
        providerId: viewDrama.providerId,
        dramaId: viewDrama.id,
        mediaTypes: values.mediaTypes,
        linkVariant: values.linkVariant ?? 'LANDING',
        campaignName: values.campaignName,
      })
      if (!result.complete) {
        void MessagePlugin.error('部分推广链接或口令生成失败，请重新发起推广')
        return
      }
      setCreateDialogVisible(false)
      promotionFormRef.current?.reset()
      void queryClient.invalidateQueries({
        queryKey: ['user', 'promotion-links'],
      })
      navigate('/workspace/promotion-links')
      void MessagePlugin.success('推广链接和口令已生成')
    } catch (error) {
      if (!isHandledRequestError(error)) {
        void MessagePlugin.error(
          error instanceof Error ? error.message : '生成失败',
        )
      }
    } finally {
      setCreatingPromotion(false)
    }
  }

  const columns: TableProps<DramaListItem>['columns'] = [
    {
      title: '短剧信息',
      colKey: 'dramaInfo',
      width: 390,
      fixed: 'left',
      cell: ({ row }) => {
        const displayTitle =
          row.titleZh || row.title || row.originalTitle || '未命名'
        const originalTitle = row.originalTitle || row.title
        return (
          <div className={Style.dramaInfo}>
            {row.coverUrl ? (
              <img
                className={Style.cover}
                src={row.coverUrl}
                alt={displayTitle}
              />
            ) : (
              <div className={Style.coverPlaceholder}>暂无</div>
            )}
            <div className={Style.dramaMeta}>
              <div className={Style.dramaTitle} title={displayTitle}>
                {displayTitle}
              </div>
              {originalTitle && originalTitle !== displayTitle && (
                <div className={Style.originalTitle} title={originalTitle}>
                  {originalTitle}
                </div>
              )}
              {row.labelNames.length > 0 && (
                <div className={Style.labels}>
                  {row.labelNames.map((label, index) => (
                    <span className={Style.label} key={`${label}-${index}`}>
                      {label}
                    </span>
                  ))}
                </div>
              )}
            </div>
          </div>
        )
      },
    },
    {
      title: '平台',
      colKey: 'providerName',
      width: 140,
      cell: ({ row }) => row.providerName || '未设置',
    },
    {
      title: '分类',
      colKey: 'categoryName',
      width: 140,
      cell: ({ row }) => row.categoryName || '未分类',
    },
    {
      title: '语言',
      colKey: 'language',
      width: 120,
      cell: ({ row }) => row.languageLabel || '未知',
    },
    {
      title: '推广说明',
      colKey: 'promotionDescription',
      width: 260,
      ellipsis: true,
      cell: ({ row }) => (
        <span className={Style.description}>
          {row.promotionDescription || '暂无推广说明'}
        </span>
      ),
    },
    {
      title: '发布时间',
      colKey: 'remoteCreatedAt',
      width: 180,
      cell: ({ row }) => formatDramaDate(row.remoteCreatedAt),
    },
    {
      title: '操作',
      colKey: 'operation',
      width: 160,
      fixed: 'right',
      cell: ({ row }) => {
        return (
          <div className={Style.operationCell}>
            <Button
              theme="primary"
              variant="text"
              onClick={() => {
                setViewDrama(row)
                setPlayingEpisode(null)
                setPlaybackError(null)
              }}
            >
              创建推广任务
            </Button>
          </div>
        )
      },
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
          setFilters({ title: '' })
          setPage(1)
        }}
      />
      <Table
        rowKey="id"
        loading={query.isLoading}
        data={rows}
        columns={columns}
        verticalAlign="middle"
        hover
        empty="暂无短剧"
        pagination={{
          current: page,
          pageSize,
          total: query.data?.total ?? 0,
          showJumper: true,
          onCurrentChange: (current) => setPage(current),
          onPageSizeChange: (size) => {
            setPageSize(size)
            setPage(1)
          },
        }}
      />
      <Drawer
        header="剧集观看与素材下载"
        visible={viewDrama !== null}
        size="large"
        footer={null}
        onClose={() => {
          setCreateDialogVisible(false)
          promotionFormRef.current?.reset()
          setViewDrama(null)
          setPlayingEpisode(null)
          setPlaybackError(null)
        }}
      >
        <div className={Style.viewer}>
          <div className={Style.detailIntro}>
            {viewDrama?.coverUrl ? (
              <img
                className={Style.detailCover}
                src={viewDrama.coverUrl}
                alt={viewDrama.titleZh || viewDrama.title || '未命名短剧'}
              />
            ) : (
              <div className={Style.detailCoverPlaceholder}>暂无</div>
            )}
            <div className={Style.detailMeta}>
              <h2>{viewDrama?.titleZh || viewDrama?.title || '未命名短剧'}</h2>
              <p>{viewDrama?.description || '暂无简介'}</p>
            </div>
          </div>
          <div className={Style.viewerActions}>
            <Button
              theme="primary"
              disabled={
                (freeContentQuery.data ?? []).filter(
                  (episode) => episode.free && episode.playUrl,
                ).length === 0
              }
              onClick={() => void downloadEpisodes(freeContentQuery.data ?? [])}
            >
              下载全部
            </Button>
            <Button
              theme="primary"
              disabled={!viewDrama?.providerId}
              onClick={() => setCreateDialogVisible(true)}
            >
              创建链接和口令
            </Button>
          </div>
          {freeContentQuery.isLoading && (
            <div className={Style.viewerState}>正在加载剧集资源...</div>
          )}
          {freeContentQuery.isError && (
            <div className={Style.viewerState}>
              剧集资源加载失败，请稍后重试
            </div>
          )}
          {!freeContentQuery.isLoading && !freeContentQuery.isError && (
            <div className={Style.episodeList}>
              {(freeContentQuery.data ?? []).map((episode) => (
                <div className={Style.episodeRow} key={episode.id}>
                  <div>
                    <span className={Style.episodeTitle}>
                      第{episode.sequenceNo}集
                    </span>
                    {episode.title &&
                      episode.title !== `第${episode.sequenceNo}集` && (
                        <span className={Style.episodeName}>
                          {episode.title}
                        </span>
                      )}
                  </div>
                  <div className={Style.episodeActions}>
                    <Button
                      theme="primary"
                      variant="text"
                      disabled={!episode.playUrl}
                      onClick={() => {
                        setPlayingEpisode(episode)
                      }}
                    >
                      播放
                    </Button>
                    <Button
                      theme="primary"
                      variant="text"
                      disabled={!episode.playUrl}
                      onClick={() => void downloadEpisodes([episode])}
                    >
                      下载
                    </Button>
                  </div>
                </div>
              ))}
              {freeContentQuery.data?.length === 0 && (
                <div className={Style.viewerState}>暂无可观看的免费剧集</div>
              )}
            </div>
          )}
        </div>
      </Drawer>
      <Dialog
        header={playingEpisode ? `第${playingEpisode.sequenceNo}集` : '播放'}
        visible={playingEpisode?.playUrl != null}
        width="min(900px, calc(100vw - 32px))"
        closeBtn
        closeOnOverlayClick={false}
        closeOnEscKeydown
        footer={false}
        onClose={() => {
          setPlayingEpisode(null)
          setPlaybackError(null)
        }}
      >
        <div className={Style.videoLayout}>
          <div className={Style.videoEpisodeList}>
            {(freeContentQuery.data ?? [])
              .filter((episode) => episode.free && episode.playUrl)
              .sort((left, right) => left.sequenceNo - right.sequenceNo)
              .map((episode) => (
                <Button
                  key={episode.id}
                  size="small"
                  theme={
                    episode.id === playingEpisode?.id ? 'primary' : 'default'
                  }
                  variant={
                    episode.id === playingEpisode?.id ? 'base' : 'outline'
                  }
                  onClick={() => {
                    setPlaybackError(null)
                    setPlayingEpisode(episode)
                  }}
                >
                  第{episode.sequenceNo}集
                </Button>
              ))}
          </div>
          <div className={Style.videoPanel}>
            <video
              ref={setVideoElement}
              className={Style.video}
              controls
              playsInline
              data-testid="drama-video"
            />
            {playbackError && (
              <div className={Style.playbackError}>{playbackError}</div>
            )}
          </div>
        </div>
      </Dialog>
      <Dialog
        header="创建链接和口令"
        visible={createDialogVisible}
        width={520}
        closeBtn={null}
        closeOnOverlayClick={false}
        closeOnEscKeydown={false}
        confirmBtn="生成链接"
        cancelBtn="取消"
        confirmLoading={creatingPromotion}
        onConfirm={() => promotionFormRef.current?.submit()}
        onCancel={() => {
          setCreateDialogVisible(false)
          promotionFormRef.current?.reset()
        }}
        onClose={() => {
          setCreateDialogVisible(false)
          promotionFormRef.current?.reset()
        }}
      >
        <Form
          ref={promotionFormRef}
          className={Style.dialogForm}
          onSubmit={submitPromotion}
          labelAlign="top"
        >
          <Form.FormItem
            label="媒体平台"
            name="mediaTypes"
            rules={[{ required: true, message: '请选择媒体平台' }]}
          >
            <Select
              multiple
              options={mediaOptions}
              placeholder="请选择媒体平台"
            />
          </Form.FormItem>
          <Form.FormItem
            label="链接类型"
            name="linkVariant"
            initialData="LANDING"
            rules={[{ required: true, message: '请选择链接类型' }]}
          >
            <Select
              options={[
                { label: '落地页', value: 'LANDING' },
                { label: 'OneLink', value: 'ONELINK' },
              ]}
              placeholder="请选择链接类型"
            />
          </Form.FormItem>
          <Form.FormItem label="推广名称" name="campaignName">
            <Input placeholder="请输入推广名称" maxlength={128} />
          </Form.FormItem>
        </Form>
      </Dialog>
    </div>
  )
}
