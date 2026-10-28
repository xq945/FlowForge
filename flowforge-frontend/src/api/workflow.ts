import client from './client'
import type {
  EdgeSpec,
  NodeSpec,
  PageResult,
  VersionInfo,
  WorkflowDetail,
  WorkflowSummary,
} from '@/types'

export interface WorkflowSavePayload {
  name: string
  description?: string
  nodes: NodeSpec[]
  edges: EdgeSpec[]
}

export interface ValidationResult {
  valid: boolean
  errors: string[]
}

export interface PublishResult {
  versionId: number
  versionNo: number
  contentHash: string
  publishedAt: string
}

export function listWorkflows(params: {
  keyword?: string
  page?: number
  size?: number
}): Promise<PageResult<WorkflowSummary>> {
  return client.get('/workflows', { params })
}

export function getWorkflow(id: number): Promise<WorkflowDetail> {
  return client.get(`/workflows/${id}`)
}

export function createWorkflow(payload: WorkflowSavePayload): Promise<WorkflowDetail> {
  return client.post('/workflows', payload)
}

export function saveDraft(id: number, payload: WorkflowSavePayload): Promise<WorkflowDetail> {
  return client.put(`/workflows/${id}`, payload)
}

export function publishWorkflow(id: number): Promise<PublishResult> {
  return client.post(`/workflows/${id}/publish`)
}

export function enableWorkflow(id: number): Promise<void> {
  return client.post(`/workflows/${id}/enable`)
}

export function disableWorkflow(id: number): Promise<void> {
  return client.post(`/workflows/${id}/disable`)
}

export function validateWorkflow(payload: WorkflowSavePayload): Promise<ValidationResult> {
  return client.post('/workflows/validate', payload)
}

export type { VersionInfo }
