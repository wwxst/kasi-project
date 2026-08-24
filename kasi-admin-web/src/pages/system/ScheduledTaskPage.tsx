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
import { isUnauthorizedError } from '../../api/http'
import { useAuthStore } from '../../features/auth/authStore'
import {
  listScheduledTasks,
  updateScheduledTask,
} from '../../features/scheduled-task/scheduledTaskApi'
import type {
  ScheduledTask,
  ScheduledTaskCycleType,
  UpdateScheduledTaskRequest,
} from '../../features/scheduled-task/scheduledTaskTypes'
import './scheduled-task-page.css'

const cycleOptions: { value: ScheduledTaskCycleType; label: string }[] = [
  { value: 'INTERVAL_SECONDS', label: '每隔N秒' },
  { value: 'INTERVAL_MINUTES', label: '每隔N分钟' },
  { value: 'INTERVAL_HOURS', label: '每隔N小时' },
  { value: 'INTERVAL_DAYS', label: '每隔N天' },
  { value: 'DAILY', label: '每天' },
  { value: 'WEEKLY', label: '每星期' },
  { value: 'MONTHLY', label: '每月' },
  { value: 'YEARLY', label: '每年' },
]

type FormValues = {
  cycleType: ScheduledTaskCycleType
  intervalValue?: number
  intervalHoursPart?: number
  intervalMinutesPart?: number
  timeHour?: number
  timeMinute?: number
  timeSecond?: number
  dayOfWeek?: number
  dayOfMonth?: number
  monthOfYear?: number
  description: string
  enabled: boolean
}

const timeParts = (time?: string) => {
  const [hour = '0', minute = '0', second = '0'] = (time ?? '00:00:00').split(':')
  return { timeHour: Number(hour), timeMinute: Number(minute), timeSecond: Number(second) }
}

const timeValue = (values: FormValues) =>
  `${String(values.timeHour ?? 0).padStart(2, '0')}:${String(values.timeMinute ?? 0).padStart(2, '0')}:${String(values.timeSecond ?? 0).padStart(2, '0')}`

