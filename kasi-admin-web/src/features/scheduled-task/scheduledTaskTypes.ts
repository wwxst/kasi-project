export type ScheduledTaskCode = 'GOODSHORT_DRAMA_INCREMENTAL_SYNC'

export type ScheduledTaskCycleType =
  | 'INTERVAL_SECONDS'
  | 'INTERVAL_MINUTES'
  | 'INTERVAL_HOURS'
  | 'INTERVAL_DAYS'
  | 'DAILY'
  | 'WEEKLY'
  | 'MONTHLY'
  | 'YEARLY'

export interface ScheduledTask {
  taskCode: ScheduledTaskCode
  title: string
  description: string
  cycleType?: ScheduledTaskCycleType
  intervalValue?: number
  intervalHoursPart?: number
  intervalMinutesPart?: number
  timeOfDay?: string
  dayOfWeek?: number
  dayOfMonth?: number
  monthOfYear?: number
  intervalMinutes: number
  enabled: boolean
}

export interface UpdateScheduledTaskRequest {
  cycleType?: ScheduledTaskCycleType
  intervalValue?: number
  intervalHoursPart?: number
  intervalMinutesPart?: number
  timeOfDay?: string
  dayOfWeek?: number
  dayOfMonth?: number
  monthOfYear?: number
  intervalMinutes?: number
  description: string
  enabled: boolean
}
