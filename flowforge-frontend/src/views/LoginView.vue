<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { register } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const mode = ref<'login' | 'register'>('login')
const loading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  username: '',
  password: '',
  displayName: '',
  email: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{4,32}$/, message: '4-32 位字母、数字或下划线', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度 8-64 位', trigger: 'blur' },
  ],
}

async function submit() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  try {
    if (mode.value === 'register') {
      await register({
        username: form.username,
        password: form.password,
        displayName: form.displayName || form.username,
        email: form.email || undefined,
      })
      ElMessage.success('注册成功，请登录')
      mode.value = 'login'
      return
    }

    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/workflows'
    await router.push(redirect)
  } catch {
    // 错误提示已由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

function switchMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  formRef.value?.clearValidate()
}
</script>

<template>
  <div class="login-page">
    <div class="card">
      <div class="head">
        <div class="mark">FF</div>
        <h1>FlowForge</h1>
        <p class="muted">智能任务编排与执行平台</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" autocomplete="username" placeholder="请输入用户名" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="请输入密码"
            @keyup.enter="submit"
          />
        </el-form-item>

        <template v-if="mode === 'register'">
          <el-form-item label="显示名称">
            <el-input v-model="form.displayName" placeholder="可选，默认与用户名一致" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="form.email" placeholder="可选" />
          </el-form-item>
        </template>

        <el-button type="primary" class="submit" :loading="loading" @click="submit">
          {{ mode === 'login' ? '登录' : '注册' }}
        </el-button>
      </el-form>

      <div class="switch">
        <el-button text type="primary" @click="switchMode">
          {{ mode === 'login' ? '没有账号？去注册' : '已有账号？去登录' }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eef3fb 0%, #f7f9fc 100%);
}

.card {
  width: 380px;
  padding: 32px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(31, 35, 41, 0.08);
}

.head {
  text-align: center;
  margin-bottom: 24px;
}

.mark {
  width: 44px;
  height: 44px;
  margin: 0 auto 12px;
  border-radius: 10px;
  background: #1f6feb;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

h1 {
  margin: 0;
  font-size: 20px;
}

.submit {
  width: 100%;
  margin-top: 8px;
}

.switch {
  text-align: center;
  margin-top: 12px;
}
</style>
