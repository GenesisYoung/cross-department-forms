<template>
  <el-select :model-value="theme" @update:model-value="setTheme" :aria-label="tr('Theme')" class="theme-select">
    <el-option :label="tr('White theme')" value="light" />
    <el-option :label="tr('Dark theme')" value="dark" />
    <el-option :label="tr('Follow system')" value="system" />
  </el-select>
</template>

<script setup>
import { theme, setTheme } from '../theme.js'
import { tr } from '../i18n.js'
</script>

<style scoped>
.theme-select { width: 140px; flex-shrink: 0; }
</style>
