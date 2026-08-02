<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="ad-box px-panel px-scroll">
      <div class="px-titlebar">
        <span>ADMIN &middot; LIVE-BALANCE</span>
        <button class="px-close" @click="emit('close')">&times;</button>
      </div>

      <div v-if="loading" class="ad-loading">Lade…</div>

      <div v-else class="ad-body">
        <div v-if="notice" class="ad-notice" :class="{ error: noticeError }">{{ notice }}</div>

        <!-- ── Markt ─────────────────────────────────────── -->
        <section class="ad-section">
          <div class="ad-section-head">
            <span>MARKT</span>
            <button class="px-btn px-btn-accent ad-save" :disabled="saving" @click="saveMarket">SPEICHERN</button>
          </div>
          <div class="ad-grid">
            <label v-for="f in marketFields" :key="f.key" class="ad-field">
              <span>{{ f.label }}</span>
              <input type="number" step="any" v-model.number="market[f.key]" />
            </label>
          </div>
        </section>

        <!-- ── Balance (Gebäude/Bürger/Prestige) ────────────── -->
        <section class="ad-section">
          <div class="ad-section-head">
            <span>BALANCE &middot; GEBÄUDE / BÜRGER / PRESTIGE</span>
            <button class="px-btn px-btn-accent ad-save" :disabled="saving" @click="saveBalance">SPEICHERN</button>
          </div>
          <div class="ad-grid">
            <label v-for="f in balanceFields" :key="f.key" class="ad-field">
              <span>{{ f.label }}</span>
              <input type="number" step="any" v-model.number="balance[f.key]" />
            </label>
          </div>
        </section>

        <!-- ── Upgrades ──────────────────────────────────── -->
        <section class="ad-section">
          <div class="ad-section-head"><span>UPGRADES</span></div>
          <div v-for="u in upgrades" :key="u.id" class="ad-row">
            <div class="ad-row-name">{{ u.name }}</div>
            <label class="ad-field ad-field-inline"><span>Basiskosten</span><input type="number" step="any" v-model.number="u.baseCost" /></label>
            <label class="ad-field ad-field-inline"><span>Effekt/Stufe</span><input type="number" step="any" v-model.number="u.effectPerLevel" /></label>
            <label class="ad-field ad-field-inline"><span>Max-Stufe</span><input type="number" step="1" v-model.number="u.maxLevel" /></label>
            <button class="px-btn px-btn-accent ad-save" :disabled="saving" @click="saveUpgrade(u)">SPEICHERN</button>
          </div>
        </section>

        <!-- ── Rezepte ───────────────────────────────────── -->
        <section class="ad-section">
          <div class="ad-section-head"><span>REZEPTE</span></div>
          <div v-for="r in recipes" :key="r.id" class="ad-row ad-row-recipe">
            <div class="ad-row-name">{{ r.name }}</div>
            <label class="ad-field ad-field-inline"><span>Zucker</span><input type="number" step="any" v-model.number="r.sugar" /></label>
            <label class="ad-field ad-field-inline"><span>Mehl</span><input type="number" step="any" v-model.number="r.flour" /></label>
            <label class="ad-field ad-field-inline"><span>Eier</span><input type="number" step="any" v-model.number="r.eggs" /></label>
            <label class="ad-field ad-field-inline"><span>Butter</span><input type="number" step="any" v-model.number="r.butter" /></label>
            <label class="ad-field ad-field-inline"><span>Schoko</span><input type="number" step="any" v-model.number="r.chocolate" /></label>
            <label class="ad-field ad-field-inline"><span>Milch</span><input type="number" step="any" v-model.number="r.milk" /></label>
            <label class="ad-field ad-field-inline"><span>Output</span><input type="number" step="any" v-model.number="r.output" /></label>
            <label class="ad-field ad-field-inline"><span>Backzeit (s)</span><input type="number" step="1" v-model.number="r.bakeDurationSeconds" /></label>
            <button class="px-btn px-btn-accent ad-save" :disabled="saving" @click="saveRecipe(r)">SPEICHERN</button>
          </div>
        </section>

        <!-- ── Reset-Aktionen ────────────────────────────── -->
        <section class="ad-section">
          <div class="ad-section-head"><span>RESET</span></div>
          <div class="ad-reset-row">
            <button class="px-btn px-btn-sell" @click="resetMarket">MARKT ZURÜCKSETZEN</button>
            <button class="px-btn px-btn-sell" @click="resetPlayer">SPIELER ZURÜCKSETZEN</button>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { useAudio } from '../composables/useAudio.js'
