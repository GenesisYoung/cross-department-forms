<template>
  <div class="dash" v-loading="!data">
    <template v-if="data">
      <div class="greeting">
        <div>
          <h2>
            {{
              tr("Good {0}, {1}", [
                tr(partOfDay),
                data.me.displayName.split(" ")[0],
              ])
            }}
          </h2>
          <p>
            {{
              tr(
                "Live view of what {0} shares and depends on. Updates arrive instantly — no meeting needed.",
                [data.me.department.name],
              )
            }}
          </p>
        </div>
        <div class="actions">
          <el-button
            v-if="auth.canCreate"
            type="primary"
            :icon="Plus"
            @click="$router.push({ name: 'sheets', query: { new: 1 } })"
            >{{ tr("New sheet") }}</el-button
          >
          <el-button
            v-if="auth.canCreate"
            :icon="Upload"
            @click="$router.push({ name: 'sheets', query: { import: 1 } })"
            >{{ tr("Import Excel") }}</el-button
          >
        </div>
      </div>

      <div class="kpis">
        <div class="kpi" :class="{ alert: data.notifications.importantUnread }">
          <div class="kpi-label">{{ tr("Key changes to acknowledge") }}</div>
          <div class="kpi-value">{{ data.notifications.importantUnread }}</div>
          <div class="kpi-foot">
            {{ data.notifications.unread }}
            {{ tr("unread notifications in total") }}
          </div>
        </div>
        <div class="kpi">
          <div class="kpi-label">{{ tr("Edits in the last 24 h") }}</div>
          <div class="kpi-value">{{ totalChanges }}</div>
          <div class="kpi-foot">
            {{ tr("across") }} {{ sheetCount }} {{ tr("sheets you can see") }}
          </div>
        </div>
        <div class="kpi">
          <div class="kpi-label">{{ tr("Shared with my department") }}</div>
          <div class="kpi-value">{{ sharedWithMe }}</div>
          <div class="kpi-foot">
            {{ tr("sheets owned by other departments") }}
          </div>
        </div>
        <div class="kpi">
          <div class="kpi-label">{{ tr("Watched-column changes") }}</div>
          <div class="kpi-value">{{ data.watchedChanges.length }}</div>
          <div class="kpi-foot">
            {{ tr("most recent deadline / qty / status moves") }}
          </div>
        </div>
      </div>

      <section v-if="data.importantUnread.length" class="panel attention">
        <div class="panel-head">
          <h3>
            <el-icon><WarningFilled /></el-icon>
            {{ tr("Needs your acknowledgement") }}
          </h3>
          <span class="muted">{{
            tr("The editor can see who has acknowledged each change")
          }}</span>
        </div>
        <div v-for="n in data.importantUnread" :key="n.id" class="attn-item">
          <div class="attn-body">
            <div class="attn-title">{{ n.title }}</div>
            <pre class="attn-msg">{{ n.message }}</pre>
            <div class="muted">
              {{ n.actorName }} · {{ n.actorDepartment }} ·
              {{ timeAgo(n.createdAt) }}
            </div>
          </div>
          <div class="attn-actions">
            <el-button
              size="small"
              @click="
                $router.push({ name: 'sheet', params: { id: n.sheetId } })
              "
              >{{ tr("Open") }}</el-button
            >
            <el-button size="small" type="warning" @click="ack(n)">{{
              tr("Acknowledge")
            }}</el-button>
          </div>
        </div>
      </section>

      <div class="columns">
        <!-- <div class="col-main">
          <section v-for="panel in data.departments" :key="panel.department.id" class="panel">
            <div class="panel-head">
              <h3>
                <span class="dept-pill" :style="deptStyle(panel.department.code)">{{ panel.department.name }}</span>
                <span v-if="panel.department.id === data.me.department.id" class="muted">{{ tr("your department") }}</span>
              </h3>
              <span class="muted">{{ panel.changesLast24h }} {{ tr("edits / 24 h") }}</span>
            </div>
            <p v-if="panel.department.description" class="dept-desc">{{ panel.department.description }}</p>
            <el-empty v-if="!panel.sheets.length" :image-size="50" :description="tr('No sheets yet')" />
            <div class="sheet-list">
              <router-link v-for="s in panel.sheets" :key="s.id" :to="{ name: 'sheet', params: { id: s.id } }" class="sheet-card">
                <div class="sheet-top">
                  <el-icon class="sheet-icon"><Grid /></el-icon>
                  <span class="sheet-title">{{ s.name }}</span>
                  <el-tag size="small" effect="plain" :type="accessType(s.access)">{{ accessLabel(s.access) }}</el-tag>
                </div>
                <div class="sheet-desc">{{ s.description || '—' }}</div>
                <div class="sheet-meta">
                  <span>{{ tr("Updated") }} {{ timeAgo(s.updatedAt) }}</span>
                  <span v-if="s.changesLast24h" class="hot">{{ s.changesLast24h }} {{ tr("edits today") }}</span>
                  <span v-if="s.subscribed"><el-icon><BellFilled /></el-icon> {{ tr("following") }}</span>
                </div>
              </router-link>
            </div>
          </section>
        </div> -->

        <div class="col-side">
          <section class="panel">
            <div class="panel-head">
              <h3>{{ tr("Key changes") }}</h3>
              <span class="muted">{{ tr("watched columns") }}</span>
            </div>
            <el-empty
              v-if="!data.watchedChanges.length"
              :image-size="50"
              :description="tr('No key changes yet')"
            />
            <div
              v-for="ch in data.watchedChanges"
              :key="ch.id"
              class="feed-item key"
              @click="openChange(ch)"
            >
              <div class="feed-top">
                <span
                  class="dept-dot"
                  :style="deptDot(ch.departmentCode)"
                ></span>
                <b>{{ ch.userName }}</b>
                <span class="muted">{{ timeAgo(ch.changedAt) }}</span>
              </div>
              <div class="feed-where">
                {{ ch.sheetName }} · {{ ch.rowLabel }}
              </div>
              <div class="feed-diff">
                {{ ch.columnLabel }}: <s>{{ show(ch.oldValue) }}</s> →
                <b>{{ show(ch.newValue) }}</b>
              </div>
              <div v-if="ch.acknowledgements.length" class="acks">
                {{ ackCount(ch) }} / {{ ch.acknowledgements.length }}
                {{ tr("acknowledged") }}
                <el-progress
                  :percentage="
                    Math.round(
                      (100 * ackCount(ch)) / ch.acknowledgements.length,
                    )
                  "
                  :show-text="false"
                  :stroke-width="4"
                />
              </div>
            </div>
          </section>

          <section class="panel">
            <div class="panel-head">
              <h3>{{ tr("Recent activity") }}</h3>
              <span class="muted">{{ tr("all shared sheets") }}</span>
            </div>
            <el-empty
              v-if="!data.recentChanges.length"
              :image-size="50"
              :description="tr('No activity yet')"
            />
            <div
              v-for="ch in data.recentChanges"
              :key="ch.id"
              class="feed-item"
              @click="openChange(ch)"
            >
              <div class="feed-top">
                <span
                  class="dept-dot"
                  :style="deptDot(ch.departmentCode)"
                ></span>
                <b>{{ ch.userName }}</b>
                <span class="muted">{{ timeAgo(ch.changedAt) }}</span>
              </div>
              <div class="feed-where">
                {{ ch.sheetName }} · {{ ch.cellRef }} · {{ ch.columnLabel }}
              </div>
              <div class="feed-diff">
                <s>{{ show(ch.oldValue) }}</s> → {{ show(ch.newValue) }}
              </div>
            </div>
          </section>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { tr } from "../i18n.js";

