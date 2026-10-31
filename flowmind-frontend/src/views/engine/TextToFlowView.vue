<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { engineApi } from '@/api/engineAndMarketApi'
import { categories } from '@/api/types'
import './engine.css'
const router = useRouter(), prompt = ref(''), category = ref('CODE_ASSIST'), busy = ref(false), error = ref('')
const examples = ['输入 Java 代码，检索开发规约并生成安全审查报告', '清洗输入文本，如果包含 Java 则检索知识库并回答，否则给出通用建议', '根据本周工作事项生成结构化周报']
async function generate() {
  if (!prompt.value.trim()) { error.value = '请描述你希望工作流完成的任务'; return }
  busy.value = true; error.value = ''
  try { const detail = await engineApi.generate(prompt.value.trim(),category.value); await router.push(`/workflow/editor/${detail.workflow.id}`) }
  catch (e) { error.value = e instanceof Error ? e.message : '生成失败，请重试' } finally { busy.value = false }
}
</script>
<template><div class="engine-heading"><div><span class="eyebrow">TEXT TO FLOW / 一句话生成</span><h1>把想法变成工作流</h1><p>描述输入、处理步骤和预期结果，自动生成可编辑的节点与连线。</p></div></div><section class="engine-panel"><el-form label-position="top" @submit.prevent="generate"><el-form-item label="任务分类"><el-select v-model="category" style="max-width:260px"><el-option v-for="c in categories" :key="c.value" :value="c.value" :label="c.label"/></el-select></el-form-item><el-form-item label="工作流需求"><el-input v-model="prompt" type="textarea" :rows="7" maxlength="1000" show-word-limit placeholder="例如：先清洗用户输入，判断是否为 Java 问题，检索知识库后调用模型生成解答。" :disabled="busy"/></el-form-item><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-button type="primary" native-type="submit" :loading="busy">生成并打开画布</el-button></el-form></section><section class="engine-panel"><h3>试试这些需求</h3><div class="engine-actions"><el-button v-for="example in examples" :key="example" :disabled="busy" @click="prompt = example">{{ example }}</el-button></div><p class="engine-hint">支持知识检索、文本清洗、条件分支及双路模型回答。生成后可在画布中调整条件值与提示词。</p></section></template>
