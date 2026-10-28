<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listExecutions } from '@/api/execution'
import type { ExecutionSummary } from '@/types'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const rows = ref<ExecutionSummary[]>([])
const total = ref(0)
const query = reactive({
  workflowId: route.query.workflowId ? Number(route.query.workflowId) : undefined,
  status: '',
  page: 1,
  size: 10,
})

const STATUS_TYPE: Record<string, 'success' | 'danger' | 'warning' | 'info'> = {
  SUCCESS: 'success',
  FAILED: 'danger',
  CANCELLED: 'info',
  TIMED_OUT: 'warning',
  RUNNING: 'info',
  PENDING: 'info',
}

const STATUS_TEXT: Record<string, string> = {
  SUCCESS: '成功',
  FAILED: '失败',
  CANCELLED: '已取消',
  TIMED_OUT: '超时',
  RUNNING: '执行中',
  PENDING: '等待中',
}

const TRIGGER_TEXT: Record<string, string> = {
  MANUAL: '手动',
  SCHEDULE: '定时',
  MANUAL_RETRY: '手动重试',
}

async function load() {
  loading.value = true
  try {
    const result = await listExecutions({
      workflowId: query.workflowId,
      status: query.status || undefined,
      page: query.page,
      size: query.size,
    })
    rows.value = result.records
    total.value = result.total
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  void load()
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div>
    <div class="page-header">
      <div>
        <h2 class="page-title">执行记录</h2>
        <p class="page-subtitle">
          {{ query.workflowId ? `已筛选工作流 #${query.workflowId}` : '所有工作流的执行历史' }}
        </p>
      </div>
      <el-button
        v-if="query.workflowId"
        @click="query.workflowId = undefined; search()"
      >
        清除筛选
      </el-button>
    </div>

    <div class="panel">
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 160px" @change="search">
        <el-option label="成功" value="SUCCESS" />
        <el-option label="失败" value="FAILED" />
        <el-option label="超时" value="TIMED_OUT" />
        <el-option label="已取消" value="CANCELLED" />
        <el-option label="执行中" value="RUNNING" />
      </el-select>

      <el-table v-loading="loading" :data="rows" class="table" empty-text="还没有执行记录">
        <el-table-column label="实例" width="90">
          <template #default="{ row }"><span class="mono">#{{ row.id }}</span></template>
        </el-table-column>
        <el-table-column label="工作流" width="100">
          <template #default="{ row }"><span class="mono">#{{ row.workflowId }}</span></template>
        </el-table-column>
        <el-table-column label="版本" width="80">
          <template #default="{ row }"><span class="mono">v{{ row.versionNo }}</span></template>
        </el-table-column>
        <el-table-column label="触发方式" width="100">
          <template #default="{ row }">{{ TRIGGER_TEXT[row.triggerType] || row.triggerType }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="STATUS_TYPE[row.status] || 'info'" effect="light">
              {{ STATUS_TEXT[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="100">
          <template #default="{ row }">
            <span class="mono">{{ row.finishedNodes }}/{{ row.totalNodes }}</span>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" width="180">
          <template #default="{ row }">
            <span class="mono">{{ (row.startedAt || '').replace('T', ' ').slice(0, 19) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="router.push(`/executions/${row.id}`)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.table {
  margin-top: 16px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
