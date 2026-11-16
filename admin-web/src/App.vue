<script setup lang="ts">
// Phase 0 骨架：仅导航占位；Phase 8 实现正式管理后台（§20）。
</script>

<template>
  <el-container class="layout">
    <el-header class="header">
      <span class="brand">Future Self 管理后台</span>
      <nav class="nav">
        <router-link to="/dashboard">Dashboard</router-link>
        <router-link to="/login">Login</router-link>
      </nav>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<style>
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", Arial, sans-serif;
}
.layout {
  min-height: 100vh;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #eee;
}
.brand {
  font-weight: 600;
}
.nav a {
  margin-left: 16px;
  color: #409eff;
  text-decoration: none;
}
</style>
