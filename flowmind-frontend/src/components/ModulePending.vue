<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
</script>
<template><main class="pending-module"><div class="brand-mark">F</div><p class="eyebrow">FLOWMIND · 智流</p><h1>{{ route.meta.title }}</h1><p>此模块尚未接入当前版本。</p><el-button @click="$router.push('/workspace')">返回工作台</el-button></main></template>