import { Plus, Upload } from "@element-plus/icons-vue";
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRouter } from "vue-router";
import api from "../api.js";
import { subscribe } from "../realtime.js";
import { useAuth } from "../stores/auth.js";
import { useNotifications } from "../stores/notifications.js";
import { deptDot, show, timeAgo } from "../utils.js";

const auth = useAuth();
const notes = useNotifications();
const router = useRouter();
const data = ref(null);

async function load() {
  data.value = await api.get("/dashboard");
}
load();

// Refresh (debounced) whenever any sheet changes or a notification arrives.
let timer = null;
const soon = () => {
  clearTimeout(timer);
  timer = setTimeout(load, 600);
};
const stopActivity = subscribe("/topic/activity", soon);
watch(() => notes.version, soon);
onBeforeUnmount(() => {
  stopActivity();
  clearTimeout(timer);
});

const partOfDay = computed(() => {
  const h = new Date().getHours();
  return h < 12 ? "morning" : h < 18 ? "afternoon" : "evening";
});
const allSheets = computed(() =>
  data.value.departments.flatMap((p) => p.sheets),
);
const sheetCount = computed(() => allSheets.value.length);
const totalChanges = computed(() =>
  allSheets.value.reduce((s, x) => s + x.changesLast24h, 0),
);
const sharedWithMe = computed(
  () =>
    allSheets.value.filter(
      (s) => s.ownerDepartment.id !== data.value.me.department.id,
    ).length,
);
const accessLabel = (a) =>
  ({ OWNER: tr("Owner"), EDIT: tr("Edit"), VIEW: tr("View") })[a];
