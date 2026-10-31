<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { workflowApi } from '@/api/workflowApi'
import { useAuthStore } from '@/stores/authStore'
import { useCanvasStore } from '@/stores/canvasStore'
import type { Workflow } from '@/api/types'
const props = defineProps<{ workflowId?: number }>()
const open = defineModel<boolean>({ default: true })
const route = useRoute(), router = useRouter(), auth = useAuthStore(), canvas = useCanvasStore()
const id = computed(() => props.workflowId ?? Number(route.params.id))
const standalone = computed(() => props.workflowId === undefined)
const workflow = ref<Workflow>(), ids = ref<number[]>([]), invite = ref(''), loading = ref(false), saving = ref(false), failed = ref(false)
const isOwner = computed(() => workflow.value?.creatorId === auth.user?.id)
const members = computed(() => [...new Set([workflow.value?.creatorId, ...ids.value])].filter((v): v is number => v !== undefined).map(userId => ({ userId, owner: userId === workflow.value?.creatorId, online: canvas.workflow?.id === id.value ? canvas.members.find(m => m.userId === userId) : undefined })))
async function load() {
  loading.value = true; failed.value = false
  try { workflow.value = (await workflowApi.detail(id.value)).workflow; ids.value = [...(workflow.value.collaboratorIds ?? [])] } catch { failed.value = true } finally { loading.value = false }
}
watch(id, load, { immediate: true })
function add() {
  const value = Number(invite.value.trim())
  if (!Number.isSafeInteger(value) || value <= 0) { ElMessage.warning('请输入有效的用户 ID'); return }
  if (ids.value.includes(value) || value === workflow.value?.creatorId) { ElMessage.info('该用户已在协作列表中'); return }
  if (ids.value.length >= 50) { ElMessage.warning('协作成员最多50人'); return }
  ids.value.push(value); invite.value = ''
}
async function save() {
  saving.value = true
  try { await workflowApi.collaborators(id.value, ids.value); ElMessage.success('协作成员已更新'); open.value = false } finally { saving.value = false }
}
function closed() { if (standalone.value) void router.push(`/workflow/editor/${id.value}`) }
</script>
<template><el-dialog v-model="open" title="一起，让好想法发生" width="540px" @closed="closed"><div v-loading="loading"><p class="subtitle">{{ workflow?.wfName }} · 协作空间</p><el-alert v-if="failed" type="error" title="成员加载失败" :closable="false"><el-button text @click="load">重试</el-button></el-alert><el-form v-if="isOwner" inline @submit.prevent="add"><el-form-item><el-input v-model="invite" placeholder="输入要邀请的用户 ID" @keyup.enter="add"/></el-form-item><el-form-item><el-button type="primary" @click="add">添加成员</el-button></el-form-item></el-form><el-table :data="members" empty-text="暂无协作成员"><el-table-column label="成员"><template #default="{ row }"><span class="member-dot" :style="{ background: row.online?.color ?? '#94a69b' }"/>{{ row.owner ? workflow?.creatorName : (row.online?.realName ?? `用户 #${row.userId}`) }} <small class="muted">#{{ row.userId }}</small></template></el-table-column><el-table-column label="角色" width="90"><template #default="{ row }">{{ row.owner ? '创建者' : '协作者' }}</template></el-table-column><el-table-column label="在线状态" width="100"><template #default="{ row }"><el-tag :type="row.online ? 'success' : 'info'" size="small" effect="plain">{{ row.online ? '在线' : '未连接' }}</el-tag></template></el-table-column><el-table-column v-if="isOwner" label="操作" width="70"><template #default="{ row }"><el-button v-if="!row.owner" link type="danger" @click="ids = ids.filter(v => v !== row.userId)">移除</el-button></template></el-table-column></el-table><p class="muted collab-note">协作者可以编辑画布和管理版本。只有创建者可以管理成员、调整公开状态与删除工作流。</p></div><template #footer><el-button @click="open = false">关闭</el-button><el-button v-if="isOwner" type="primary" :loading="saving" :disabled="loading || failed" @click="save">保存成员</el-button></template></el-dialog></template>
