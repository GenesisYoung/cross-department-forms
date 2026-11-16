<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>{{ tr("Templates") }}</h2>
        <p>
          {{
            tr(
              "Reusable layouts: headers, column rules (watched / department-only), widths and optional starter data.",
            )
          }}
        </p>
      </div>
      <el-button
        v-if="auth.canCreate"
        type="primary"
        :icon="Plus"
        @click="openDesigner()"
        >{{ tr("New template") }}</el-button
      >
    </div>

    <div
      class="cards"
      v-loading="loading"
      v-if="!loading && !templates.length && !templates.length < 1"
    >
      <div v-for="t in templates" :key="t.id" class="card">
        <div class="card-head">
          <div class="card-title">{{ t.name }}</div>
          <span
            v-if="t.department"
            class="dept-pill"
            :style="deptStyle(t.department.code)"
            >{{ t.department.name }}</span
          >
        </div>
        <div class="card-desc">{{ t.description || "—" }}</div>
        <div class="chips">
          <span
            v-for="c in titledColumns(t)"
            :key="c.index"
            class="chip"
            :class="{ watched: c.watched }"
          >
            <el-icon v-if="c.watched"><Bell /></el-icon>
            <el-icon v-if="c.editDepartments?.length"><Lock /></el-icon>
            {{ c.title }}
            <small v-if="c.editDepartments?.length">{{
              c.editDepartments.join("/")
            }}</small>
          </span>
        </div>
        <div class="card-meta">
          {{
            tr("{0} rows × {1} cols · by {2} · {3}", [
              t.definition.rowCount,
              t.definition.colCount,
              t.createdBy || tr("system"),
              timeAgo(t.createdAt),
            ])
          }}
        </div>
        <div class="card-actions">
          <el-button
            size="small"
            type="primary"
            :disabled="!auth.canCreate"
            @click="
              $router.push({
                name: 'sheets',
                query: { new: 1, template: t.id },
              })
            "
            >{{ tr("Use template") }}</el-button
          >
          <el-button
            size="small"
            :disabled="!canManage(t)"
            @click="openDesigner(t)"
            >{{ tr("Edit") }}</el-button
          >
          <el-button
            size="small"
            type="danger"
            plain
            :disabled="!canManage(t)"
            @click="remove(t)"
            >{{ tr("Delete") }}</el-button
          >
        </div>
      </div>
    </div>
    <div v-else class="no-data">
      <el-empty
        v-if="!loading && !templates.length"
        :description="tr('No templates yet')"
      />
    </div>

    <TemplateDesigner
      v-model="designerOpen"
      :template="editing"
      @saved="load"
    />
  </div>
</template>

<script setup>
import { tr } from "../i18n.js";

import { Plus } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { ref } from "vue";
import api from "../api.js";
import TemplateDesigner from "../components/TemplateDesigner.vue";
import { useAuth } from "../stores/auth.js";
import { deptStyle, timeAgo } from "../utils.js";

const auth = useAuth();
const templates = ref([]);
const loading = ref(true);
const designerOpen = ref(false);
const editing = ref(null);

async function load() {
  loading.value = true;
  try {
    templates.value = await api.get("/templates");
  } finally {
    loading.value = false;
  }
}
load();

function titledColumns(t) {
  const header = Object.fromEntries(
    t.definition.cells
      .filter((c) => c.r === t.definition.headerRows - 1)
      .map((c) => [c.c, c.v]),
  );
  const byIndex = Object.fromEntries(
    t.definition.columns.map((c) => [c.index, c]),
  );
  const out = [];
  for (let i = 0; i < t.definition.colCount; i++) {
    const title = byIndex[i]?.title || header[i];
    if (title)
      out.push({
        index: i,
        title,
        watched: byIndex[i]?.watched,
        editDepartments: byIndex[i]?.editDepartments,
      });
  }
  return out;
}
const canManage = (t) =>
  auth.isAdmin ||
  (auth.user.role === "MANAGER" &&
    t.department?.id === auth.user.department.id);
function openDesigner(t = null) {
  editing.value = t;
  designerOpen.value = true;
}
async function remove(t) {
  await ElMessageBox.confirm(
    tr('Delete template "{0}"? Sheets created from it are not affected.', [
      t.name,
    ]),
    tr("Delete template"),
    { type: "warning" },
  );
  await api.delete(`/templates/${t.id}`);
  ElMessage.success(tr("Template deleted"));
  load();
}
</script>

<style scoped>
.page {
  max-width: 1400px;
  margin: 0 auto;
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.page-head h2 {
  margin: 0;
  font-size: 22px;
  color: var(--text-1);
}
.page-head p {
  margin: 4px 0 0;
  color: var(--text-2);
}
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 14px;
  min-height: 120px;
}
.card {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.card-title {
  font-weight: 600;
  font-size: 15px;
  color: var(--text-1);
}
.card-desc {
  color: var(--text-2);
  font-size: 13px;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.chip {
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 10px;
  background: var(--hover);
  color: var(--text-1);
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.chip.watched {
  background: var(--warn-soft);
  color: var(--warn-text);
}
.chip small {
  color: var(--text-3);
}
.card-meta {
  font-size: 12px;
  color: var(--text-3);
}
.card-actions {
  display: flex;
  gap: 6px;
  margin-top: auto;
}
.no-data {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 120px;
}
</style>
