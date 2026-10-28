<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { unreadCount } from '@/api/notification'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const unread = ref(0)

async function refreshUnread() {
  try {
    const result = await unreadCount()
    unread.value = result.count
  } catch {
    // 未读数失败不影响主流程，静默处理
  }
}

function handleLogout() {
  auth.logout()
  ElMessage.success('已退出登录')
  void router.push('/login')
}

onMounted(() => {
  void refreshUnread()
})

// 切换到通知页时刷新一次角标
router.afterEach((to) => {
  if (to.name === 'notifications') {
    void refreshUnread()
  }
})
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="brand">
        <div class="brand-mark">FF</div>
        <div>
          <div class="brand-name">FlowForge</div>
          <div class="brand-desc">任务编排与执行</div>
        </div>
      </div>

      <el-menu :default-active="route.path" router class="menu">
        <el-menu-item index="/workflows">
          <span>工作流</span>
        </el-menu-item>
        <el-menu-item index="/executions">
          <span>执行记录</span>
        </el-menu-item>
        <el-menu-item index="/notifications">
          <span>通知中心</span>
          <el-badge v-if="unread > 0" :value="unread" class="badge" />
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-right">
          <span class="who">{{ auth.user?.displayName || auth.user?.username || '未登录' }}</span>
          <el-button text @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}

.aside {
  background: #fff;
  border-right: 1px solid var(--ff-border);
  padding-top: 16px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px 16px;
}

.brand-mark {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: #1f6feb;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 13px;
}

.brand-name {
  font-weight: 600;
}

.brand-desc {
  font-size: 12px;
  color: var(--ff-muted);
}

.menu {
  border-right: none;
}

.badge {
  margin-left: 8px;
}

.header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  background: #fff;
  border-bottom: 1px solid var(--ff-border);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.who {
  font-size: 14px;
}

.main {
  padding: 20px;
  overflow-y: auto;
}
</style>
