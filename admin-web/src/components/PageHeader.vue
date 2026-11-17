<script setup lang="ts">
/** 页面顶部标题区：标题 + 描述 + 右侧操作插槽 */
defineProps<{
  title: string;
  subtitle?: string;
}>();
</script>

<template>
  <div class="page-header">
    <div class="titles">
      <h2 class="title">{{ title }}</h2>
      <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
    </div>
    <div class="actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 16px;
}
.title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
  line-height: 1.3;
}
.subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
}
.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
</style>
