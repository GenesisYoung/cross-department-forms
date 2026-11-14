<template>
  <el-config-provider :locale="elementLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup>
import { computed } from 'vue'
import en from 'element-plus/es/locale/lang/en'
import zhTw from 'element-plus/es/locale/lang/zh-tw'
import { locale } from './i18n.js'

const elementLocale = computed(() => locale.value === 'zh-TW' ? zhTw : en)
</script>
