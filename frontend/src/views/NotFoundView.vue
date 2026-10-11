<template>
  <div class="auth-page">
    <el-result icon="warning" title="404" sub-title="页面不存在">
      <template #extra>
        <router-link :to="{ name: 'home' }">
          <el-button type="primary">回首页</el-button>
        </router-link>
      </template>
    </el-result>
  </div>
</template>
