<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  createWorkflow,
  getWorkflow,
  publishWorkflow,
  saveDraft,
  validateWorkflow,
  type WorkflowSavePayload,
} from '@/api/workflow'
import type { EdgeSpec, NodeSpec, NodeType, WorkflowDetail } from '@/types'

const route = useRoute()
const router = useRouter()

const workflowId = computed(() => {
  const raw = route.params.id
  return raw ? Number(raw) : null
})

const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const detail = ref<WorkflowDetail | null>(null)

const form = reactive({ name: '', description: '' })
const nodes = ref<NodeSpec[]>([])
const edges = ref<EdgeSpec[]>([])
const validationErrors = ref<string[]>([])

const NODE_TYPE_TEXT: Record<NodeType, string> = {
  HTTP: 'HTTP 请求',
  CONDITION: '条件判断',
  MOCK_TASK: '模拟业务',
  NOTIFY: '发送通知',
}

const OPERATORS = ['EQ', 'NE', 'GT', 'GE', 'LT', 'LE', 'CONTAINS', 'EMPTY', 'NOT_EMPTY']

// ------------------------------------------------------------------ 节点编辑

const dialogVisible = ref(false)
const editingIndex = ref<number>(-1)
const nodeForm = reactive({
  nodeKey: '',
  nodeType: 'HTTP' as NodeType,
  name: '',
  retryMax: 0,
  retryInitialMs: 1000,
  // HTTP
  url: '',
  method: 'GET',
  expectStatus: '200',
  timeoutMs: 10000,
  // CONDITION
  field: '',
  operator: 'EQ',
  value: '',
  // MOCK_TASK
  durationMs: 200,
  failRate: 0,
  // NOTIFY
  title: '',
  content: '',
})

function resetNodeForm() {
  Object.assign(nodeForm, {
    nodeKey: '',
    nodeType: 'HTTP',
    name: '',
    retryMax: 0,
    retryInitialMs: 1000,
    url: '',
    method: 'GET',
    expectStatus: '200',
    timeoutMs: 10000,
    field: '',
    operator: 'EQ',
    value: '',
    durationMs: 200,
    failRate: 0,
    title: '',
    content: '',
  })
}

function nextNodeKey(): string {
  let index = nodes.value.length + 1
  const existing = new Set(nodes.value.map((n) => n.nodeKey))
  while (existing.has(`node_${index}`)) {
    index += 1
  }
  return `node_${index}`
}

function openCreateNode() {
  resetNodeForm()
  nodeForm.nodeKey = nextNodeKey()
  nodeForm.name = ''
  editingIndex.value = -1
  dialogVisible.value = true
}

function openEditNode(index: number) {
  const node = nodes.value[index]
  resetNodeForm()
  nodeForm.nodeKey = node.nodeKey
  nodeForm.nodeType = node.nodeType
  nodeForm.name = node.name
  nodeForm.retryMax = node.retryMax ?? 0
  nodeForm.retryInitialMs = node.retryInitialMs ?? 1000

  const config = (node.config || {}) as Record<string, unknown>
  if (node.nodeType === 'HTTP') {
    nodeForm.url = String(config.url ?? '')
    nodeForm.method = String(config.method ?? 'GET')
    nodeForm.expectStatus = Array.isArray(config.expectStatus)
      ? (config.expectStatus as unknown[]).join(',')
      : '200'
    nodeForm.timeoutMs = Number(config.timeoutMs ?? 10000)
  } else if (node.nodeType === 'CONDITION') {
    nodeForm.field = String(config.field ?? '')
    nodeForm.operator = String(config.operator ?? 'EQ')
    nodeForm.value = String(config.value ?? '')
  } else if (node.nodeType === 'MOCK_TASK') {
    nodeForm.durationMs = Number(config.durationMs ?? 200)
    nodeForm.failRate = Number(config.failRate ?? 0)
  } else if (node.nodeType === 'NOTIFY') {
    nodeForm.title = String(config.title ?? '')
    nodeForm.content = String(config.content ?? '')
  }

  editingIndex.value = index
  dialogVisible.value = true
}

function buildConfig(): Record<string, unknown> {
  switch (nodeForm.nodeType) {
    case 'HTTP':
      return {
        url: nodeForm.url,
        method: nodeForm.method,
        expectStatus: nodeForm.expectStatus
          .split(',')
          .map((item) => item.trim())
          .filter((item) => item.length > 0)
          .map((item) => Number(item) || item),
        timeoutMs: nodeForm.timeoutMs,
      }
    case 'CONDITION':
      return { field: nodeForm.field, operator: nodeForm.operator, value: nodeForm.value }
    case 'MOCK_TASK':
      return { durationMs: nodeForm.durationMs, failRate: nodeForm.failRate }
    case 'NOTIFY':
      return { channel: 'INBOX', title: nodeForm.title, content: nodeForm.content }
    default:
      return {}
  }
}

