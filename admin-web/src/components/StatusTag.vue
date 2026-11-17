<script setup lang="ts">
import { computed } from "vue";
import type { TagType } from "../utils/labels";

/** 通用状态标签：value + kind 自动映射中文与颜色 */
const props = defineProps<{
  label: string;
  type?: TagType;
  effect?: "dark" | "light" | "plain";
}>();

const tagType = computed(() => props.type || "info");
const tagEffect = computed(() => props.effect || "light");
</script>

<template>
  <el-tag :type="tagType" :effect="tagEffect" size="small" disable-transitions>
    {{ label }}
  </el-tag>
</template>
