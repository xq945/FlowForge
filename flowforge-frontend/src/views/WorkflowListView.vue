<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  disableWorkflow,
  enableWorkflow,
  listWorkflows,
  publishWorkflow,
} from '@/api/workflow'
import { newRequestId, triggerExecution } from '@/api/execution'
import type { WorkflowSummary } from '@/types'

const router = useRouter()

const loading = ref(false)
const rows = ref<WorkflowSummary[]>([])
const total = ref(0)
const query = reactive({ keyword: '', page: 1, size: 10 })

const STATUS_TEXT: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  DISABLED: '已停用',
}

const STATUS_TYPE: Record<string, 'info' | 'success' | 'warning'> = {
  DRAFT: 'info',
  PUBLISHED: 'success',
  DISABLED: 'warning',
}

async function load() {
  loading.value = true
  try {
    const result = await listWorkflows({
      keyword: query.keyword || undefined,
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

async function handlePublish(row: WorkflowSummary) {
  try {
    const result = await publishWorkflow(row.id)
    ElMessage.success(`发布成功，版本 v${result.versionNo}`)
    await load()
  } catch {
    // 校验失败的详细原因由后端返回，拦截器已提示
  }
}

async function handleToggle(row: WorkflowSummary) {
  const disabling = row.status === 'PUBLISHED'
  try {
    await ElMessageBox.confirm(
      disabling
        ? '停用后不会再被手动或定时触发，已在执行的实例不受影响。'
        : '启用后可以重新手动触发与定时执行。',
      disabling ? '确认停用' : '确认启用',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  try {
    if (disabling) {
      await disableWorkflow(row.id)
      ElMessage.success('已停用')
    } else {
      await enableWorkflow(row.id)
      ElMessage.success('已启用')
    }
    await load()
  } catch {
    /* 已提示 */
  }
}

async function handleTrigger(row: WorkflowSummary) {
  if (row.status !== 'PUBLISHED') {
    ElMessage.warning('请先发布再执行')
    return
  }
  try {
    const result = await triggerExecution({ workflowId: row.id, requestId: newRequestId() })
    ElMessage.success(`已创建执行实例 #${result.executionId}`)
    await router.push(`/executions/${result.executionId}`)
  } catch {
    /* 已提示 */
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div>
    <div class="page-header">
      <div>
        <h2 class="page-title">工作流</h2>
        <p class="page-subtitle">创建、编排并发布自动化任务</p>
      </div>
      <el-button type="primary" @click="router.push('/workflows/new')">新建工作流</el-button>
    </div>

    <div class="panel">
      <el-input
        v-model="query.keyword"
        placeholder="按名称搜索"
        clearable
        class="search"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button @click="search">搜索</el-button>

      <el-table v-loading="loading" :data="rows" class="table" empty-text="还没有工作流，先创建一个吧">
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="muted">{{ row.description || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="STATUS_TYPE[row.status] || 'info'" effect="light">
              {{ STATUS_TEXT[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="80">
          <template #default="{ row }">
            <span class="mono">{{ row.latestVersionNo > 0 ? `v${row.latestVersionNo}` : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180">
          <template #default="{ row }">
            <span class="mono">{{ (row.updatedAt || '').replace('T', ' ').slice(0, 19) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="router.push(`/workflows/${row.id}/edit`)">编辑</el-button>
            <el-button text type="primary" @click="handlePublish(row)">发布</el-button>
            <el-button text type="primary" @click="handleTrigger(row)">执行</el-button>
            <el-button text :type="row.status === 'PUBLISHED' ? 'danger' : 'success'" @click="handleToggle(row)">
              {{ row.status === 'PUBLISHED' ? '停用' : '启用' }}
            </el-button>
            <el-button text @click="router.push(`/executions?workflowId=${row.id}`)">记录</el-button>
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
.search {
  width: 240px;
  margin-right: 8px;
}

.table {
  margin-top: 16px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