function saveNode() {
  if (!nodeForm.nodeKey.trim()) {
    ElMessage.warning('节点 key 不能为空')
    return
  }
  if (!/^[a-zA-Z0-9_]{1,64}$/.test(nodeForm.nodeKey)) {
    ElMessage.warning('节点 key 只能包含字母、数字、下划线')
    return
  }

  const duplicated = nodes.value.some(
    (node, index) => node.nodeKey === nodeForm.nodeKey && index !== editingIndex.value,
  )
  if (duplicated) {
    ElMessage.warning('节点 key 已存在')
    return
  }

  const spec: NodeSpec = {
    nodeKey: nodeForm.nodeKey.trim(),
    nodeType: nodeForm.nodeType,
    name: nodeForm.name.trim() || nodeForm.nodeKey.trim(),
    config: buildConfig(),
    retryMax: nodeForm.retryMax,
    retryInitialMs: nodeForm.retryInitialMs,
    timeoutMs: nodeForm.timeoutMs,
  }

  if (editingIndex.value >= 0) {
    const oldKey = nodes.value[editingIndex.value].nodeKey
    nodes.value.splice(editingIndex.value, 1, spec)
    if (oldKey !== spec.nodeKey) {
      // key 改了，同步更新引用它的边，避免产生悬空依赖
      edges.value = edges.value.map((edge) => ({
        fromNodeKey: edge.fromNodeKey === oldKey ? spec.nodeKey : edge.fromNodeKey,
        toNodeKey: edge.toNodeKey === oldKey ? spec.nodeKey : edge.toNodeKey,
        conditionValue: edge.conditionValue,
      }))
    }
  } else {
    nodes.value.push(spec)
  }

  dialogVisible.value = false
}

function removeNode(index: number) {
  const key = nodes.value[index].nodeKey
  nodes.value.splice(index, 1)
  edges.value = edges.value.filter((edge) => edge.fromNodeKey !== key && edge.toNodeKey !== key)
}

// ------------------------------------------------------------------ 依赖编辑

const newEdge = reactive({ from: '', to: '', condition: '' })

const conditionSourceSelected = computed(
  () => nodes.value.find((node) => node.nodeKey === newEdge.from)?.nodeType === 'CONDITION',
)

function addEdge() {
  if (!newEdge.from || !newEdge.to) {
    ElMessage.warning('请选择起点和终点')
    return
  }
  if (newEdge.from === newEdge.to) {
    ElMessage.warning('节点不能依赖自己')
    return
  }

  const exists = edges.value.some(
    (edge) => edge.fromNodeKey === newEdge.from && edge.toNodeKey === newEdge.to,
  )
  if (exists) {
    ElMessage.warning('该依赖已存在')
    return
  }

  let conditionValue: string | null = null
  if (conditionSourceSelected.value) {
    if (!newEdge.condition) {
      ElMessage.warning('条件节点的出边必须选择 TRUE 或 FALSE')
      return
    }
    conditionValue = newEdge.condition
  }

  edges.value.push({ fromNodeKey: newEdge.from, toNodeKey: newEdge.to, conditionValue })
  newEdge.from = ''
  newEdge.to = ''
  newEdge.condition = ''
}

function removeEdge(index: number) {
  edges.value.splice(index, 1)
}

// ------------------------------------------------------------------ 存取

function buildPayload(): WorkflowSavePayload {
  return {
    name: form.name.trim(),
    description: form.description.trim() || undefined,
    nodes: nodes.value,
    edges: edges.value,
  }
}

async function load() {
  if (!workflowId.value) {
    return
  }
  loading.value = true
  try {
    const result = await getWorkflow(workflowId.value)
    detail.value = result
    form.name = result.name
    form.description = result.description || ''
    nodes.value = result.nodes || []
    edges.value = result.edges || []
  } catch {
    await router.push('/workflows')
  } finally {
    loading.value = false
  }
}

async function handleValidate() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写工作流名称')
    return
  }
  try {
    const result = await validateWorkflow(buildPayload())
    validationErrors.value = result.errors
    if (result.valid) {
      ElMessage.success('校验通过')
    } else {
      ElMessage.warning(`校验发现 ${result.errors.length} 个问题`)
    }
  } catch {
    /* 已提示 */
  }
}

