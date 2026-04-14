<script setup lang="ts">
import { NConfigProvider, NDialogProvider, NMessageProvider, lightTheme, zhCN } from 'naive-ui'
</script>

<template>
  <n-config-provider class="app-cfg" :theme="lightTheme" :locale="zhCN">
    <n-message-provider>
      <!-- useDialog() 必须在此 Provider 内，否则 ChatView 会在 setup 阶段抛错导致整页空白 -->
      <n-dialog-provider>
        <div class="app-shell">
          <router-view />
        </div>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<style scoped>
.app-cfg {
  flex: 1;
  min-height: 0;
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
}
.app-shell {
  flex: 1;
  min-height: 0;
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.app-shell > * {
  flex: 1;
  min-height: 0;
  min-width: 0;
}
</style>
