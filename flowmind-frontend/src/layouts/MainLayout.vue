<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { Grid, Connection, Collection, Reading, DataAnalysis, Setting, SwitchButton, MagicStick } from '@element-plus/icons-vue'
const auth = useAuthStore(), route = useRoute(), router = useRouter()
const role = computed(() => auth.user?.roleType)
async function logout() { await auth.logout(); await router.push('/login') }
</script>
<template><div class="app-shell"><aside class="sidebar"><router-link class="brand" to="/workspace"><span class="brand-mark">F</span><div>FlowMind<small>智流 · 让想法自由连接</small></div></router-link>
<div class="workspace-label">WORKSPACE <span>个人空间</span></div><el-menu :default-active="route.path" router><el-menu-item index="/workspace"><el-icon><Grid /></el-icon>工作台</el-menu-item><el-menu-item index="/workflows"><el-icon><Connection /></el-icon>我的工作流</el-menu-item><el-menu-item index="/market"><el-icon><Collection /></el-icon>模板广场</el-menu-item><el-menu-item index="/engine/text-to-flow"><el-icon><MagicStick /></el-icon>一句话生成</el-menu-item><el-menu-item index="/engine/records"><el-icon><DataAnalysis /></el-icon>运行记录</el-menu-item>
<el-menu-item v-if="role === 'STUDENT'" index="/edu/tasks"><el-icon><Reading /></el-icon>我的实训</el-menu-item><template v-if="role === 'TEACHER' || role === 'ADMIN'"><el-menu-item index="/edu/manage">课题管理</el-menu-item><el-menu-item index="/edu/grades">作业批改</el-menu-item><el-menu-item index="/edu/analytics">学情分析</el-menu-item><el-menu-item index="/admin/monitor">全局监控</el-menu-item></template><template v-if="role === 'ADMIN'"><el-menu-item index="/admin/users"><el-icon><Setting /></el-icon>用户与权限</el-menu-item><el-menu-item index="/admin/audit">模型与审计</el-menu-item></template></el-menu>
<div class="sidebar-bottom"><span class="status-dot" />创作，由你掌控<span class="version">v1.0</span></div></aside>
<div class="main-column"><header class="topbar"><div><span class="muted">工作空间</span><span class="breadcrumb-slash">/</span>{{ route.meta.title }}</div><div class="topbar-right"><span class="quota-pill">✦ {{ (auth.user?.tokenQuota ?? 0).toLocaleString() }} Tokens</span><span class="avatar">{{ auth.user?.realName?.slice(0, 1) ?? 'F' }}</span><span>{{ auth.user?.realName ?? '访客' }}</span><el-button v-if="auth.token" text :icon="SwitchButton" @click="logout">退出</el-button></div></header><main class="main-content"><router-view /></main><footer class="footer">FlowMind 智流 <span>连接灵感，构建智能工作流</span></footer></div></div></template>
