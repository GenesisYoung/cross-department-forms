<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-language"><ThemeSelect /><LanguageSelect /></div>
      <div class="brand">
        <img src="/favicon.svg" alt="" />
        <div>
          <h1>{{ tr("Factory Collaboration") }}</h1>
          <p>
            {{ tr("Warehouse · PMC · Purchase — one live source of truth") }}
          </p>
        </div>
      </div>
      <el-alert
        v-if="$route.query.expired"
        type="warning"
        :closable="false"
        show-icon
        :title="tr('Your session expired, please sign in again.')"
        class="mb"
      />
      <el-form @submit.prevent="submit" label-position="top">
        <el-form-item :label="tr('Username')">
          <el-input v-model="username" autocomplete="username" autofocus />
        </el-form-item>
        <el-form-item :label="tr('Password')">
          <el-input
            v-model="password"
            type="password"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-button
          type="primary"
          native-type="submit"
          :loading="busy"
          class="full"
          >{{ tr("Sign in") }}</el-button
        >
      </el-form>
      <!-- <div class="demo">
        <div class="demo-title">{{ tr("Demo accounts (password") }} <code>password123</code>)</div>
        <div class="demo-grid">
          <button v-for="d in demos" :key="d.u" type="button" @click="username = d.u; password = 'password123'">
            <span class="dept-pill" :style="deptStyle(d.code)">{{ tr(d.dept) }}</span>
            <span>{{ d.u }}</span>
            <small>{{ tr(d.role) }}</small>
          </button>
        </div>
      </div> -->
    </div>
  </div>
</template>

<script setup>
import LanguageSelect from "../components/LanguageSelect.vue";
import ThemeSelect from "../components/ThemeSelect.vue";
import { tr } from "../i18n.js";

import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuth } from "../stores/auth.js";

const auth = useAuth();
const router = useRouter();
const route = useRoute();
const username = ref("");
const password = ref("");
const busy = ref(false);
const demos = [
  { u: "wh.manager", dept: "Warehouse", code: "WAREHOUSE", role: "Manager" },
  { u: "wh.clerk", dept: "Warehouse", code: "WAREHOUSE", role: "Member" },
  { u: "pmc.manager", dept: "PMC", code: "PMC", role: "Manager" },
  { u: "pmc.planner", dept: "PMC", code: "PMC", role: "Member" },
  { u: "pur.manager", dept: "Purchase", code: "PURCHASE", role: "Manager" },
  { u: "pur.buyer", dept: "Purchase", code: "PURCHASE", role: "Member" },
  { u: "admin", dept: "IT", code: "IT", role: "Admin" },
];

async function submit() {
  busy.value = true;
  try {
    await auth.login(username.value, password.value);
    router.replace(route.query.next || "/");
  } finally {
    busy.value = false;
  }
}
</script>

<style scoped>
.login-language {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
  margin-bottom: 16px;
}
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 16px;
  background: var(--page);
}
.login-card {
  width: 100%;
  max-width: 440px;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 28px;
  box-shadow: 0 10px 40px rgba(15, 30, 60, 0.08);
}
.brand {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-bottom: 22px;
}
.brand img {
  width: 44px;
  height: 44px;
}
.brand h1 {
  font-size: 20px;
  margin: 0;
  color: var(--text-1);
}
.brand p {
  margin: 2px 0 0;
  color: var(--text-2);
  font-size: 13px;
}
.full {
  width: 100%;
  margin-top: 4px;
}
.mb {
  margin-bottom: 14px;
}
.demo {
  margin-top: 22px;
  border-top: 1px solid var(--border);
  padding-top: 14px;
}
.demo-title {
  font-size: 12px;
  color: var(--text-2);
  margin-bottom: 8px;
}
.demo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 6px;
}
.demo-grid button {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  color: var(--text-1);
  font: inherit;
  font-size: 12px;
  text-align: left;
}
.demo-grid button:hover {
  background: var(--hover);
}
.demo-grid small {
  color: var(--text-3);
  margin-left: auto;
}
</style>
