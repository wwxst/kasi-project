export type ScheduledTaskCode = 'GOODSHORT_DRAMA_INCREMENTAL_SYNC'

export interface ScheduledTask {
  taskCode: ScheduledTaskCode
  title: string
  description: string
  intervalMinutes: number
  enabled: boolean
}

export interface UpdateScheduledTaskRequest {
  cycleType?: string
  intervalMinutes: number
  description: string
  enabled: boolean
}