export function ScheduledTaskPage() {
  const [form] = Form.useForm<FormValues>()
  const { message } = App.useApp()
  const isSuperAdmin = useAuthStore((state) => state.admin?.isSuperAdmin === 1)
  const [tasks, setTasks] = useState<ScheduledTask[]>([])
  const [loading, setLoading] = useState(true)
  const [editingTask, setEditingTask] = useState<ScheduledTask | null>(null)
  const [selectedCycleType, setSelectedCycleType] = useState<ScheduledTaskCycleType>('INTERVAL_MINUTES')
  const [saving, setSaving] = useState(false)
  const [switchingTaskCode, setSwitchingTaskCode] = useState<string>()
  const cycleType = selectedCycleType
  const intervalValue = Form.useWatch('intervalValue', form)
  const intervalHoursPart = Form.useWatch('intervalHoursPart', form)
  const intervalMinutesPart = Form.useWatch('intervalMinutesPart', form)
  const watchedValues = Form.useWatch([], form) as Partial<FormValues>

  const loadTasks = useCallback(async () => {
    setLoading(true)
    try {
      setTasks(await listScheduledTasks())
    } catch (error) {
      if (isUnauthorizedError(error)) return
      message.error(error instanceof Error ? error.message : '定时任务加载失败')
    } finally {
      setLoading(false)
    }
  }, [message])

  useEffect(() => {
    void loadTasks()
  }, [loadTasks])

  const replaceTask = (updated: ScheduledTask) => {
    setTasks((current) => current.map((task) => task.taskCode === updated.taskCode ? updated : task))
  }

  const requestFor = (task: ScheduledTask, enabled: boolean): UpdateScheduledTaskRequest => ({
    cycleType: task.cycleType ?? 'INTERVAL_MINUTES',
    intervalValue: task.intervalValue ?? task.intervalMinutes,
    ...(task.cycleType === 'INTERVAL_HOURS' || task.cycleType === 'INTERVAL_DAYS' ? {
      intervalHoursPart: task.intervalHoursPart ?? 0,
      intervalMinutesPart: task.intervalMinutesPart ?? 0,
    } : {}),
    timeOfDay: task.timeOfDay,
    dayOfWeek: task.dayOfWeek,
    dayOfMonth: task.dayOfMonth,
    monthOfYear: task.monthOfYear,
    description: task.description,
    enabled,
  })

  const handleToggle = async (task: ScheduledTask, enabled: boolean) => {
    setSwitchingTaskCode(task.taskCode)
    try {
      replaceTask(await updateScheduledTask(task.taskCode, requestFor(task, enabled)))
      message.success(enabled ? '定时任务已开启' : '定时任务已关闭')
    } catch (error) {
      if (isUnauthorizedError(error)) return
      message.error(error instanceof Error ? error.message : '定时任务更新失败')
    } finally {
      setSwitchingTaskCode(undefined)
    }
  }

  const openEditor = (task: ScheduledTask) => {
    const parts = timeParts(task.timeOfDay)
    form.setFieldsValue({
      cycleType: task.cycleType ?? 'INTERVAL_MINUTES',
      intervalValue: task.intervalValue ?? task.intervalMinutes,
      intervalHoursPart: task.intervalHoursPart ?? 0,
      intervalMinutesPart: task.intervalMinutesPart ?? 0,
      ...parts,
      dayOfWeek: task.dayOfWeek,
      dayOfMonth: task.dayOfMonth,
      monthOfYear: task.monthOfYear,
      description: task.description,
      enabled: task.enabled,
    })
    setSelectedCycleType(task.cycleType ?? 'INTERVAL_MINUTES')
    setEditingTask(task)
  }

  const handleCycleChange = (value: ScheduledTaskCycleType) => {
    setSelectedCycleType(value)
    const current = form.getFieldValue('intervalValue') as number | undefined
    form.setFieldsValue({
      cycleType: value,
      intervalValue: value === 'INTERVAL_HOURS' || value === 'INTERVAL_DAYS'
        ? (current && current <= 24 ? current : 1)
        : current ?? 1,
      intervalHoursPart: form.getFieldValue('intervalHoursPart') ?? 0,
      intervalMinutesPart: form.getFieldValue('intervalMinutesPart') ?? 0,
    })
  }

  const handleSave = async () => {
    if (!editingTask) return
    try {
      const values = await form.validateFields()
      setSaving(true)
      const request: UpdateScheduledTaskRequest = {
        cycleType: values.cycleType,
        intervalValue: values.intervalValue,
        ...(values.cycleType === 'INTERVAL_HOURS' || values.cycleType === 'INTERVAL_DAYS' ? {
          intervalHoursPart: values.intervalHoursPart ?? 0,
          intervalMinutesPart: values.intervalMinutesPart ?? 0,
        } : {}),
        timeOfDay: values.cycleType === 'INTERVAL_SECONDS' || values.cycleType === 'INTERVAL_MINUTES'
          ? undefined
          : timeValue(values),
        dayOfWeek: values.dayOfWeek,
        dayOfMonth: values.dayOfMonth,
        monthOfYear: values.monthOfYear,
        description: values.description.trim(),
        enabled: values.enabled,
      }
      replaceTask(await updateScheduledTask(editingTask.taskCode, request))
      setEditingTask(null)
      message.success('定时任务已保存')
    } catch (error) {
      if (isValidationError(error)) return
      if (isUnauthorizedError(error)) return
      message.error(error instanceof Error ? error.message : '定时任务保存失败')
    } finally {
      setSaving(false)
    }
  }

  const columns: ColumnsType<ScheduledTask> = [
    { title: '标题', dataIndex: 'title', width: 240 },
    { title: '任务说明', dataIndex: 'description' },
    { title: '执行周期', dataIndex: 'cycleType', width: 280, render: (_, task) => formatCycle(task) },
    {
      title: '是否开启', dataIndex: 'enabled', width: 150,
      render: (enabled: boolean, task) => (
        <Switch checked={enabled} checkedChildren="开启" unCheckedChildren="关闭"
          disabled={!isSuperAdmin} loading={switchingTaskCode === task.taskCode}
          aria-label={`${task.title}是否开启`} onChange={(checked) => void handleToggle(task, checked)} />
      ),
    },
    {
      title: '操作', key: 'action', width: 100,
      render: (_, task) => isSuperAdmin ? <Button type="link" size="small" onClick={() => openEditor(task)}>编辑</Button> : <span data-testid="scheduled-task-readonly-action">-</span>,
    },
  ]

  return (
    <PageContainer className="scheduled-task-page" title="定时任务" data-testid="scheduled-task-page">
      <Table<ScheduledTask> rowKey="taskCode" columns={columns} dataSource={tasks} loading={loading} pagination={false} scroll={{ x: 960 }} />
      <Modal title="编辑定时任务" open={editingTask !== null} width={760} okText="保存" cancelText="取消" confirmLoading={saving} destroyOnHidden onOk={() => void handleSave()} onCancel={() => setEditingTask(null)}>
        <Form form={form} layout="horizontal" labelCol={{ flex: '80px' }} className="scheduled-task-page__form">
          <Form.Item label="执行周期" required>
            <div className="scheduled-task-page__cycle-row">
              <Select value={cycleType} options={cycleOptions} onChange={handleCycleChange} aria-label="周期类型" />
              <IntervalFields cycleType={cycleType} />
              <CalendarFields cycleType={cycleType} />
            </div>
            <div className="scheduled-task-page__cycle-help">{formatFormCycle({ ...watchedValues, intervalHoursPart, intervalMinutesPart }, cycleType, intervalValue)}</div>
            <Form.Item name="cycleType" hidden><Input aria-label="执行周期" /></Form.Item>
          </Form.Item>
          <Form.Item label="任务说明" name="description" rules={[{ required: true, whitespace: true }, { max: 255 }]}><Input.TextArea rows={3} maxLength={255} /></Form.Item>
          <Form.Item label="是否开启" name="enabled" valuePropName="checked"><Switch /></Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  )
}