async function handleSave() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写工作流名称')
    return
  }
  saving.value = true
  try {
    if (workflowId.value) {
      await saveDraft(workflowId.value, buildPayload())
      ElMessage.success('草稿已保存')
      await load()
    } else {
      const created = await createWorkflow(buildPayload())
      ElMessage.success('创建成功')
      await router.replace(`/workflows/${created.id}/edit`)
    }
  } catch {
    /* 已提示 */
  } finally {
    saving.value = false
  }
}

async function handlePublish() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写工作流名称')
    return
  }
  publishing.value = true
  try {
    // 发布前先落盘草稿：用户可能刚改完节点就直接点了发布
    let target: number
    if (workflowId.value !== null) {
      await saveDraft(workflowId.value, buildPayload())
      target = workflowId.value
    } else {
      const created = await createWorkflow(buildPayload())
      target = created.id
    }

    const result = await publishWorkflow(target)
    ElMessage.success(`发布成功，版本 v${result.versionNo}`)
    if (!workflowId.value) {
      await router.replace(`/workflows/${target}/edit`)
    }
    await load()
  } catch {
    /* 校验失败原因由后端返回，拦截器已提示 */
  } finally {
    publishing.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div v-loading="loading">
    <div class="page-header">
      <div>
        <h2 class="page-title">{{ workflowId ? '编辑工作流' : '新建工作流' }}</h2>
        <p class="page-subtitle">
          节点用表单配置，依赖用「起点 → 终点」表达；条件节点的两条出边要分别标记 TRUE / FALSE
        </p>
      </div>
      <div>
        <el-button @click="router.push('/workflows')">返回列表</el-button>
        <el-button @click="handleValidate">校验</el-button>
        <el-button :loading="saving" @click="handleSave">保存草稿</el-button>
        <el-button type="primary" :loading="publishing" @click="handlePublish">发布</el-button>
      </div>
    </div>

    <div v-if="validationErrors.length" class="panel error-panel">
      <div class="error-title">校验未通过</div>
      <ul class="error-list">
        <li v-for="(error, index) in validationErrors" :key="index">{{ error }}</li>
      </ul>
    </div>

    <div class="panel">
      <el-form label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="64" placeholder="例如：每日数据同步" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="512" />
        </el-form-item>
      </el-form>
    </div>

    <div class="panel section">
      <div class="section-head">
        <div>
          <h3>节点</h3>
          <span class="muted">共 {{ nodes.length }} 个</span>
        </div>
        <el-button type="primary" plain @click="openCreateNode">添加节点</el-button>
      </div>

      <el-table :data="nodes" empty-text="还没有节点">
        <el-table-column prop="nodeKey" label="Key" width="140">
          <template #default="{ row }"><span class="mono">{{ row.nodeKey }}</span></template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ NODE_TYPE_TEXT[row.nodeType as NodeType] || row.nodeType }}</template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="重试" width="100">
          <template #default="{ row }">
            <span class="mono">{{ row.retryMax ?? 0 }} 次</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ $index }">
            <el-button text type="primary" @click="openEditNode($index)">编辑</el-button>
            <el-button text type="danger" @click="removeNode($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="panel section">
      <div class="section-head">
        <div>
          <h3>依赖关系</h3>
          <span class="muted">共 {{ edges.length }} 条</span>
        </div>
      </div>

      <div class="edge-form">
        <el-select v-model="newEdge.from" placeholder="起点节点" clearable style="width: 180px">
          <el-option v-for="node in nodes" :key="node.nodeKey" :label="`${node.nodeKey}（${node.name}）`" :value="node.nodeKey" />
        </el-select>
        <span class="arrow">→</span>
        <el-select v-model="newEdge.to" placeholder="终点节点" clearable style="width: 180px">
          <el-option v-for="node in nodes" :key="node.nodeKey" :label="`${node.nodeKey}（${node.name}）`" :value="node.nodeKey" />
        </el-select>
        <el-select
          v-if="conditionSourceSelected"
          v-model="newEdge.condition"
          placeholder="分支"
          style="width: 110px"
        >
          <el-option label="TRUE" value="TRUE" />
          <el-option label="FALSE" value="FALSE" />
        </el-select>
        <el-button type="primary" plain @click="addEdge">添加</el-button>
      </div>

      <el-table :data="edges" empty-text="还没有依赖关系">
        <el-table-column label="起点" width="180">
          <template #default="{ row }"><span class="mono">{{ row.fromNodeKey }}</span></template>
        </el-table-column>
        <el-table-column label="" width="60">
          <template #default>→</template>
        </el-table-column>
        <el-table-column label="终点" width="180">
          <template #default="{ row }"><span class="mono">{{ row.toNodeKey }}</span></template>
        </el-table-column>
        <el-table-column label="分支条件" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.conditionValue" size="small" effect="plain">{{ row.conditionValue }}</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ $index }">
            <el-button text type="danger" @click="removeEdge($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="detail && detail.versions.length" class="panel section">
      <div class="section-head">
        <h3>版本历史</h3>
      </div>
      <el-timeline>
        <el-timeline-item
          v-for="version in detail.versions"
          :key="version.id"
          :timestamp="(version.publishedAt || '').replace('T', ' ').slice(0, 19)"
        >
          v{{ version.versionNo }} · <span class="mono muted">{{ (version.contentHash || '').slice(0, 12) }}</span>
        </el-timeline-item>
      </el-timeline>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingIndex >= 0 ? '编辑节点' : '添加节点'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="节点 Key" required>
          <el-input v-model="nodeForm.nodeKey" placeholder="字母、数字、下划线" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="nodeForm.nodeType" style="width: 100%">
            <el-option label="HTTP 请求" value="HTTP" />
            <el-option label="条件判断" value="CONDITION" />
            <el-option label="模拟业务" value="MOCK_TASK" />
            <el-option label="发送通知" value="NOTIFY" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="nodeForm.name" placeholder="默认与 key 一致" />
        </el-form-item>

        <template v-if="nodeForm.nodeType === 'HTTP'">
          <el-form-item label="URL" required>
            <el-input v-model="nodeForm.url" placeholder="https://example.com/api" />
          </el-form-item>
          <el-form-item label="方法">
            <el-select v-model="nodeForm.method" style="width: 100%">
              <el-option v-for="method in ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']" :key="method" :label="method" :value="method" />
            </el-select>
          </el-form-item>
          <el-form-item label="期望状态码">
            <el-input v-model="nodeForm.expectStatus" placeholder="逗号分隔，如 200,201" />
          </el-form-item>
          <el-form-item label="超时(ms)">
            <el-input-number v-model="nodeForm.timeoutMs" :min="100" :max="60000" :step="500" />
          </el-form-item>
        </template>

        <template v-if="nodeForm.nodeType === 'CONDITION'">
          <el-form-item label="取值字段" required>
            <el-input v-model="nodeForm.field" placeholder="如 body.status，从上游节点输出中取值" />
          </el-form-item>
          <el-form-item label="操作符" required>
            <el-select v-model="nodeForm.operator" style="width: 100%">
              <el-option v-for="op in OPERATORS" :key="op" :label="op" :value="op" />
            </el-select>
          </el-form-item>
          <el-form-item label="比较值">
            <el-input v-model="nodeForm.value" placeholder="EQ/NE/GT 等需要填写" />
          </el-form-item>
        </template>

        <template v-if="nodeForm.nodeType === 'MOCK_TASK'">
          <el-form-item label="耗时(ms)">
            <el-input-number v-model="nodeForm.durationMs" :min="0" :max="60000" :step="100" />
          </el-form-item>
          <el-form-item label="失败率">
            <el-input-number v-model="nodeForm.failRate" :min="0" :max="1" :step="0.1" :precision="2" />
            <span class="muted hint">用于演示失败重试</span>
          </el-form-item>
        </template>

        <template v-if="nodeForm.nodeType === 'NOTIFY'">
          <el-form-item label="标题" required>
            <el-input v-model="nodeForm.title" />
          </el-form-item>
          <el-form-item label="内容">
            <el-input v-model="nodeForm.content" type="textarea" :rows="2" />
          </el-form-item>
        </template>

        <el-form-item label="最大重试">
          <el-input-number v-model="nodeForm.retryMax" :min="0" :max="10" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveNode">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.section {
  margin-top: 16px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-head h3 {
  margin: 0;
  font-size: 15px;
  display: inline-block;
  margin-right: 8px;
}

.edge-form {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.arrow {
  color: var(--ff-muted);
}

.hint {
  margin-left: 8px;
  font-size: 12px;
}

.error-panel {
  margin-top: 16px;
  border-color: #f5c6cb;
  background: #fff5f5;
}

.error-title {
  font-weight: 600;
  color: #c0392b;
  margin-bottom: 8px;
}

.error-list {
  margin: 0;
  padding-left: 20px;
  color: #c0392b;
  font-size: 13px;
}
</style>
