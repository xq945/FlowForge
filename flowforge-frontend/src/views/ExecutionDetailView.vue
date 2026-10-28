<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getExecution, listAttempts } from '@/api/execution'
import type { AttemptView, ExecutionDetail, NodeExecutionView } from '@/types'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const detail = ref<ExecutionDetail | null>(null)

const attemptsVisible = ref(false)
const attemptsLoading = ref(false)
const attempts = ref<AttemptView[]>([])
const attemptsNode = ref<NodeExecutionView | null>(null)

const STATUS_TYPE: Record<string, 'success' | 'danger' | 'warning' | 'info'> = {
  SUCCESS: 'success',
  FAILED: 'danger',
  CANCELLED: 'info',
  TIMED_OUT: 'warning',
  RUNNING: 'info',
  PENDING: 'info',
  WAITING: 'info',
  READY: 'info',
  RETRY_WAIT: 'warning',
  SKIPPED: 'info',
}

const STATUS_TEXT: Record<string, string> = {
  SUCCESS: '成功',
  FAILED: '失败',
  CANCELLED: '已取消',
  TIMED_OUT: '超时',
  RUNNING: '执行中',
  PENDING: '等待中',
  WAITING: '等待前置',
  READY: '待执行',
  RETRY_WAIT: '等待重试',
  SKIPPED: '已跳过',
}

const elapsed = computed(() => {
  if (!detail.value?.startedAt) {
    return '—'
  }
  const start = new Date(detail.value.startedAt).getTime()
  const end = detail.value.finishedAt ? new Date(detail.value.finishedAt).getTime() : Date.now()
  const seconds = Math.max(0, (end - start) / 1000)
  return `${seconds.toFixed(2)}s`
})

async function load() {
  loading.value = true
  try {
    detail.value = await getExecution(Number(route.params.id))
  } catch {
    await router.push('/executions')
  } finally {
    loading.value = false
  }
}

async function openAttempts(node: NodeExecutionView) {
  attemptsNode.value = node
  attemptsVisible.value = true
  attemptsLoading.value = true
  attempts.value = []
  try {
    attempts.value = await listAttempts(Number(route.params.id), node.id)
  } catch {
    ElMessage.error('尝试记录加载失败')
  } finally {
    attemptsLoading.value = false
  }
}

function formatTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div v-loading="loading">
    <div class="page-header">
      <div>
        <h2 class="page-title">执行详情 #{{ route.params.id }}</h2>
        <p class="page-subtitle">每个节点的状态、失败原因与每次尝试的明细</p>
      </div>
      <div>
        <el-button @click="load">刷新</el-button>
        <el-button @click="router.push('/executions')">返回列表</el-button>
      </div>
    </div>

    <template v-if="detail">
      <div class="panel">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="状态">
            <el-tag :type="STATUS_TYPE[detail.status] || 'info'">
              {{ STATUS_TEXT[detail.status] || detail.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="工作流">#{{ detail.workflowId }}</el-descriptions-item>
          <el-descriptions-item label="版本">v{{ detail.versionNo }}</el-descriptions-item>
          <el-descriptions-item label="进度">
            {{ detail.finishedNodes }} / {{ detail.totalNodes }}
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ elapsed }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatTime(detail.startedAt) }}</el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="detail.errorMessage"
          class="error-alert"
          type="error"
          :closable="false"
          show-icon
          :title="`${detail.errorCode || '失败'}：${detail.errorMessage}`"
        />
      </div>

      <div class="panel section">
        <div class="section-head">
          <h3>节点</h3>
        </div>
        <el-table :data="detail.nodes">
          <el-table-column prop="nodeKey" label="Key" width="140">
            <template #default="{ row }"><span class="mono">{{ row.nodeKey }}</span></template>
          </el-table-column>
          <el-table-column prop="nodeName" label="名称" min-width="140" show-overflow-tooltip />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="STATUS_TYPE[row.status] || 'info'" effect="light">
                {{ STATUS_TEXT[row.status] || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="尝试" width="110">
            <template #default="{ row }">
              <span class="mono">#{{ row.attemptNo }} / 重试 {{ row.retryCount }}/{{ row.maxRetry }}</span>
            </template>
          </el-table-column>
          <el-table-column label="失败原因" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <span :class="row.errorMessage ? 'danger-text' : 'muted'">
                {{ row.errorMessage || '—' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="结束时间" width="180">
            <template #default="{ row }">
              <span class="mono">{{ formatTime(row.finishedAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" :disabled="row.attemptNo === 0" @click="openAttempts(row)">
                尝试记录
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <el-drawer v-model="attemptsVisible" :title="`尝试记录 · ${attemptsNode?.nodeKey ?? ''}`" size="560px">
      <div v-loading="attemptsLoading">
        <el-empty v-if="!attemptsLoading && attempts.length === 0" description="还没有执行记录" />
        <div v-for="attempt in attempts" :key="attempt.id" class="attempt">
          <div class="attempt-head">
            <span class="mono">第 {{ attempt.attemptNo }} 次</span>
            <el-tag :type="attempt.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ attempt.status }}
            </el-tag>
            <el-tag v-if="attempt.uncertain" type="warning" size="small" effect="plain">
              结果未知
            </el-tag>
            <span class="muted mono">{{ formatTime(attempt.startedAt) }}</span>
          </div>
          <div v-if="attempt.errorCode" class="attempt-row">
            <span class="label">错误码</span><span class="mono">{{ attempt.errorCode }}</span>
          </div>
          <div v-if="attempt.errorMessage" class="attempt-row">
            <span class="label">原因</span><span class="danger-text">{{ attempt.errorMessage }}</span>
          </div>
          <div v-if="attempt.requestSummary" class="attempt-row">
            <span class="label">请求</span><span class="mono">{{ attempt.requestSummary }}</span>
          </div>
          <div v-if="attempt.responseSummary" class="attempt-row">
            <span class="label">响应</span><span class="mono">{{ attempt.responseSummary }}</span>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.section {
  margin-top: 16px;
}

.section-head h3 {
  margin: 0 0 12px;
  font-size: 15px;
}

.error-alert {
  margin-top: 16px;
}

.danger-text {
  color: #c0392b;
}

.attempt {
  padding: 12px 0;
  border-bottom: 1px solid var(--ff-border);
}

.attempt-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.attempt-row {
  display: flex;
  gap: 10px;
  font-size: 13px;
  margin-bottom: 4px;
}

.label {
  color: var(--ff-muted);
  min-width: 48px;
}
</style>
