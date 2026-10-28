<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listNotifications, markRead } from '@/api/notification'
import type { NotificationView } from '@/types'

const router = useRouter()

const loading = ref(false)
const rows = ref<NotificationView[]>([])
const total = ref(0)
const query = reactive({ unreadOnly: false, page: 1, size: 10 })

const TYPE_TYPE: Record<string, 'success' | 'danger' | 'warning' | 'info'> = {
  WORKFLOW_SUCCESS: 'success',
  WORKFLOW_FAILED: 'danger',
  WORKFLOW_TIMED_OUT: 'warning',
  WORKFLOW_CANCELLED: 'info',
}

async function load() {
  loading.value = true
  try {
    const result = await listNotifications({
      unreadOnly: query.unreadOnly,
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

async function handleRead(item: NotificationView) {
  if (item.read) {
    return
  }
  try {
    await markRead(item.id)
    item.read = true
  } catch {
    /* 已提示 */
  }
}

async function openExecution(item: NotificationView) {
  await handleRead(item)
  if (item.bizType === 'EXECUTION') {
    await router.push(`/executions/${item.bizId}`)
  }
}

function switchFilter() {
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
        <h2 class="page-title">通知中心</h2>
        <p class="page-subtitle">工作流执行结束时会在这里收到站内通知</p>
      </div>
      <el-switch
        v-model="query.unreadOnly"
        active-text="只看未读"
        @change="switchFilter"
      />
    </div>

    <div v-loading="loading" class="panel">
      <el-empty v-if="!loading && rows.length === 0" description="暂时没有通知" />
      <div v-for="item in rows" :key="item.id" class="item" :class="{ unread: !item.read }">
        <div class="item-main">
          <div class="item-head">
            <el-tag :type="TYPE_TYPE[item.type] || 'info'" size="small" effect="light">
              {{ item.title }}
            </el-tag>
            <span v-if="!item.read" class="dot" />
            <span class="muted mono">{{ (item.createdAt || '').replace('T', ' ').slice(0, 19) }}</span>
          </div>
          <div class="item-content">{{ item.content }}</div>
        </div>
        <div class="item-actions">
          <el-button v-if="!item.read" text type="primary" @click="handleRead(item)">标记已读</el-button>
          <el-button text @click="openExecution(item)">查看执行</el-button>
        </div>
      </div>

      <div v-if="rows.length > 0" class="pager">
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
.item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 4px;
  border-bottom: 1px solid var(--ff-border);
}

.item:last-of-type {
  border-bottom: none;
}

.item.unread .item-content {
  color: var(--ff-text);
  font-weight: 500;
}

.item-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.item-content {
  font-size: 13px;
  color: var(--ff-muted);
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #1f6feb;
}

.item-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
