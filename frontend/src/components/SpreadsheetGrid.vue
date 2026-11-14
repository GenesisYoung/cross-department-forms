<template>
  <div
    ref="scroller"
    class="grid-scroller"
    @scroll="onScroll"
    @mousedown.self="focusInput"
  >
    <table class="grid" :style="{ width: totalWidth + 'px' }" @contextmenu.prevent="onContextMenu">
      <colgroup>
        <col :style="{ width: ROWNUM_W + 'px' }" />
        <col v-for="c in colCount" :key="c" :style="{ width: colWidth(c - 1) + 'px' }" />
      </colgroup>
      <thead>
        <tr class="letters">
          <th class="corner" @mousedown.prevent="selectAll"></th>
          <th
            v-for="c in colCount"
            :key="c"
            :class="{ sel: inSelCol(c - 1) }"
            @mousedown.prevent="onColHeaderDown($event, c - 1)"
          >
            <span class="letter">{{ colName(c - 1) }}</span>
            <el-icon v-if="columns[c - 1]?.watched" class="col-flag watch" title="Watched: changes notify every department"><Bell /></el-icon>
            <el-tooltip v-if="columns[c - 1]?.editDepartments?.length" :content="'Editable by: ' + columns[c - 1].editDepartments.join(', ')" placement="top">
              <el-icon class="col-flag lock"><Lock /></el-icon>
            </el-tooltip>
            <span class="resizer" @mousedown.stop.prevent="startResize($event, c - 1)"></span>
          </th>
        </tr>
        <tr v-for="r in frozenRows" :key="'h' + r" class="frozen">
          <th class="rownum" :class="{ sel: inSelRow(r - 1) }" :style="{ top: (LETTERS_H + (r - 1) * ROW_H) + 'px' }"
              @mousedown.prevent="onRowHeaderDown($event, r - 1)">{{ r }}</th>
          <td v-for="c in colCount" :key="c" v-bind="cellAttrs(r - 1, c - 1)"
              :style="[cellStyle(r - 1, c - 1), { top: (LETTERS_H + (r - 1) * ROW_H) + 'px' }]"
              @mousedown="onCellDown($event, r - 1, c - 1)" @mouseenter="onCellEnter(r - 1, c - 1)" @dblclick="startEdit()">
            {{ text(r - 1, c - 1) }}
          </td>
        </tr>
      </thead>
      <tbody>
        <tr v-if="topPad > 0" :style="{ height: topPad + 'px' }"><td :colspan="colCount + 1" class="pad"></td></tr>
        <tr v-for="r in visibleRows" :key="r">
          <th class="rownum" :class="{ sel: inSelRow(r) }" @mousedown.prevent="onRowHeaderDown($event, r)">{{ r + 1 }}</th>
          <td v-for="c in colCount" :key="c" v-bind="cellAttrs(r, c - 1)" :style="cellStyle(r, c - 1)"
              @mousedown="onCellDown($event, r, c - 1)" @mouseenter="onCellEnter(r, c - 1)" @dblclick="startEdit()">
            {{ text(r, c - 1) }}
          </td>
        </tr>
        <tr v-if="bottomPad > 0" :style="{ height: bottomPad + 'px' }"><td :colspan="colCount + 1" class="pad"></td></tr>
      </tbody>
    </table>

    <!-- selection rectangle -->
    <div class="sel-box" :style="selBoxStyle"></div>

    <!-- The editor input is always focused (invisible until editing) so typing and IME input start an edit naturally. -->
    <textarea
      ref="input"
      class="cell-input"
      :class="{ editing }"
      :style="inputStyle"
      v-model="draft"
      spellcheck="false"
      @keydown="onKeyDown"
      @input="onInput"
      @compositionstart="onCompositionStart"
      @copy="onCopy"
      @cut="onCut"
      @paste="onPaste"
      @blur="onBlur"
    ></textarea>

    <div v-if="menu.show" class="ctx-menu" :style="{ left: menu.x + 'px', top: menu.y + 'px' }" @mousedown.stop>
      <div v-for="item in menuItems" :key="item.key" class="ctx-item" :class="{ divider: item.divider, disabled: item.disabled }"
           @click="!item.disabled && !item.divider && menuAction(item.key)">
        {{ item.label }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { colName, shiftFormula } from '../formula/engine.js'

const props = defineProps({
  rowCount: { type: Number, required: true },
  colCount: { type: Number, required: true },
  headerRows: { type: Number, default: 1 },
  columns: { type: Array, default: () => [] }, // index -> { title, width, watched, editDepartments }
  getRaw: { type: Function, required: true }, // (r,c) => string|null
  getStyle: { type: Function, required: true }, // (r,c) => object
  getDisplay: { type: Function, required: true }, // (r,c) => { text, kind }
  canEdit: { type: Function, required: true }, // (r,c) => null | reason
  flashes: { type: Object, default: () => ({}) }, // "r,c" -> { by }
  renderKey: { type: Number, default: 0 }, // bump to force re-render after data changes
  canStructure: { type: Object, default: () => ({ rows: false, cols: false }) }
})
const emit = defineEmits(['edit', 'select', 'resize-col', 'resize-col-preview', 'action'])

const ROW_H = 26
const LETTERS_H = 24
const ROWNUM_W = 48
const DEFAULT_W = 110
const BUFFER = 12

const scroller = ref(null)
const input = ref(null)
const scrollTop = ref(0)
const viewH = ref(600)
const widthOverride = reactive({})

const sel = reactive({ r: 0, c: 0, r2: 0, c2: 0 })
const editing = ref(false)
const draft = ref('')
const editOrigin = ref('') // 'type' (arrow keys commit+move) or 'dbl' (arrow keys move caret)
let dragging = false
let composing = false

// ------------------------------------------------------------------ geometry
const colWidth = (c) => widthOverride[c] ?? props.columns[c]?.width ?? DEFAULT_W
const colOffsets = computed(() => {
  const out = [ROWNUM_W]
  for (let c = 0; c < props.colCount; c++) out.push(out[c] + colWidth(c))
  return out
})
const totalWidth = computed(() => colOffsets.value[props.colCount])
const frozenRows = computed(() => Math.min(props.headerRows, props.rowCount))
const firstBody = computed(() => frozenRows.value)
const startRow = computed(() => Math.max(firstBody.value, Math.floor(scrollTop.value / ROW_H) - BUFFER + firstBody.value))
const endRow = computed(() => Math.min(props.rowCount, startRow.value + Math.ceil(viewH.value / ROW_H) + BUFFER * 2))
const visibleRows = computed(() => {
  void props.renderKey
  const out = []
  for (let r = startRow.value; r < endRow.value; r++) out.push(r)
  return out
})
const topPad = computed(() => (startRow.value - firstBody.value) * ROW_H)
const bottomPad = computed(() => (props.rowCount - endRow.value) * ROW_H)

function rowTop(r) {
  if (r < frozenRows.value) return scrollTop.value + LETTERS_H + r * ROW_H
  return LETTERS_H + r * ROW_H
}

const range = computed(() => ({
  r1: Math.min(sel.r, sel.r2), r2: Math.max(sel.r, sel.r2),
  c1: Math.min(sel.c, sel.c2), c2: Math.max(sel.c, sel.c2)
}))

const selBoxStyle = computed(() => {
  const { r1, r2, c1, c2 } = range.value
  const top = rowTop(r1)
  const bottom = rowTop(r2) + ROW_H
  return {
    left: colOffsets.value[c1] - 1 + 'px',
    top: top - 1 + 'px',
    width: colOffsets.value[c2 + 1] - colOffsets.value[c1] + 1 + 'px',
    height: bottom - top + 1 + 'px'
  }
})

const inputStyle = computed(() => {
  const base = {
    left: colOffsets.value[sel.c] + 'px',
    top: rowTop(sel.r) + 'px',
    minWidth: colWidth(sel.c) + 'px',
    height: ROW_H + 'px'
  }
  if (editing.value) {
    const len = Math.max(draft.value.length * 8 + 16, colWidth(sel.c))
    base.width = Math.min(len, 600) + 'px'
  } else base.width = colWidth(sel.c) + 'px'
  return base
})

// ------------------------------------------------------------------ rendering helpers
function text(r, c) {
  void props.renderKey
  return props.getDisplay(r, c).text
}
function cellAttrs(r, c) {
  void props.renderKey
  const d = props.getDisplay(r, c)
  const key = r + ',' + c
  const flash = props.flashes[key]
  const cls = ['cell', 'k-' + d.kind]
  if (r < props.headerRows) cls.push('hdr')
  if (props.canEdit(r, c)) cls.push('ro')
  if (flash) cls.push('flash')
  if (r >= range.value.r1 && r <= range.value.r2 && c >= range.value.c1 && c <= range.value.c2 && !(r === sel.r && c === sel.c)) cls.push('in-sel')
  return { class: cls, title: flash ? `Changed just now by ${flash.by}` : (d.text.length > 18 ? d.text : null) }
}
function cellStyle(r, c) {
  const s = props.getStyle(r, c)
  if (!s) return null
  const out = {}
  if (s.b) out.fontWeight = '600'
  if (s.i) out.fontStyle = 'italic'
  if (s.bg) {
    out.background = s.bg
    // Fills are absolute colours (like Excel); pick readable text when no text colour is set.
    if (!s.fc) out.color = isLight(s.bg) ? '#1f2329' : '#f3f5f8'
  }
  if (s.fc) out.color = s.fc
  if (s.al) out.textAlign = s.al
  return out
}
function isLight(hex) {
  const m = /^#?([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})/i.exec(hex || '')
  if (!m) return true
  const [r, g, b] = [m[1], m[2], m[3]].map((x) => parseInt(x, 16))
  return 0.299 * r + 0.587 * g + 0.114 * b > 150
}
const inSelCol = (c) => c >= range.value.c1 && c <= range.value.c2
const inSelRow = (r) => r >= range.value.r1 && r <= range.value.r2

// ------------------------------------------------------------------ selection
function setSel(r, c, extend = false) {
  r = Math.max(0, Math.min(props.rowCount - 1, r))
  c = Math.max(0, Math.min(props.colCount - 1, c))
  if (extend) { sel.r2 = r; sel.c2 = c } else { sel.r = r; sel.c = c; sel.r2 = r; sel.c2 = c }
  emit('select', { ...sel, ...range.value })
  ensureVisible(extend ? r : sel.r, extend ? c : sel.c)
}
function ensureVisible(r, c) {
  const el = scroller.value
  if (!el) return
  if (r >= frozenRows.value) {
    const top = LETTERS_H + r * ROW_H
    const frozenH = LETTERS_H + frozenRows.value * ROW_H
    if (top - frozenH < el.scrollTop + LETTERS_H) el.scrollTop = Math.max(0, top - frozenH)
    else if (top + ROW_H > el.scrollTop + el.clientHeight) el.scrollTop = top + ROW_H - el.clientHeight
  }
  const left = colOffsets.value[c]
  const right = colOffsets.value[c + 1]
  if (left - ROWNUM_W < el.scrollLeft) el.scrollLeft = left - ROWNUM_W
  else if (right > el.scrollLeft + el.clientWidth) el.scrollLeft = right - el.clientWidth
}
function selectAll() {
  commitEdit()
  sel.r = 0; sel.c = 0; sel.r2 = props.rowCount - 1; sel.c2 = props.colCount - 1
  emit('select', { ...sel, ...range.value })
  focusInput()
}
function onColHeaderDown(e, c) {
  commitEdit()
  if (e.shiftKey) { sel.c2 = c; sel.r2 = props.rowCount - 1 } else { sel.r = 0; sel.c = c; sel.r2 = props.rowCount - 1; sel.c2 = c }
  emit('select', { ...sel, ...range.value })
  focusInput()
}
function onRowHeaderDown(e, r) {
  commitEdit()
  if (e.shiftKey) { sel.r2 = r; sel.c2 = props.colCount - 1 } else { sel.r = r; sel.c = 0; sel.r2 = r; sel.c2 = props.colCount - 1 }
  emit('select', { ...sel, ...range.value })
  focusInput()
}
function onCellDown(e, r, c) {
  if (e.button === 2 && r >= range.value.r1 && r <= range.value.r2 && c >= range.value.c1 && c <= range.value.c2) return
  if (editing.value && draft.value.startsWith('=') && editOrigin.value) {
    // Excel-style: clicking while typing a formula inserts a reference
    e.preventDefault()
    draft.value += colName(c) + (r + 1)
    nextTick(() => input.value?.focus())
    return
  }
  commitEdit()
  e.preventDefault()
  setSel(r, c, e.shiftKey)
  dragging = true
  focusInput()
}
function onCellEnter(r, c) {
  if (dragging) setSel(r, c, true)
}
const stopDrag = () => { dragging = false }

// ------------------------------------------------------------------ editing
function focusInput() {
  nextTick(() => input.value?.focus({ preventScroll: true }))
}
function startEdit(initial) {
  const reason = props.canEdit(sel.r, sel.c)
  if (reason) {
    ElMessage.warning(`Read-only: ${reason}`)
    return false
  }
  editing.value = true
  editOrigin.value = initial === undefined ? 'dbl' : 'type'
  draft.value = initial === undefined ? (props.getRaw(sel.r, sel.c) ?? '') : initial
  focusInput()
  return true
}
function commitEdit() {
  if (!editing.value) return
  editing.value = false
  const raw = props.getRaw(sel.r, sel.c) ?? ''
  if (draft.value !== raw) {
    emit('edit', [{ r: sel.r, c: sel.c, v: draft.value, s: props.getStyle(sel.r, sel.c) }])
  }
  draft.value = ''
}
function cancelEdit() {
  editing.value = false
  draft.value = ''
}
function onInput() {
  if (!editing.value && !composing) {
    const typed = draft.value
    if (!startEdit(typed)) draft.value = ''
  }
}
function onCompositionStart() {
  composing = true
  if (!editing.value) {
    if (props.canEdit(sel.r, sel.c)) { ElMessage.warning('Read-only cell'); return }
    editing.value = true
    editOrigin.value = 'type'
  }
  const end = () => { composing = false; input.value?.removeEventListener('compositionend', end) }
  input.value?.addEventListener('compositionend', end)
}
function onBlur() {
  // keep the edit if focus moved into the context menu; otherwise commit
  setTimeout(() => {
    if (document.activeElement !== input.value) commitEdit()
  }, 0)
}

function onKeyDown(e) {
  const mod = e.ctrlKey || e.metaKey
  if (editing.value) {
    if (composing || e.isComposing) return
    if (e.key === 'Enter' && !e.altKey) {
      e.preventDefault()
      commitEdit()
      setSel(sel.r + (e.shiftKey ? -1 : 1), sel.c)
    } else if (e.key === 'Enter' && e.altKey) {
      e.preventDefault()
      draft.value += '\n'
    } else if (e.key === 'Tab') {
      e.preventDefault()
      commitEdit()
      setSel(sel.r, sel.c + (e.shiftKey ? -1 : 1))
    } else if (e.key === 'Escape') {
      e.preventDefault()
      cancelEdit()
    } else if (editOrigin.value === 'type' && ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key)) {
      e.preventDefault()
      commitEdit()
      move(e.key, false)
    }
    return
  }
  // navigation mode
  if (mod && e.key.toLowerCase() === 'a') { e.preventDefault(); selectAll(); return }
  if (mod && ['z', 'y', 'b', 'i'].includes(e.key.toLowerCase())) {
    e.preventDefault()
    const k = e.key.toLowerCase()
    emit('action', k === 'z' ? (e.shiftKey ? 'redo' : 'undo') : k === 'y' ? 'redo' : k === 'b' ? 'bold' : 'italic')
    return
  }
  if (mod) return // let copy/cut/paste events fire
  switch (e.key) {
    case 'ArrowUp': case 'ArrowDown': case 'ArrowLeft': case 'ArrowRight':
      e.preventDefault(); move(e.key, e.shiftKey); return
    case 'Enter':
      e.preventDefault(); setSel(sel.r + (e.shiftKey ? -1 : 1), sel.c); return
    case 'Tab':
      e.preventDefault(); setSel(sel.r, sel.c + (e.shiftKey ? -1 : 1)); return
    case 'F2':
      e.preventDefault(); startEdit(); return
    case 'Delete': case 'Backspace':
      e.preventDefault(); clearSelection(); return
    case 'PageDown':
      e.preventDefault(); setSel(sel.r + Math.floor(viewH.value / ROW_H) - 2, sel.c, e.shiftKey); return
    case 'PageUp':
      e.preventDefault(); setSel(sel.r - Math.floor(viewH.value / ROW_H) + 2, sel.c, e.shiftKey); return
    case 'Home':
      e.preventDefault(); setSel(sel.r, 0, e.shiftKey); return
    case 'Escape':
      menu.show = false; return
  }
  // printable characters fall through into the textarea and trigger onInput -> startEdit
}
function move(key, extend) {
  const r = extend ? sel.r2 : sel.r
  const c = extend ? sel.c2 : sel.c
  const d = { ArrowUp: [-1, 0], ArrowDown: [1, 0], ArrowLeft: [0, -1], ArrowRight: [0, 1] }[key]
  setSel(r + d[0], c + d[1], extend)
}

