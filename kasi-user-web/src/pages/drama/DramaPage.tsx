import { useEffect, useMemo, useRef, useState } from 'react'
import { Button, Dialog, MessagePlugin, Table } from 'tdesign-react'
import type { TableProps } from 'tdesign-react'
import { useQuery } from '@tanstack/react-query'
import { createSearchParams, useNavigate } from 'react-router-dom'
import Hls from 'hls.js'
import {
  createDramaDownloadTask,
  downloadDramaTaskFile,
  getDramaDownloadTask,
  getPublishedDramaFreeContent,
  getPublishedDramas,
} from '../../features/dramas/dramasApi'
import type {
  DramaContentResource,
  DramaDownloadTask,
  DramaListItem,
} from '../../features/dramas/types'
import { isHandledRequestError } from '../../shared/api/httpClient'
import SearchForm, { type DramaFilters } from './components/SearchForm'
import { filterDramas, formatDramaDate } from './dramaList'
import Style from './DramaPage.module.less'

const languageLabels: Record<string, string> = {
  ENGLISH: '英文',
  CHINESE: '中文',
}

export default function DramaPage({ title: _title }: { title: string }) {
  const navigate = useNavigate()
  const [filters, setFilters] = useState<DramaFilters>({ title: '' })
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [viewDrama, setViewDrama] = useState<DramaListItem | null>(null)
  const [playingEpisode, setPlayingEpisode] =
    useState<DramaContentResource | null>(null)
  const [playbackError, setPlaybackError] = useState<string | null>(null)
  const [downloadTask, setDownloadTask] = useState<DramaDownloadTask | null>(
    null,
  )
  const [creatingDownload, setCreatingDownload] = useState(false)
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const playbackRefreshAttempted = useRef(false)
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
  const downloadTaskQuery = useQuery({
    queryKey: ['user', 'drama-download-task', downloadTask?.taskId],
    queryFn: () => getDramaDownloadTask(downloadTask!.taskId),
    enabled: downloadTask !== null,
    retry: false,
    refetchInterval: (currentQuery) => {
      const status = currentQuery.state.data?.status ?? downloadTask?.status
      return status === 'PENDING' || status === 'RUNNING' ? 2000 : false
    },
  })
  const currentDownloadTask = downloadTaskQuery.data ?? downloadTask

  useEffect(() => {
    const video = videoRef.current
    const source = playingEpisode?.playUrl
    if (!video || !source) return

    setPlaybackError(null)
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
      if (playbackRefreshAttempted.current || !viewDrama) {
        setPlaybackError('视频加载失败，请稍后重试')
        return
      }
      playbackRefreshAttempted.current = true
      void getPublishedDramaFreeContent(viewDrama.id, true)
        .then((resources) => {
          const refreshed = resources.find(
            (resource) => resource.id === playingEpisode.id,
          )
          if (!refreshed?.playUrl || refreshed.playUrl === source) {
            setPlaybackError('视频加载失败，请稍后重试')
            return
          }
          setPlayingEpisode(refreshed)
        })
        .catch(() => setPlaybackError('视频加载失败，请稍后重试'))
    })
    return () => {
      hls.destroy()
      video.removeAttribute('src')
      video.load()
    }
  }, [playingEpisode, viewDrama])

  useEffect(() => {
    if (!query.isError || isHandledRequestError(query.error)) return
    void MessagePlugin.error('短剧加载失败，请稍后重试')
  }, [query.error, query.isError])

  const rows = useMemo(
    () => filterDramas(query.data?.list ?? [], filters),
    [filters, query.data?.list],
  )

  const createDownload = async (contentIds: number[]) => {
    if (!viewDrama || contentIds.length === 0) return
    setCreatingDownload(true)
    try {
      setDownloadTask(await createDramaDownloadTask(viewDrama.id, contentIds))
    } catch (error) {
      if (!isHandledRequestError(error)) {
        void MessagePlugin.error(
          error instanceof Error ? error.message : '下载任务创建失败',
        )
      }
    } finally {
      setCreatingDownload(false)
    }
  }

  const downloadArchive = async () => {
    if (!currentDownloadTask || currentDownloadTask.status !== 'SUCCESS') return
    try {
      const blob = await downloadDramaTaskFile(currentDownloadTask.taskId)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `drama-${viewDrama?.id ?? 'materials'}.zip`
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (error) {
      if (!isHandledRequestError(error)) {
        void MessagePlugin.error('下载文件失败，请稍后重试')
      }
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
      cell: ({ row }) =>
        languageLabels[row.language ?? ''] ?? row.language ?? '未知',
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
                playbackRefreshAttempted.current = false
                setDownloadTask(null)
              }}
            >
              观看剧集
            </Button>
            <Button
              theme="primary"
              variant="text"
              onClick={() => {
                const params: Record<string, string> = {
                  dramaId: String(row.id),
                }
                if (row.providerId !== null) {
                  params.providerId = String(row.providerId)
                }
                navigate({
                  pathname: '/workspace/promotion-links',
                  search: createSearchParams(params).toString(),
                })
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
      <Dialog
        header="剧集观看与素材下载"
        visible={viewDrama !== null}
        width={760}
        closeBtn
        footer={false}
        onClose={() => {
          setViewDrama(null)
          setPlayingEpisode(null)
          setPlaybackError(null)
          playbackRefreshAttempted.current = false
          setDownloadTask(null)
        }}
      >
        <div className={Style.viewer}>
          <div className={Style.viewerTitle}>
            <span>
              {viewDrama?.titleZh || viewDrama?.title || '未命名短剧'}
            </span>
            <Button
              theme="primary"
              loading={creatingDownload}
              disabled={
                (freeContentQuery.data ?? []).filter(
                  (episode) => episode.free && episode.downloadUrl,
                ).length === 0
              }
              onClick={() =>
                void createDownload(
                  (freeContentQuery.data ?? [])
                    .filter((episode) => episode.free && episode.downloadUrl)
                    .map((episode) => episode.id),
                )
              }
            >
              下载全部
            </Button>
          </div>
          {currentDownloadTask && (
            <div className={Style.downloadTask}>
              <span>
                {currentDownloadTask.status === 'PENDING' && '下载任务已创建'}
                {currentDownloadTask.status === 'RUNNING' &&
                  `正在处理 ${currentDownloadTask.completedCount}/${currentDownloadTask.totalCount}`}
                {currentDownloadTask.status === 'SUCCESS' && '素材压缩包已生成'}
                {currentDownloadTask.status === 'FAILED' &&
                  (currentDownloadTask.errorMessage || '素材下载失败')}
              </span>
              {currentDownloadTask.status === 'SUCCESS' && (
                <Button
                  theme="primary"
                  variant="text"
                  onClick={() => void downloadArchive()}
                >
                  下载 ZIP
                </Button>
              )}
            </div>
          )}
          {playingEpisode?.playUrl && (
            <div className={Style.videoPanel}>
              <video
                ref={videoRef}
                className={Style.video}
                controls
                playsInline
                data-testid="drama-video"
              />
              {playbackError && (
                <div className={Style.playbackError}>{playbackError}</div>
              )}
            </div>
          )}
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
                        playbackRefreshAttempted.current = false
                        setPlayingEpisode(episode)
                      }}
                    >
                      播放
                    </Button>
                    <Button
                      theme="primary"
                      variant="text"
                      loading={creatingDownload}
                      disabled={!episode.downloadUrl}
                      onClick={() => void createDownload([episode.id])}
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
      </Dialog>
    </div>
  )
}
