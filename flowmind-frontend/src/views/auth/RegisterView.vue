<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { authAndAdminApi, type Registration } from '@/api/authAndAdminApi'
import { useAuthStore } from '@/stores/authStore'
import './auth.css'
const router = useRouter(), auth = useAuthStore(), form = ref<FormInstance>(), loading = ref(false)
const draft = reactive<Registration & { confirmPassword: string }>({ username: '', password: '', confirmPassword: '', realName: '', roleType: 'STUDENT', deptClass: '', email: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入账号' }, { pattern: /^[a-zA-Z0-9_]{4,32}$/, message: '使用4～32位字母、数字或下划线', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码' }, { min: 6, max: 32, message: '密码长度为6～32位', trigger: 'blur' }],
  confirmPassword: [{ required: true, message: '请再次输入密码' }, { validator: (_rule, value, done) => value === draft.password ? done() : done(new Error('两次密码不一致')), trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名' }, { min: 2, max: 32, message: '姓名长度为2～32位', trigger: 'blur' }],
  deptClass: [{ required: true, whitespace: true, message: '请输入院系或班级' }], email: [{ type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }]
}
async function register() {
  if (loading.value || !(await form.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    const { confirmPassword: _confirm, ...input } = draft
    await authAndAdminApi.register(input); ElMessage.success('注册成功，请登录'); await router.replace('/login')
  } catch { /* Request layer displays the server validation message. */ } finally { loading.value = false }
}
onMounted(() => auth.clear())
</script>
<template><main class="auth-shell"><section class="auth-story"><div class="auth-logo"><span class="brand-mark">F</span>FlowMind 智流</div><div><p class="eyebrow">YOUR NEXT IDEA STARTS HERE</p><h1>一起探索，<br>一起创造。</h1><p>连接同伴的灵感，将知识、模型与代码编排成属于你的智能工作流。</p><div class="auth-flow"><span>协同创作</span>→<span>实训探索</span>→<span>共同成长</span></div></div><footer>高校 AI 工作流协同与实训平台</footer></section><section class="auth-content"><div class="auth-card"><p class="eyebrow">JOIN FLOWMIND</p><h2>创建你的智流账号</h2><p class="subtitle">填写基本信息，开启智能协作之旅。</p><el-form ref="form" :model="draft" :rules="rules" label-position="top" @submit.prevent="register"><el-form-item label="账号" prop="username"><el-input v-model="draft.username" autocomplete="username" maxlength="32" placeholder="4～32位字母、数字或下划线"/></el-form-item><el-form-item label="真实姓名" prop="realName"><el-input v-model="draft.realName" autocomplete="name" maxlength="32"/></el-form-item><el-form-item label="身份" prop="roleType"><el-radio-group v-model="draft.roleType"><el-radio-button value="STUDENT">学生</el-radio-button><el-radio-button value="TEACHER">教师</el-radio-button></el-radio-group></el-form-item><el-form-item label="所属院系 / 班级" prop="deptClass"><el-input v-model="draft.deptClass" maxlength="100" placeholder="例如：计科1班"/></el-form-item><el-form-item label="邮箱（选填）" prop="email"><el-input v-model="draft.email" autocomplete="email" maxlength="128"/></el-form-item><el-form-item label="密码" prop="password"><el-input v-model="draft.password" type="password" show-password autocomplete="new-password" maxlength="32" placeholder="6～32位密码"/></el-form-item><el-form-item label="确认密码" prop="confirmPassword"><el-input v-model="draft.confirmPassword" type="password" show-password autocomplete="new-password" maxlength="32"/></el-form-item><el-button type="primary" native-type="submit" class="auth-submit" :loading="loading">注册账号</el-button></el-form><div class="auth-links">已有账号？<router-link to="/login"><el-text type="primary">返回登录</el-text></router-link></div></div></section></main></template>
