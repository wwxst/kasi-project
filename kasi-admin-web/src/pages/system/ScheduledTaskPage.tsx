import { useCallback, useEffect, useState } from 'react'
import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Table,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PageContainer } from '@ant-design/pro-components'
import { useAuthStore } from '../../features/auth/authStore'
import {
  listScheduledTasks,
  updateScheduledTask,
} from '../../features/scheduled-task/scheduledTaskApi'
import type {
  ScheduledTask,
  UpdateScheduledTaskRequest,
} from '../../features/scheduled-task/scheduledTaskTypes'
import './scheduled-task-page.css'

export function ScheduledTaskPage() {
  const [form] = Form.useForm<UpdateScheduledTaskRequest>()
  const { message } = App.useApp()
  const isSuperAdmin = useAuthStore((state) => state.admin?.isSuperAdmin === 1)
  const [tasks, setTasks] = useState<ScheduledTask[]>([])
  const [loading, setLoading] = useState(true)
  const [editingTask, setEditingTask] = useState<ScheduledTask | null>(null)
  const [saving, setSaving] = useState(false)
  const [switchingTaskCode, setSwitchingTaskCode] = useState<string>()
  const intervalMinutes = Form.useWatch('intervalMinutes', form)

  const loadTasks = useCallback(async () => {
    setLoading(true)
    try {
      setTasks(await listScheduledTasks())
    } catch (error) {
      message.error(error instanceof Error ? error.message : '定时任务加载失败')
    } finally {
      setLoading(false)
    }
  }, [message])

  useEffect(() => {
    void loadTasks()
  }, [loadTasks])

  const replaceTask = (updated: ScheduledTask) => {
    setTasks((current) =>
      current.map((task) =>
        task.taskCode === updated.taskCode ? updated : task,
      ),
    )
  }

  const handleToggle = async (task: ScheduledTask, enabled: boolean) => {
    setSwitchingTaskCode(task.taskCode)
    try {
      replaceTask(
        await updateScheduledTask(task.taskCode, {
          intervalMinutes: task.intervalMinutes,
          description: task.description,
          enabled,
        }),
      )
      message.success(enabled ? '定时任务已开启' : '定时任务已关闭')
    } catch (error) {
      message.error(error instanceof Error ? error.message : '定时任务更新失败')
    } finally {
      setSwitchingTaskCode(undefined)
    }
  }

  const openEditor = (task: ScheduledTask) => {
    setEditingTask(task)
    form.setFieldsValue({
      intervalMinutes: task.intervalMinutes,
      description: task.description,
      enabled: task.enabled,
    })
  }

  const handleSave = async () => {
    if (!editingTask) return
    try {
      const values = await form.validateFields()
      setSaving(true)
      replaceTask(
        await updateScheduledTask(editingTask.taskCode, {
          ...values,
          description: values.description.trim(),
        }),
      )
      setEditingTask(null)
      message.success('定时任务已保存')
    } catch (error) {
      if (isValidationError(error)) return
      message.error(error instanceof Error ? error.message : '定时任务保存失败')
    } finally {
      setSaving(false)
    }
  }

  const columns: ColumnsType<ScheduledTask> = [
    { title: '标题', dataIndex: 'title', width: 240 },
    { title: '任务说明', dataIndex: 'description' },
    {
      title: '执行周期',
      dataIndex: 'intervalMinutes',
      width: 220,
      render: (minutes: number) => `每隔${minutes}分钟执行一次`,
    },
    {
      title: '是否开启',
      dataIndex: 'enabled',
      width: 150,
      render: (enabled: boolean, task) => (
        <Switch
          checked={enabled}
          checkedChildren="开启"
          unCheckedChildren="关闭"
          disabled={!isSuperAdmin}
          loading={switchingTaskCode === task.taskCode}
          aria-label={`${task.title}是否开启`}
          onChange={(checked) => void handleToggle(task, checked)}
        />
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, task) =>
        isSuperAdmin ? (
          <Button type="link" size="small" onClick={() => openEditor(task)}>
            编辑
          </Button>
        ) : (
          <span data-testid="scheduled-task-readonly-action">-</span>
        ),
    },
  ]

  return (
    <PageContainer
      className="scheduled-task-page"
      title="定时任务"
      data-testid="scheduled-task-page"
    >
      <Table<ScheduledTask>
        rowKey="taskCode"
        columns={columns}
        dataSource={tasks}
        loading={loading}
        pagination={false}
        scroll={{ x: 960 }}
      />

      <Modal
        title="编辑定时任务"
        open={editingTask !== null}
        width={680}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        destroyOnHidden
        onOk={() => void handleSave()}
        onCancel={() => setEditingTask(null)}
      >
        <Form
          form={form}
          layout="horizontal"
          labelCol={{ flex: '80px' }}
          className="scheduled-task-page__form"
        >
          <Form.Item label="执行周期" required>
            <Space.Compact>
              <Select
                value="INTERVAL_MINUTES"
                disabled
                options={[{ value: 'INTERVAL_MINUTES', label: '每隔N分钟' }]}
                aria-label="周期类型"
              />
              <Form.Item
                name="intervalMinutes"
                noStyle
                rules={[
                  { required: true, message: '请输入执行周期' },
                  { type: 'number', min: 5, message: '执行周期不能少于5分钟' },
                  {
                    type: 'number',
                    max: 1440,
                    message: '执行周期不能超过1440分钟',
                  },
                ]}
              >
                <InputNumber min={5} max={1440} aria-label="执行周期" />
              </Form.Item>
              <span className="scheduled-task-page__cycle-unit">分</span>
            </Space.Compact>
            <div className="scheduled-task-page__cycle-help">
              每隔{intervalMinutes ?? 0}分钟执行一次
            </div>
          </Form.Item>

          <Form.Item
            label="任务说明"
            name="description"
            rules={[
              { required: true, whitespace: true, message: '请输入任务说明' },
              { max: 255, message: '任务说明不能超过255个字符' },
            ]}
          >
            <Input.TextArea rows={3} maxLength={255} />
          </Form.Item>

          <Form.Item label="是否开启" name="enabled" valuePropName="checked">
            <Switch aria-label="是否开启" />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  )
}

function isValidationError(error: unknown) {
  return typeof error === 'object' && error !== null && 'errorFields' in error
}
