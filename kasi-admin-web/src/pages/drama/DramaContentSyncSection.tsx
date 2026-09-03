import { Button, Descriptions, Spin, Tag, Tooltip } from 'antd'
import { RefreshCw } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { isUnauthorizedError } from '../../api/http'
import { getDramaContentSyncStatus } from '../../features/drama/dramaCatalogApi'
import type {
  DramaContentSyncStatus,
  DramaContentSyncTask,
} from '../../features/drama/dramaCatalogTypes'

interface DramaContentSyncSectionProps {
  dramaId: number
  active: boolean
  refreshKey: number
  onSucceeded: () => void
}

const POLL_INTERVAL_MS = 3_000
const MAX_POLL_DURATION_MS = 60_000

const statusLabels: Record<DramaContentSyncStatus, string> = {
  REQUESTED: '等待执行',
  RUNNING: '运行中',
  SUCCESS: '同步成功',
  FAILED: '同步失败',
}

export function DramaContentSyncSection({
  dramaId,
  active,
  refreshKey,
  onSucceeded,
}: DramaContentSyncSectionProps) {
  const [task, setTask] = useState<DramaContentSyncTask | null>(null)
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const pollStartedAt = useRef(0)
  const lastNotifiedSuccessTaskId = useRef<number | null>(null)
  const onSucceededRef = useRef(onSucceeded)

  useEffect(() => {
    onSucceededRef.current = onSucceeded
  }, [onSucceeded])

  const loadStatus = useCallback(async () => {
    setLoading(true)
    try {
      const result = await getDramaContentSyncStatus(dramaId)
      setTask(result)
      setErrorMessage(null)
      if (
        result?.status === 'SUCCESS' &&
        lastNotifiedSuccessTaskId.current !== result.id
      ) {
        lastNotifiedSuccessTaskId.current = result.id
        onSucceededRef.current()
      }
    } catch (error) {
      if (isUnauthorizedError(error)) return
      setErrorMessage(
        error instanceof Error ? error.message : '剧集同步状态加载失败',
      )
    } finally {
      setLoading(false)
    }
  }, [dramaId])

  useEffect(() => {
    if (!active) return
    pollStartedAt.current = Date.now()
    void loadStatus()
  }, [active, dramaId, loadStatus, refreshKey])

  useEffect(() => {
    if (
      !active ||
      !task ||
      (task.status !== 'REQUESTED' && task.status !== 'RUNNING')
    ) {
      return
    }
    if (Date.now() - pollStartedAt.current >= MAX_POLL_DURATION_MS) return
    const timer = window.setTimeout(() => void loadStatus(), POLL_INTERVAL_MS)
    return () => window.clearTimeout(timer)
  }, [active, loadStatus, task])

  const refresh = () => {
    pollStartedAt.current = Date.now()
    void loadStatus()
  }

  if (!active) return null

  return (
    <section
      className="drama-catalog-page__section"
      data-testid="drama-content-sync-section"
    >
      <div className="drama-catalog-page__section-heading">
        <h3>剧集同步</h3>
        <Tooltip title="刷新剧集同步状态">
          <Button
            aria-label="刷新剧集同步状态"
            icon={<RefreshCw size={16} />}
            loading={loading}
            onClick={refresh}
          />
        </Tooltip>
      </div>
      <Spin spinning={loading}>
        {errorMessage ? (
          <div className="drama-catalog-page__content-sync-error">
            {errorMessage}
          </div>
        ) : task ? (
          <>
            <div className="drama-catalog-page__content-sync-status">
              <Tag color={statusColor(task.status)}>
                {statusLabels[task.status]}
              </Tag>
            </div>
            <Descriptions column={2} size="small">
              <Descriptions.Item label="请求时间">
                {formatDate(task.requestedAt)}
              </Descriptions.Item>
              <Descriptions.Item label="下次执行时间">
                {formatDate(task.nextRunAt)}
              </Descriptions.Item>
              <Descriptions.Item label="重试次数">
                {task.retryCount}
              </Descriptions.Item>
              <Descriptions.Item label="本次统计">
                获取 {task.totalFetched} / 新增 {task.insertedCount} / 更新{' '}
                {task.updatedCount}
              </Descriptions.Item>
            </Descriptions>
            {task.lastErrorCode || task.lastErrorMessage ? (
              <div className="drama-catalog-page__content-sync-error">
                {task.lastErrorCode ? <code>{task.lastErrorCode}</code> : null}
                {task.lastErrorMessage ? (
                  <div>{task.lastErrorMessage}</div>
                ) : null}
              </div>
            ) : null}
          </>
        ) : (
          <span>尚未提交剧集同步任务</span>
        )}
      </Spin>
    </section>
  )
}

function statusColor(status: DramaContentSyncStatus) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'error'
  return 'processing'
}

function formatDate(value: string | null | undefined) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}
