export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  traceId?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface UserProfile {
  id: number
  username: string
  displayName: string
  email: string | null
  roles: string[]
  createdAt: string | null
}

export interface LoginResponse {
  token: string
  tokenType: string
  expiresIn: number
  user: UserProfile
}

export type NodeType = 'HTTP' | 'CONDITION' | 'MOCK_TASK' | 'NOTIFY'
export type WorkflowStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'

export interface NodeSpec {
  nodeKey: string
  nodeType: NodeType
  name: string
  config: Record<string, unknown>
  timeoutMs?: number | null
  retryMax?: number | null
  retryInitialMs?: number | null
  retryMaxMs?: number | null
  retryOnUncertain?: boolean | null
  posX?: number | null
  posY?: number | null
}

export interface EdgeSpec {
  fromNodeKey: string
  toNodeKey: string
  conditionValue: string | null
}

export interface VersionInfo {
  id: number
  versionNo: number
  status: string
  contentHash: string | null
  publishedAt: string | null
}

export interface WorkflowSummary {
  id: number
  name: string
  description: string | null
  status: WorkflowStatus
  latestVersionNo: number
  createdAt: string
  updatedAt: string
}

export interface WorkflowDetail {
  id: number
  name: string
  description: string | null
  status: WorkflowStatus
  currentVersionId: number | null
  latestVersionNo: number
  draftVersionId: number | null
  nodes: NodeSpec[]
  edges: EdgeSpec[]
  versions: VersionInfo[]
  createdAt: string
  updatedAt: string
}

export interface ExecutionSummary {
  id: number
  workflowId: number
  versionNo: number
  triggerType: string
  status: string
  totalNodes: number
  finishedNodes: number
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
}

export interface NodeExecutionView {
  id: number
  nodeKey: string
  nodeType: string
  nodeName: string
  status: string
  retryCount: number
  maxRetry: number
  attemptNo: number
  nextRetryAt: string | null
  output: string | null
  errorCode: string | null
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
}

export interface ExecutionDetail {
  id: number
  workflowId: number
  workflowVersionId: number
  versionNo: number
  triggerType: string
  status: string
  totalNodes: number
  finishedNodes: number
  errorCode: string | null
  errorMessage: string | null
  deadlineAt: string | null
  startedAt: string | null
  finishedAt: string | null
  nodes: NodeExecutionView[]
}

export interface AttemptView {
  id: number
  attemptNo: number
  status: string
  errorCode: string | null
  errorMessage: string | null
  uncertain: boolean
  requestSummary: string | null
  responseSummary: string | null
  durationMs: number | null
  startedAt: string | null
  finishedAt: string | null
}

export interface NotificationView {
  id: number
  type: string
  title: string
  content: string
  bizType: string
  bizId: number
  read: boolean
  createdAt: string
}

export interface ScheduleJobView {
  id: number
  workflowId: number
  name: string
  cronExpr: string
  timezone: string
  enabled: boolean
  nextFireTime: string
  lastFireTime: string | null
  createdAt: string
}
