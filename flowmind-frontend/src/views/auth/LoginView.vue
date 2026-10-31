<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { type FormInstance, type FormRules } from 'element-plus'
import { authAndAdminApi } from '@/api/authAndAdminApi'
import { useAuthStore } from '@/stores/authStore'
import './auth.css'
const auth = useAuthStore(), router = useRouter(), route = useRoute()
const form = ref<FormInstance>(), loading = ref(false), failed = ref(false)
const draft = reactive({ username: '', password: '' })
const rules: FormRules = { username: [{ required: true, message: '请输入账号', trigger: 'blur' }], password: [{ required: true, message: '请输入密码', trigger: 'blur' }] }
async function login() {
  if (loading.value || !(await form.value?.validate().catch(() => false))) return
  loading.value = true; failed.value = false
  try {
    const result = await authAndAdminApi.login(draft.username, draft.password)
    auth.setSession(result.token, result.userInfo)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    await router.replace(redirect.startsWith('/') && !redirect.startsWith('//') && !['/login', '/register', '/403'].includes(redirect.split('?')[0]) ? redirect : result.userInfo.roleType === 'ADMIN' ? '/admin/monitor' : '/workspace')
  } catch { failed.value = true } finally { loading.value = false }
}
async function demo(username: string) { draft.username = username; draft.password = '123456'; await login() }
onMounted(() => auth.clear())
</script>
<template><main class="auth-shell"><section class="auth-story"><div class="auth-logo"><span class="brand-mark">F</span>FlowMind 智流</div><div><p class="eyebrow">CONNECT IDEAS. BUILD INTELLIGENCE.</p><h1>让每一个想法，<br>连接成可能。</h1><p>从第一条连线开始，与团队共同构建智能工作流，让学习、探索与创造自然发生。</p><div class="auth-flow"><span>灵感输入</span>→<span>智能编排</span>→<span>价值输出</span></div></div><footer>高校 AI 工作流协同与实训平台</footer></section><section class="auth-content"><div class="auth-card"><p class="eyebrow">WELCOME BACK</p><h2>欢迎回到智流</h2><p class="subtitle">登录你的账号，继续未完成的好想法。</p><el-alert v-if="failed" title="登录未成功，请检查账号和密码后重试" type="error" :closable="false"/><el-form ref="form" :model="draft" :rules="rules" label-position="top" @submit.prevent="login"><el-form-item label="账号" prop="username"><el-input v-model="draft.username" autocomplete="username" placeholder="学号、工号或管理员账号" maxlength="64"/></el-form-item><el-form-item label="密码" prop="password"><el-input v-model="draft.password" type="password" show-password autocomplete="current-password" placeholder="请输入密码" maxlength="32"/></el-form-item><el-button class="auth-submit" type="primary" native-type="submit" :loading="loading">登录工作空间</el-button></el-form><div class="auth-links">还没有账号？<router-link to="/register"><el-text type="primary">创建账号</el-text></router-link></div><div class="auth-demos"><p>使用预置账号体验 · 默认密码 123456</p><div class="auth-demo-buttons"><el-button :disabled="loading" @click="demo('lijunhao')">学生演示登录</el-button><el-button :disabled="loading" @click="demo('teacher_litian')">教师演示登录</el-button><el-button :disabled="loading" @click="demo('admin')">管理员演示登录</el-button></div></div></div></section></main></template>