const accessType = (a) =>
  ({ OWNER: "success", EDIT: "primary", VIEW: "info" })[a];
const ackCount = (ch) =>
  ch.acknowledgements.filter((a) => a.acknowledgedAt).length;

async function ack(n) {
  await notes.markRead(n.id);
  await load();
}
function openChange(ch) {
  router.push({
    name: "sheet",
    params: { id: ch.sheetId },
    query: { r: ch.row, c: ch.col },
  });
}
</script>

<style scoped>
.dash {
  max-width: 1400px;
  margin: 0 auto;
  min-height: 300px;
}
.greeting {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}
.greeting h2 {
  margin: 0;
  font-size: 22px;
  color: var(--text-1);
}
.greeting p {
  margin: 4px 0 0;
  color: var(--text-2);
}
.actions {
  display: flex;
  gap: 8px;
}
.kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.kpi {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 14px 16px;
}
.kpi.alert {
  border-color: var(--warn);
  background: var(--warn-soft);
}
.kpi-label {
  font-size: 12px;
  color: var(--text-2);
}
.kpi-value {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-1);
  margin: 4px 0;
  font-variant-numeric: tabular-nums;
}
.kpi-foot {
  font-size: 12px;
  color: var(--text-3);
}
.panel {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 14px;
}
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.panel-head h3 {
  margin: 0;
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-1);
}
.muted {
  color: var(--text-3);
  font-size: 12px;
  font-weight: 400;
}
.attention {
  border-color: var(--warn);
}
.attention h3 {
  color: var(--warn-text);
}
.attn-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-top: 1px solid var(--border);
}
.attn-title {
  font-weight: 600;
  font-size: 14px;
}
.attn-msg {
  font-family: inherit;
  white-space: pre-wrap;
  margin: 4px 0;
  font-size: 13px;
  color: var(--text-1);
}
.attn-actions {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  flex-shrink: 0;
}
.columns {
}
.dept-desc {
  margin: -4px 0 10px;
  color: var(--text-2);
  font-size: 13px;
}
.sheet-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 10px;
}
.sheet-card {
  display: block;
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 10px 12px;
  text-decoration: none;
  color: inherit;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}
.sheet-card:hover {
  border-color: var(--accent);
  box-shadow: 0 2px 10px rgba(31, 78, 140, 0.08);
}
.sheet-top {
  display: flex;
  align-items: center;
  gap: 6px;
}
.sheet-icon {
  color: var(--accent);
}
.sheet-title {
  font-weight: 600;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-1);
}
.sheet-desc {
  color: var(--text-2);
  font-size: 12px;
  margin: 4px 0 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.sheet-meta {
  display: flex;
  gap: 10px;
  font-size: 11px;
  color: var(--text-3);
  flex-wrap: wrap;
  align-items: center;
}
.sheet-meta .hot {
  color: var(--warn-text);
}
.feed-item {
  padding: 8px 0;
  border-top: 1px solid var(--border);
  cursor: pointer;
  font-size: 13px;
}
.feed-item:first-of-type {
  border-top: 0;
}
.feed-item:hover {
  background: var(--hover);
}
.feed-top {
  display: flex;
  align-items: center;
  gap: 6px;
}
.feed-top .muted {
  margin-left: auto;
}
.dept-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.feed-where {
  font-size: 12px;
  color: var(--text-2);
  margin: 2px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.feed-diff {
  word-break: break-word;
}
.feed-diff s {
  color: var(--text-3);
}
.acks {
  font-size: 11px;
  color: var(--text-2);
  margin-top: 4px;
}
@media (max-width: 960px) {
  .columns {
    grid-template-columns: 1fr;
  }
}
</style>
