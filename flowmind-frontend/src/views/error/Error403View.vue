<script setup lang="ts">
import { useAuthStore } from '@/stores/authStore'
const auth = useAuthStore()
</script>
<template><main class="forbidden"><div class="forbidden-card"><span class="brand-mark">F</span><p class="eyebrow">ACCESS RESTRICTED</p><strong>403</strong><h1>这里需要更多权限</h1><p>当前账号无法访问此页面。你可以返回工作台，<br>或联系管理员确认账号角色。</p><div><router-link :to="auth.token ? '/workspace' : '/login'"><el-button type="primary" size="large">{{ auth.token ? '返回工作台' : '返回登录' }}</el-button></router-link><router-link v-if="auth.token" to="/login"><el-button size="large">切换账号</el-button></router-link></div></div></main></template>
<style scoped>.forbidden{min-height:100vh;display:grid;place-items:center;background:#f5f7f4;padding:30px}.forbidden-card{text-align:center;max-width:560px}.eyebrow{margin-top:28px}.forbidden strong{font-size:120px;font-weight:600;color:#b5cbbb;line-height:1.3;letter-spacing:8px}.forbidden h1{font-size:28px}.forbidden p:not(.eyebrow){font-size:14px;color:#809083;line-height:2;margin:16px 0 30px}.forbidden-card>div{display:flex;justify-content:center;gap:12px}</style>
