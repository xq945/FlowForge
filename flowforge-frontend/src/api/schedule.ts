import client from './client'
import type { ScheduleJobView } from '@/types'

export interface CreateSchedulePayload {
  workflowId: number
  name: string
  cronExpr: string
  timezone?: string
}

export function listSchedules(workflowId?: number): Promise<ScheduleJobView[]> {
  return client.get('/schedules', { params: { workflowId } })
}

export function createSchedule(payload: CreateSchedulePayload): Promise<ScheduleJobView> {
  return client.post('/schedules', payload)
}

export function enableSchedule(id: number): Promise<void> {
  return client.post(`/schedules/${id}/enable`)
}

export function disableSchedule(id: number): Promise<void> {
  return client.post(`/schedules/${id}/disable`)
}
