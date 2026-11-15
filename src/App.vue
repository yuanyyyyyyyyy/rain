<template>
  <SiteHeader />
  <main>
    <RouterView />
  </main>
  <SiteFooter />
  <!-- AI 分身（2026-11-15 P0）：全路由悬浮 dock；enabled=false 或 Worker AGENT_ON="0" 双闸下线 -->
  <AgentDock v-if="agentConfig.enabled" />
</template>

<script setup>
import SiteHeader from './components/layout/SiteHeader.vue'
import SiteFooter from './components/layout/SiteFooter.vue'
import AgentDock from './components/agent/AgentDock.vue'
import { agentConfig } from './data/agentConfig.js'
</script>