function IntervalFields({ cycleType }: { cycleType: ScheduledTaskCycleType }) {
  if (!cycleType.startsWith('INTERVAL_')) return null
  const unit = cycleType === 'INTERVAL_SECONDS' ? '秒' : cycleType === 'INTERVAL_MINUTES' ? '分' : cycleType === 'INTERVAL_HOURS' ? '时' : '日'
  return <>
    <Form.Item name="intervalValue" noStyle rules={[{ required: true }, { type: 'number', min: 1 }]}><UnitNumber max={cycleType === 'INTERVAL_DAYS' ? 365 : 1440} unit={unit} ariaLabel="执行周期数值" /></Form.Item>
    {cycleType === 'INTERVAL_HOURS' ? <NumberField name="intervalMinutesPart" unit="分" max={59} label="间隔分钟" /> : null}
    {cycleType === 'INTERVAL_DAYS' ? <><NumberField name="intervalHoursPart" unit="时" max={23} label="间隔小时" /><NumberField name="intervalMinutesPart" unit="分" max={59} label="间隔分钟" /></> : null}
  </>
}

function CalendarFields({ cycleType }: { cycleType: ScheduledTaskCycleType }) {
  if (cycleType === 'INTERVAL_SECONDS' || cycleType === 'INTERVAL_MINUTES') return null
  return <>
    {cycleType === 'WEEKLY' ? <><span>星期</span><Form.Item name="dayOfWeek" noStyle rules={[{ required: true }]}><Select className="scheduled-task-page__weekday" options={[1, 2, 3, 4, 5, 6, 7].map((value) => ({ value, label: `周${['一', '二', '三', '四', '五', '六', '日'][value - 1]}` }))} aria-label="星期" /></Form.Item></> : null}
    {cycleType === 'MONTHLY' || cycleType === 'YEARLY' ? <><span>日期</span><NumberField name="dayOfMonth" unit="日" max={31} label="日期" /></> : null}
    {cycleType === 'YEARLY' ? <><span>月份</span><NumberField name="monthOfYear" unit="月" max={12} label="月份" /></> : null}
    <span>执行时间</span>
    <NumberField name="timeHour" unit="时" max={23} label="执行小时" />
    <NumberField name="timeMinute" unit="分" max={59} label="执行分钟" />
    <NumberField name="timeSecond" unit="秒" max={59} label="执行秒" />
  </>
}

function NumberField({ name, unit, max, label }: { name: keyof FormValues; unit: string; max: number; label: string }) {
  return <Form.Item name={name} noStyle rules={[{ required: true }, { type: 'number', min: 0, max }]}><UnitNumber max={max} unit={unit} ariaLabel={label} /></Form.Item>
}

function UnitNumber({ max, unit, ariaLabel, value, onChange }: {
  max: number
  unit: string
  ariaLabel: string
  value?: number
  onChange?: (value: number | null) => void
}) {
  return <Space.Compact className="scheduled-task-page__unit-number"><InputNumber min={0} max={max} value={value} onChange={onChange} aria-label={ariaLabel} /><span className="scheduled-task-page__unit">{unit}</span></Space.Compact>
}

function formatFormCycle(values: Partial<FormValues>, type: ScheduledTaskCycleType, value?: number) {
  if (type === 'INTERVAL_SECONDS') return `每隔${value ?? 0}秒执行一次`
  if (type === 'INTERVAL_MINUTES') return `每隔${value ?? 0}分钟执行一次`
  if (type === 'INTERVAL_HOURS') return `每隔${value ?? 0}小时${values.intervalMinutesPart ?? 0}分钟执行一次`
  if (type === 'INTERVAL_DAYS') return `每隔${value ?? 0}天${values.intervalHoursPart ?? 0}小时${values.intervalMinutesPart ?? 0}分钟执行一次`
  const time = `${String(values.timeHour ?? 0).padStart(2, '0')}:${String(values.timeMinute ?? 0).padStart(2, '0')}:${String(values.timeSecond ?? 0).padStart(2, '0')}`
  if (type === 'DAILY') return `每天${time}执行一次`
  if (type === 'WEEKLY') return `每星期${values.dayOfWeek ?? ''} ${time}执行一次`
  if (type === 'MONTHLY') return `每月${values.dayOfMonth ?? ''}日 ${time}执行一次`
  return `每年${values.monthOfYear ?? ''}月${values.dayOfMonth ?? ''}日 ${time}执行一次`
}

function formatCycle(task: ScheduledTask) {
  const parts = timeParts(task.timeOfDay)
  return formatFormCycle({ ...task, ...parts }, task.cycleType ?? 'INTERVAL_MINUTES', task.intervalValue ?? task.intervalMinutes)
}

function isValidationError(error: unknown) {
  return typeof error === 'object' && error !== null && 'errorFields' in error
}
