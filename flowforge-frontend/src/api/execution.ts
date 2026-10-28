import client from './client'
import type {
  AttemptView,
  ExecutionDetail,
  ExecutionSummary,
  PageResult,
} from '@/types'

export interface TriggerPayload {
  workflowId: number
  requestId: string
  timeoutSeconds?: number
}

export interface TriggerResult {
  executionId: number
}

export function triggerExecution(payload: TriggerPayload): Promise<TriggerResult> {
  return client.post('/executions', payload)
}

export function listExecutions(params: {
  workflowId?: number
  status?: string
  page?: number
  size?: number
}): Promise<PageResult<ExecutionSummary>> {
  return client.get('/executions', { params })
}

export function getExecution(id: number): Promise<ExecutionDetail> {
  return client.get(`/executions/${id}`)
}

export function listAttempts(executionId: number, nodeExecutionId: number): Promise<AttemptView[]> {
  return client.get(`/executions/${executionId}/nodes/${nodeExecutionId}/attempts`)
}

/**
 * 幂等键由前端生成：重复点击、网络重试只要复用同一个 requestId，
 * 服务端就只会创建一个执行实例。
 */
export function newRequestId(): string {
  return `web-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}