import {
  getAdminConfig, updateAdminMarketConfig, updateAdminBalanceConfig,
  getAdminUpgrades, updateAdminUpgrade,
  getAdminRecipes, updateAdminRecipe,
  adminResetMarket, adminResetPlayer,
} from '../services/api.js'

const emit = defineEmits(['close'])
const playerStore = usePlayerStore()
const audio = useAudio()

onMounted(() => { audio.playBookOpen(); load() })

const loading = ref(true)
const saving  = ref(false)
const notice  = ref('')
const noticeError = ref(false)

const market   = reactive({})
const balance  = reactive({})
const upgrades = ref([])
const recipes  = ref([])

const marketFields = [
  { key: 'updateIntervalMs',      label: 'Tick-Intervall (ms)' },
  { key: 'minPrice',              label: 'Preis-Notbremse (min)' },
  { key: 'stockFluctuationRatio', label: 'Zufalls-Stock-Anteil/Tick' },
  { key: 'sellFeeRate',           label: 'Marktgebühr' },
  { key: 'initialSugarPrice',     label: 'Startpreis Zucker' },
  { key: 'initialFlourPrice',     label: 'Startpreis Mehl' },
  { key: 'initialEggsPrice',      label: 'Startpreis Eier' },
  { key: 'initialButterPrice',    label: 'Startpreis Butter' },
  { key: 'initialChocolatePrice', label: 'Startpreis Schokolade' },
  { key: 'initialMilkPrice',      label: 'Startpreis Milch' },
  { key: 'initialSugarStock',     label: 'Startbestand Zucker' },
  { key: 'initialFlourStock',     label: 'Startbestand Mehl' },
  { key: 'initialEggsStock',      label: 'Startbestand Eier' },
  { key: 'initialButterStock',    label: 'Startbestand Butter' },
  { key: 'initialChocolateStock', label: 'Startbestand Schokolade' },
  { key: 'initialMilkStock',      label: 'Startbestand Milch' },
]

const balanceFields = [
  { key: 'baseStorageCap',            label: 'Lager-Basis-Kapazität' },
  { key: 'storagePerLevel',           label: 'Lager-Kapazität/Stufe' },
  { key: 'citizensPerRatLevel',       label: 'Bürger pro Rathaus-Stufe' },
  { key: 'citizenBaseCost',           label: 'Bürger-Basiskosten' },
  { key: 'citizenCostGrowth',         label: 'Bürger-Kosten-Wachstum' },
  { key: 'workersPerLevel',           label: 'Arbeiter-Slots/Gebäude-Stufe' },
  { key: 'buildingCostGrowth',        label: 'Gebäude-Kosten-Wachstum' },
  { key: 'passiveTickSeconds',        label: 'Passiv-Tick (s)' },
  { key: 'prestigeBaseThreshold',     label: 'Prestige-Schwelle (Basis)' },
  { key: 'prestigeThresholdGrowth',   label: 'Prestige-Schwelle-Wachstum' },
  { key: 'prestigeMultiplierPerLevel',label: 'Prestige-Bonus/Stufe' },
]

function flash(msg, isError = false) {
  notice.value = msg
  noticeError.value = isError
  setTimeout(() => { notice.value = '' }, 2500)
}

