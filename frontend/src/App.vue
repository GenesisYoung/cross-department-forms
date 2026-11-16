<template>
  <el-config-provider :locale="elementLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup>
import { computed } from 'vue'
import en from 'element-plus/es/locale/lang/en'
import zhTw from 'element-plus/es/locale/lang/zh-tw'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { locale } from './i18n.js'

const elementLocales = { en, 'zh-TW': zhTw, 'zh-CN': zhCn }
const elementLocale = computed(() => elementLocales[locale.value] ?? en)
</script>
