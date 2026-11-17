<script setup lang="ts">
// 设置页：运行配置说明（只读展示；secret 一律走后端环境变量，不进前端包）
</script>

<template>
  <div>
    <el-card>
      <template #header>运行配置说明（只读）</template>
      <p>· 所有 secret（DB/Redis/微信/AI Provider）仅存在于后端环境变量与 .env，不进入本前端包。</p>
      <p>· AI Provider：AI_TEXT_PROVIDER / AI_AUDIO_PROVIDER（mock 可完整演示）。</p>
      <p>· 限流：Goal 30/时、追问 20/时、生成 10/日、重试音频 5/日/故事（§24，可用环境变量调整）。</p>
      <p>· 数据保留：已删除故事音频保留 30 天后由每日任务物理清理（§25.2）。</p>
      <p>· 管理员：bootstrap 自 ADMIN_BOOTSTRAP_USERNAME/PASSWORD；修改密码需重建 admin_user 行。</p>
    </el-card>
    <el-card style="margin-top: 16px">
      <template #header>排障入口</template>
      <p>· 失败任务：任务页按状态 FAILED 过滤 → 查看错误阶段/错误码 → 手动重试。</p>
      <p>· 一次失败生成的完整链路：AI 日志页按 requestId 查询 TEXT/AUDIO/MODERATION 全部调用。</p>
    </el-card>
  </div>
</template>
