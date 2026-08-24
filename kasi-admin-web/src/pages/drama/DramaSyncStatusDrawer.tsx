import {
  App as AntdApp,
  Button,
  Drawer,
  Select,
  Table,
  Tag,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { RefreshCw } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { isUnauthorizedError } from '../../api/http'
import { listDramaSyncStatuses } from '../../features/drama/dramaCatalogApi'
import type {
  DramaSyncStatus,
  DramaSyncTask,
  DramaSyncType,
} from '../../features/drama/dramaCatalogTypes'
import type { DramaProvider } from '../../features/provider/providerTypes'

interface DramaSyncStatusDrawerProps {
  open: boolean
  providers: DramaProvider[]
  providerId: number | null
  onProviderChange: (providerId: number) => void
  onClose: () => void
}

const syncTypeLabels: Record<DramaSyncType, string> = {
  FULL: '全量同步',
  INCREMENTAL: '增量同步',
}

const syncStatusLabels: Record<DramaSyncStatus, string> = {
  IDLE: '等待触发',
  REQUESTED: '等待执行',
  RUNNING: '运行中',
  SUCCESS: '同步成功',
  FAILED: '同步失败',
}

export function DramaSyncStatusDrawer({
  open,
  providers,
  providerId,
  onProviderChange,
  onClose,
}: DramaSyncStatusDrawerProps) {
  const { message } = AntdApp.useApp()
  const [tasks, setTasks] = useState<DramaSyncTask[]>([])
  const [loading, setLoading] = useState(false)

  const loadStatuses = useCallback(async () => {
    if (providerId === null) {
      setTasks([])
      return
    }
    setLoading(true)
    try {
      setTasks(await listDramaSyncStatuses(providerId))
    } catch (error) {
      if (isUnauthorizedError(error)) return
      message.error(error instanceof Error ? error.message : '同步状态加载失败')
    } finally {
      setLoading(false)
    }
  }, [message, providerId])

  useEffect(() => {
    if (open) void loadStatuses()
  }, [loadStatuses, open])

  const columns: ColumnsType<DramaSyncTask> = [
    {
      title: '任务',
      key: 'task',
      width: 120,
      render: (_, task) => (
        <div className="drama-catalog-page__sync-task">
          <strong>{syncTypeLabels[task.syncType]}</strong>
          <span>{task.language}</span>
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: DramaSyncStatus) => <SyncStatusTag status={status} />,
    },
    {
      title: '进度',
      dataIndex: 'pageNo',
      width: 90,
      render: (pageNo: number) => `第 ${pageNo} 页`,
    },
    {
      title: '本次统计',
      key: 'statistics',
      width: 250,
      render: (_, task) => (
        <div className="drama-catalog-page__sync-statistics">
          <span>拉取 {task.totalFetched}</span>
          <span>写入 {task.totalUpserted}</span>
          <span>新增 {task.insertedCount}</span>
          <span>更新 {task.updatedCount}</span>
          <span>跳过 {task.skippedCount}</span>
          <span>异常 {task.errorCount}</span>
        </div>
      ),
    },
    {
      title: '最近结果',
      key: 'result',
      width: 220,
      render: (_, task) => (
        <div className="drama-catalog-page__sync-result">
          <span>成功：{formatDate(task.lastSuccessAt)}</span>
          {task.lastErrorCode ? <code>{task.lastErrorCode}</code> : null}
          {task.lastErrorMessage ? (
            <strong>{task.lastErrorMessage}</strong>
          ) : null}
        </div>
      ),
    },
  ]

  return (
    <Drawer
      title="目录同步状态"
      open={open}
      size={760}
      rootClassName="drama-catalog-page__status-drawer"
      onClose={onClose}
    >
      <div data-testid="drama-sync-status-drawer">
        <div className="drama-catalog-page__status-toolbar">
          <Select
            aria-label="同步状态平台"
            value={providerId ?? undefined}
            placeholder="请选择短剧平台"
            options={providers.map((provider) => ({
              value: provider.id,
              label: provider.providerName,
            }))}
            onChange={onProviderChange}
          />
          <Tooltip title="刷新同步状态">
            <Button
              aria-label="刷新同步状态"
              icon={<RefreshCw size={16} />}
              loading={loading}
              data-testid="drama-sync-status-refresh"
              onClick={() => void loadStatuses()}
            />
          </Tooltip>
        </div>

        <Table<DramaSyncTask>
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={tasks}
          locale={{ emptyText: '暂无同步任务' }}
          pagination={false}
          scroll={{ x: 780 }}
        />
      </div>
    </Drawer>
  )
}

function SyncStatusTag({ status }: { status: DramaSyncStatus }) {
  const color =
    status === 'SUCCESS'
      ? 'success'
      : status === 'FAILED'
        ? 'error'
        : status === 'RUNNING' || status === 'REQUESTED'
          ? 'processing'
          : 'default'
  return <Tag color={color}>{syncStatusLabels[status]}</Tag>
}

function formatDate(value: string | null | undefined) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}
