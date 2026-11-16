<template>
  <el-select :model-value="locale" @update:model-value="setLocale" :aria-label="tr('Language')" class="language-select">
    <el-option label="English" value="en" />
    <el-option label="简体中文" value="zh-CN" />
    <el-option label="繁體中文" value="zh-TW" />
  </el-select>
</template>

<script setup>
import { locale, setLocale, tr } from '../i18n.js'
</script>

<style scoped>
.language-select { width: 124px; flex-shrink: 0; }
</style>