async function load() {
  loading.value = true
  try {
    const [cfg, u, r] = await Promise.all([getAdminConfig(), getAdminUpgrades(), getAdminRecipes()])
    Object.assign(market, cfg.market)
    Object.assign(balance, cfg.balance)
    upgrades.value = u
    recipes.value = r
  } catch (e) {
    flash(e?.message ?? 'Laden fehlgeschlagen', true)
  } finally {
    loading.value = false
  }
}

async function saveMarket() {
  saving.value = true
  try {
    Object.assign(market, await updateAdminMarketConfig(market))
    flash('Markt-Config gespeichert.')
  } catch (e) { flash(e?.message ?? 'Fehler', true) }
  finally { saving.value = false }
}

async function saveBalance() {
  saving.value = true
  try {
    Object.assign(balance, await updateAdminBalanceConfig(balance))
    flash('Balance-Config gespeichert.')
  } catch (e) { flash(e?.message ?? 'Fehler', true) }
  finally { saving.value = false }
}

async function saveUpgrade(u) {
  saving.value = true
  try {
    await updateAdminUpgrade(u.id, u)
    flash(`${u.name} gespeichert.`)
  } catch (e) { flash(e?.message ?? 'Fehler', true) }
  finally { saving.value = false }
}

async function saveRecipe(r) {
  saving.value = true
  try {
    await updateAdminRecipe(r.id, r)
    flash(`${r.name} gespeichert.`)
  } catch (e) { flash(e?.message ?? 'Fehler', true) }
  finally { saving.value = false }
}

async function resetMarket() {
  try { await adminResetMarket(); flash('Markt zurückgesetzt.') }
  catch (e) { flash(e?.message ?? 'Fehler', true) }
}

async function resetPlayer() {
  try {
    await adminResetPlayer(playerStore.steamId)
    await playerStore.init(playerStore.steamId)
    flash('Spieler zurückgesetzt.')
  } catch (e) { flash(e?.message ?? 'Fehler', true) }
}
</script>

<style scoped>
.ad-box { width: 900px; max-width: 95vw; max-height: 88vh; overflow: auto; display: flex; flex-direction: column; }
.ad-loading { padding: 24px; text-align: center; color: var(--px-tan-ink); }
.ad-body { padding: 16px 20px; display: flex; flex-direction: column; gap: 16px; }

.ad-notice { padding: 8px 12px; background: #dff0d0; border: 3px solid var(--px-green); color: #2f5a1c; font-size: 13px; }
.ad-notice.error { background: #f5d5d5; border-color: var(--px-red); color: var(--px-red-dk); }

.ad-section { display: flex; flex-direction: column; gap: 10px; }
.ad-section-head {
  display: flex; align-items: center; justify-content: space-between;
  font-family: 'Silkscreen', monospace; font-size: 12px; color: var(--px-tan-hd);
  border-bottom: 3px solid var(--px-brown2); padding-bottom: 6px; letter-spacing: 1px;
}

.ad-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px 14px; }
.ad-field { display: flex; flex-direction: column; gap: 3px; font-size: 11px; color: var(--px-tan-ink); }
.ad-field input {
  font-family: 'Silkscreen', monospace; font-size: 12px; padding: 5px 7px;
  border: 3px solid var(--px-ink); background: var(--px-cream2); color: var(--px-ink-txt);
}

.ad-row {
  display: flex; align-items: end; gap: 10px; flex-wrap: wrap;
  padding: 8px 10px; background: var(--px-cream3); border: 2px solid var(--px-brown2);
}
.ad-row-name { font-size: 14px; color: var(--px-ink-txt); min-width: 140px; flex-shrink: 0; }
.ad-field-inline { width: 90px; }

.ad-save { font-size: 10px; padding: 6px 10px; margin-left: auto; }

.ad-reset-row { display: flex; gap: 10px; }
</style>