function selectionCells() {
  const { r1, r2, c1, c2 } = range.value
  const out = []
  for (let r = r1; r <= r2; r++) for (let c = c1; c <= c2; c++) out.push([r, c])
  return out
}
function clearSelection() {
  const edits = []
  let blocked = 0
  for (const [r, c] of selectionCells()) {
    if (!props.getRaw(r, c)) continue
    if (props.canEdit(r, c)) { blocked++; continue }
    edits.push({ r, c, v: '', s: props.getStyle(r, c) })
  }
  if (blocked) ElMessage.warning(`${blocked} read-only cell(s) were skipped`)
  if (edits.length) emit('edit', edits)
}

// ------------------------------------------------------------------ clipboard
let internalClip = null // { text, rows: [[raw]], r, c }
function selectionTsv() {
  const { r1, r2, c1, c2 } = range.value
  const lines = []
  const raws = []
  for (let r = r1; r <= r2; r++) {
    const line = []
    const rawLine = []
    for (let c = c1; c <= c2; c++) {
      const t = props.getDisplay(r, c).text
      line.push(/[\t\n"]/.test(t) ? '"' + t.replace(/"/g, '""') + '"' : t)
      rawLine.push({ v: props.getRaw(r, c) ?? '', s: props.getStyle(r, c) })
    }
    lines.push(line.join('\t'))
    raws.push(rawLine)
  }
  return { text: lines.join('\n'), raws, r: r1, c: c1 }
}
function onCopy(e) {
  if (editing.value) return
  e.preventDefault()
  const clip = selectionTsv()
  internalClip = { ...clip }
  e.clipboardData.setData('text/plain', clip.text)
}
function onCut(e) {
  if (editing.value) return
  onCopy(e)
  clearSelection()
}
function parseTsv(text) {
  const rows = []
  let row = [], field = '', q = false
  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    if (q) {
      if (ch === '"') { if (text[i + 1] === '"') { field += '"'; i++ } else q = false } else field += ch
    } else if (ch === '"' && field === '') q = true
    else if (ch === '\t') { row.push(field); field = '' }
    else if (ch === '\n' || ch === '\r') {
      if (ch === '\r' && text[i + 1] === '\n') i++
      row.push(field); rows.push(row); row = []; field = ''
    } else field += ch
  }
  if (field !== '' || row.length) { row.push(field); rows.push(row) }
  return rows
}
function onPaste(e) {
  if (editing.value) return
  e.preventDefault()
  const text = e.clipboardData.getData('text/plain')
  if (!text) return
  const edits = []
  let blocked = 0
  const push = (r, c, v, s) => {
    if (props.canEdit(r, c)) { blocked++; return }
    edits.push({ r, c, v, s })
  }
  if (internalClip && internalClip.text === text) {
    // internal paste keeps formulas (shifted relatively) and styles
    const dr = sel.r - internalClip.r, dc = sel.c - internalClip.c
    internalClip.raws.forEach((line, i) => line.forEach((cell, j) => push(sel.r + i, sel.c + j, shiftFormula(cell.v, dr, dc), cell.s)))
  } else {
    const rows = parseTsv(text.replace(/\n$/, ''))
    rows.forEach((line, i) => line.forEach((v, j) => push(sel.r + i, sel.c + j, v, props.getStyle(sel.r + i, sel.c + j))))
  }
  if (blocked) ElMessage.warning(`${blocked} read-only cell(s) were skipped`)
  if (edits.length) {
    emit('edit', edits)
    const maxR = Math.max(...edits.map((x) => x.r)), maxC = Math.max(...edits.map((x) => x.c))
    sel.r2 = Math.min(maxR, props.rowCount - 1); sel.c2 = Math.min(maxC, props.colCount - 1)
  }
}

// ------------------------------------------------------------------ column resize
function startResize(e, c) {
  const startX = e.clientX
  const startW = colWidth(c)
  const onMove = (ev) => { widthOverride[c] = Math.max(40, startW + ev.clientX - startX) }
  const onUp = () => {
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
    const w = widthOverride[c]
    if (w && w !== startW) emit('resize-col', c, w)
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}
watch(() => props.columns, () => { for (const k of Object.keys(widthOverride)) delete widthOverride[k] }, { deep: true })

// ------------------------------------------------------------------ context menu
const menu = reactive({ show: false, x: 0, y: 0 })
const menuItems = computed(() => {
  const { r1, r2, c1, c2 } = range.value
  const nr = r2 - r1 + 1, nc = c2 - c1 + 1
  return [
    { key: 'history', label: `History of ${colName(sel.c)}${sel.r + 1}` },
    { key: 'column', label: `Column ${colName(sel.c)} settings…`, disabled: !props.canStructure.cols },
    { key: 'd1', divider: true },
    { key: 'insert-row-above', label: `Insert ${nr} row(s) above`, disabled: !props.canStructure.rows },
    { key: 'insert-row-below', label: `Insert ${nr} row(s) below`, disabled: !props.canStructure.rows },
    { key: 'delete-rows', label: `Delete row(s) ${r1 + 1}–${r2 + 1}`, disabled: !props.canStructure.rows },
    { key: 'd2', divider: true },
    { key: 'insert-col-left', label: `Insert ${nc} column(s) left`, disabled: !props.canStructure.cols },
    { key: 'insert-col-right', label: `Insert ${nc} column(s) right`, disabled: !props.canStructure.cols },
    { key: 'delete-cols', label: `Delete column(s) ${colName(c1)}–${colName(c2)}`, disabled: !props.canStructure.cols },
    { key: 'd3', divider: true },
    { key: 'clear', label: 'Clear contents' }
  ]
})
function onContextMenu(e) {
  const rect = scroller.value.getBoundingClientRect()
  menu.x = e.clientX - rect.left + scroller.value.scrollLeft
  menu.y = e.clientY - rect.top + scroller.value.scrollTop
  menu.show = true
}
function menuAction(key) {
  menu.show = false
  if (key === 'clear') clearSelection()
  else emit('action', key, { ...range.value, r: sel.r, c: sel.c })
  focusInput()
}
const closeMenu = () => { menu.show = false }

// ------------------------------------------------------------------ lifecycle
function onScroll() {
  scrollTop.value = scroller.value.scrollTop
  menu.show = false
}
let ro
onMounted(() => {
  viewH.value = scroller.value.clientHeight
  ro = new ResizeObserver(() => { viewH.value = scroller.value?.clientHeight ?? 600 })
  ro.observe(scroller.value)
  window.addEventListener('mouseup', stopDrag)
  window.addEventListener('mousedown', closeMenu)
  emit('select', { ...sel, ...range.value })
  focusInput()
})
onBeforeUnmount(() => {
  ro?.disconnect()
  window.removeEventListener('mouseup', stopDrag)
  window.removeEventListener('mousedown', closeMenu)
})

watch(() => [props.rowCount, props.colCount], () => {
  if (sel.r >= props.rowCount || sel.c >= props.colCount) setSel(Math.min(sel.r, props.rowCount - 1), Math.min(sel.c, props.colCount - 1))
})

defineExpose({
  selection: () => ({ ...sel, ...range.value }),
  select: (r, c) => { commitEdit(); setSel(r, c); focusInput() },
  startEdit,
  focus: focusInput,
  isEditing: () => editing.value
})
</script>

<style scoped>
.grid-scroller {
  position: relative;
  overflow: auto;
  flex: 1;
  min-height: 0;
  background: var(--grid-bg);
  outline: none;
}
.grid {
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
  user-select: none;
}
th, td {
  height: 26px;
  box-sizing: border-box;
  border-right: 1px solid var(--grid-line);
  border-bottom: 1px solid var(--grid-line);
  padding: 0 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 25px;
}
thead tr.letters th {
  position: sticky;
  top: 0;
  z-index: 3;
  height: 24px;
  line-height: 23px;
  background: var(--grid-head);
  color: var(--text-2);
  font-weight: 500;
  text-align: center;
  font-size: 12px;
}
thead tr.letters th.sel, th.rownum.sel { background: var(--grid-head-sel); color: var(--accent); }
thead tr.frozen td, thead tr.frozen th { position: sticky; z-index: 2; }
thead tr.frozen td { background: var(--grid-bg); box-shadow: inset 0 -1px 0 var(--grid-line-strong); }
th.corner { left: 0; z-index: 5 !important; cursor: pointer; }
th.rownum {
  position: sticky;
  left: 0;
  z-index: 1;
  background: var(--grid-head);
  color: var(--text-2);
  font-weight: 400;
  font-size: 12px;
  text-align: center;
  cursor: pointer;
}
thead tr.frozen th.rownum { z-index: 4; }
td.pad { border: 0; padding: 0; }
.cell { cursor: cell; color: var(--text-1); }
.cell.k-num { text-align: right; font-variant-numeric: tabular-nums; }
.cell.k-err { color: var(--danger); text-align: center; }
.cell.k-bool { text-align: center; }
.cell.hdr { font-weight: 600; }
.cell.ro { background-image: linear-gradient(var(--ro-tint), var(--ro-tint)); }
.cell.in-sel { background-image: linear-gradient(var(--sel-tint), var(--sel-tint)); }
.cell.flash { animation: flash 2.5s ease-out; }
@keyframes flash {
  0%, 40% { box-shadow: inset 0 0 0 2px var(--warn); background-color: var(--warn-soft); }
  100% { box-shadow: none; }
}
.letter { pointer-events: none; }
.col-flag { font-size: 11px; margin-left: 3px; vertical-align: -1px; }
.col-flag.watch { color: var(--warn); }
.col-flag.lock { color: var(--text-3); }
.resizer {
  position: absolute;
  right: -3px;
  top: 0;
  width: 7px;
  height: 100%;
  cursor: col-resize;
  z-index: 4;
}
.resizer:hover { background: var(--accent-soft); }
.sel-box {
  position: absolute;
  border: 2px solid var(--accent);
  pointer-events: none;
  z-index: 2;
  box-sizing: border-box;
}
.cell-input {
  position: absolute;
  z-index: 6;
  box-sizing: border-box;
  border: 0;
  padding: 0 6px;
  font: inherit;
  font-size: 13px;
  line-height: 24px;
  resize: none;
  overflow: hidden;
  white-space: pre;
  opacity: 0;
  pointer-events: none;
  background: transparent;
  color: var(--text-1);
  outline: none;
}
.cell-input.editing {
  opacity: 1;
  pointer-events: auto;
  background: var(--grid-bg);
  border: 2px solid var(--accent);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.18);
  line-height: 20px;
}
.ctx-menu {
  position: absolute;
  z-index: 20;
  min-width: 220px;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.18);
  padding: 4px 0;
  font-size: 13px;
}
.ctx-item { padding: 6px 14px; cursor: pointer; color: var(--text-1); }
.ctx-item:hover { background: var(--hover); }
.ctx-item.disabled { color: var(--text-3); cursor: default; background: none; }
.ctx-item.divider { height: 1px; padding: 0; margin: 4px 0; background: var(--border); cursor: default; }
</style>
