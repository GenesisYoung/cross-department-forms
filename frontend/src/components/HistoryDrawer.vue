<template>
  <el-drawer :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" size="460px" @open="load">
    <template #header>
      <div>
        <div class="h-title">{{ cell ? tr("History of {0}", [ref_]) : tr("Change history") }}</div>
        <div class="h-sub">{{ tr("Every edit is recorded with who, when, and the previous value.") }}</div>
      </div>
    </template>
    <div v-loading="loading">
      <el-radio-group v-if="!cell" v-model="filter" size="small" class="filter">
        <el-radio-button value="all">{{ tr("All changes") }}</el-radio-button>
        <el-radio-button value="watched">{{ tr("Watched only") }}</el-radio-button>
      </el-radio-group>
      <el-empty v-if="!loading && !shown.length" :description="tr('No changes recorded yet')" />
      <el-timeline>
        <el-timeline-item v-for="ch in shown" :key="ch.id" :timestamp="formatTime(ch.changedAt)" :type="ch.watched ? 'warning' : 'primary'" placement="top">
          <div class="change" @click="$emit('jump', { r: ch.row, c: ch.col })">
            <div class="who">
              <b>{{ ch.userName }}</b>
              <span class="dept-pill" :style="deptStyle(ch.departmentCode)">{{ ch.departmentName }}</span>
              <el-tag v-if="ch.watched" size="small" type="warning" effect="plain">{{ tr("watched") }}</el-tag>
            </div>
            <div class="where">{{ ch.cellRef }} · {{ changeRowLabel(ch) }} · {{ ch.columnLabel }}</div>
            <div class="diff"><span class="old">{{ show(ch.oldValue) }}</span> → <span class="new">{{ show(ch.newValue) }}</span></div>
            <div v-if="ch.acknowledgements?.length" class="acks">
              <span v-for="a in ch.acknowledgements" :key="a.userName" class="ack" :class="{ done: a.acknowledgedAt }"
                    :title="a.acknowledgedAt ? tr('Acknowledged ') + formatTime(a.acknowledgedAt) : tr('Not yet acknowledged')">
                <el-icon><component :is="a.acknowledgedAt ? 'CircleCheckFilled' : 'Clock'" /></el-icon>{{ a.userName }}
              </span>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </div>
  </el-drawer>
</template>

<script setup>
import { changeRowLabel } from '../notificationText.js'
import { tr } from '../i18n.js'

import { computed, ref } from 'vue'
import api from '../api.js'
import { colName } from '../formula/engine.js'
import { deptStyle, formatTime, show } from '../utils.js'

const props = defineProps({ modelValue: Boolean, sheetId: Number, cell: Object })
defineEmits(['update:modelValue', 'jump'])
const items = ref([])
const loading = ref(false)
const filter = ref('all')
const ref_ = computed(() => (props.cell ? colName(props.cell.c) + (props.cell.r + 1) : ''))
const shown = computed(() => (filter.value === 'watched' && !props.cell ? items.value.filter((c) => c.watched) : items.value))

async function load() {
  loading.value = true
  try {
    items.value = props.cell
      ? await api.get(`/sheets/${props.sheetId}/cells/history`, { params: { row: props.cell.r, col: props.cell.c } })
      : await api.get(`/sheets/${props.sheetId}/history`, { params: { limit: 200 } })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.h-title { font-weight: 600; font-size: 16px; color: var(--text-1); }
.h-sub { font-size: 12px; color: var(--text-2); margin-top: 2px; }
.filter { margin-bottom: 16px; }
.change { cursor: pointer; padding: 8px 10px; border-radius: 8px; border: 1px solid var(--border); }
.change:hover { background: var(--hover); }
.who { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; font-size: 13px; }
.where { font-size: 12px; color: var(--text-2); margin: 4px 0; }
.diff { font-size: 13px; word-break: break-word; }
.old { color: var(--text-2); text-decoration: line-through; }
.new { font-weight: 600; }
.acks { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.ack { font-size: 11px; display: inline-flex; align-items: center; gap: 3px; color: var(--text-3); }
.ack.done { color: var(--ok); }
</style>
