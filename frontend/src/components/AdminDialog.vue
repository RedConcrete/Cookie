<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="ad-box px-panel">
      <div class="px-titlebar">
        <span>{{ t('adminDialog.title') }}</span>
        <button class="px-close" @click="emit('close')">&times;</button>
      </div>

      <PixelScrollBox class="ad-scroll">
      <div v-if="loading" class="ad-loading"><LoadingIndicator /></div>

      <div v-else class="ad-body">
        <div v-if="notice" class="ad-notice" :class="{ error: noticeError }">{{ notice }}</div>

        <!-- ── Markt ─────────────────────────────────────── -->
        <section class="ad-section">
          <div class="ad-section-head">
            <span>{{ t('adminDialog.marketSection') }}</span>
            <button class="px-btn px-btn-accent ad-save" :disabled="saving" @click="saveMarket">{{ t('adminDialog.save') }}</button>
          </div>
          <div class="ad-grid">
            <label v-for="f in marketFields" :key="f.key" class="ad-field">
              <span>{{ t(f.labelKey) }}</span>
              <input type="number" step="any" v-model.number="market[f.key]" />
            </label>
          </div>
        </section>

        <!-- ── Balance (Gebäude/Bürger/Prestige) ────────────── -->
        <section class="ad-section">
          <div class="ad-section-head">
            <span>{{ t('adminDialog.balanceSection') }}</span>
            <button class="px-btn px-btn-accent ad-save" :disabled="saving" @click="saveBalance">{{ t('adminDialog.save') }}</button>
          </div>
          <div class="ad-grid">
            <label v-for="f in balanceFields" :key="f.key" class="ad-field">
              <span>{{ t(f.labelKey) }}</span>
              <input type="number" step="any" v-model.number="balance[f.key]" />
            </label>
          </div>
        </section>

        <!-- ── Upgrades ──────────────────────────────────── -->
        <section class="ad-section">
          <div class="ad-section-head"><span>{{ t('adminDialog.upgradesSection') }}</span></div>
          <div v-for="u in upgrades" :key="u.id" class="ad-row">
            <div class="ad-row-name">{{ u.name }}</div>
            <label class="ad-field ad-field-inline"><span>{{ t('adminDialog.baseCost') }}</span><input type="number" step="any" v-model.number="u.baseCost" /></label>
            <label class="ad-field ad-field-inline"><span>{{ t('adminDialog.effectPerLevel') }}</span><input type="number" step="any" v-model.number="u.effectPerLevel" /></label>
            <label class="ad-field ad-field-inline"><span>{{ t('adminDialog.maxLevel') }}</span><input type="number" step="1" v-model.number="u.maxLevel" /></label>
            <button class="px-btn px-btn-accent ad-save" :disabled="saving" @click="saveUpgrade(u)">{{ t('adminDialog.save') }}</button>
          </div>
        </section>

        <!-- ── Rezepte ───────────────────────────────────── -->
        <section class="ad-section">
          <div class="ad-section-head"><span>{{ t('adminDialog.recipesSection') }}</span></div>
          <div v-for="r in recipes" :key="r.id" class="ad-row ad-row-recipe">
            <div class="ad-row-name">{{ r.name }}</div>
            <label class="ad-field ad-field-inline"><span>{{ t('adminDialog.sugar') }}</span><input type="number" step="any" v-model.number="r.sugar" /></label>
            <label class="ad-field ad-field-inline"><span>{{ t('adminDialog.flour') }}</span><input type="number" step="any" v-model.number="r.flour" /></label>
            <label class="ad-field ad-field-inline"><span>{{ t('adminDialog.eggs') }}</span><input type="number" step="any" v-model.number="r.eggs" /></label>
            <label class="ad-field ad-field-inline"><span>{{ t('adminDialog.butter') }}</span><input type="number" step="any" v-model.number="r.butter" /></label>
            <label class="ad-field ad-field-inline"><span>{{ t('adminDialog.chocolate') }}</span><input type="number" step="any" v-model.number="r.chocolate" /></label>
            <label class="ad-field ad-field-inline"><span>{{ t('adminDialog.milk') }}</span><input type="number" step="any" v-model.number="r.milk" /></label>
            <label class="ad-field ad-field-inline"><span>{{ t('adminDialog.output') }}</span><input type="number" step="any" v-model.number="r.output" /></label>
            <label class="ad-field ad-field-inline"><span>{{ t('adminDialog.bakeDuration') }}</span><input type="number" step="1" v-model.number="r.bakeDurationSeconds" /></label>
            <button class="px-btn px-btn-accent ad-save" :disabled="saving" @click="saveRecipe(r)">{{ t('adminDialog.save') }}</button>
          </div>
        </section>

        <!-- ── Reset-Aktionen ────────────────────────────── -->
        <section class="ad-section">
          <div class="ad-section-head"><span>{{ t('adminDialog.resetSection') }}</span></div>
          <div class="ad-reset-row">
            <button class="px-btn px-btn-sell" @click="resetMarket">{{ t('adminDialog.resetMarket') }}</button>
            <button class="px-btn px-btn-sell" @click="resetPlayer">{{ t('adminDialog.resetPlayer') }}</button>
          </div>
        </section>
      </div>
      </PixelScrollBox>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePlayerStore } from '../stores/player.js'
import { useAudio } from '../composables/useAudio.js'
import LoadingIndicator from './pixel/LoadingIndicator.vue'
import PixelScrollBox from './pixel/PixelScrollBox.vue'
import {
  getAdminConfig, updateAdminMarketConfig, updateAdminBalanceConfig,
  getAdminUpgrades, updateAdminUpgrade,
  getAdminRecipes, updateAdminRecipe,
  adminResetMarket, adminResetPlayer,
} from '../services/api.js'

const emit = defineEmits(['close'])
const playerStore = usePlayerStore()
const audio = useAudio()
const { t } = useI18n()

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
  { key: 'updateIntervalMs',      labelKey: 'adminDialog.fieldTickInterval' },
  { key: 'minPrice',              labelKey: 'adminDialog.fieldMinPrice' },
  { key: 'stockFluctuationRatio', labelKey: 'adminDialog.fieldStockFluctuation' },
  { key: 'sellFeeRate',           labelKey: 'adminDialog.fieldSellFee' },
  { key: 'initialSugarPrice',     labelKey: 'adminDialog.fieldInitialSugarPrice' },
  { key: 'initialFlourPrice',     labelKey: 'adminDialog.fieldInitialFlourPrice' },
  { key: 'initialEggsPrice',      labelKey: 'adminDialog.fieldInitialEggsPrice' },
  { key: 'initialButterPrice',    labelKey: 'adminDialog.fieldInitialButterPrice' },
  { key: 'initialChocolatePrice', labelKey: 'adminDialog.fieldInitialChocolatePrice' },
  { key: 'initialMilkPrice',      labelKey: 'adminDialog.fieldInitialMilkPrice' },
  { key: 'initialSugarStock',     labelKey: 'adminDialog.fieldInitialSugarStock' },
  { key: 'initialFlourStock',     labelKey: 'adminDialog.fieldInitialFlourStock' },
  { key: 'initialEggsStock',      labelKey: 'adminDialog.fieldInitialEggsStock' },
  { key: 'initialButterStock',    labelKey: 'adminDialog.fieldInitialButterStock' },
  { key: 'initialChocolateStock', labelKey: 'adminDialog.fieldInitialChocolateStock' },
  { key: 'initialMilkStock',      labelKey: 'adminDialog.fieldInitialMilkStock' },
]

const balanceFields = [
  { key: 'baseStorageCap',            labelKey: 'adminDialog.fieldBaseStorageCap' },
  { key: 'storagePerLevel',           labelKey: 'adminDialog.fieldStoragePerLevel' },
  { key: 'citizensPerRatLevel',       labelKey: 'adminDialog.fieldCitizensPerRatLevel' },
  { key: 'citizenBaseCost',           labelKey: 'adminDialog.fieldCitizenBaseCost' },
  { key: 'citizenCostGrowth',         labelKey: 'adminDialog.fieldCitizenCostGrowth' },
  { key: 'workersPerLevel',           labelKey: 'adminDialog.fieldWorkersPerLevel' },
  { key: 'buildingCostGrowth',        labelKey: 'adminDialog.fieldBuildingCostGrowth' },
  { key: 'passiveTickSeconds',        labelKey: 'adminDialog.fieldPassiveTick' },
  { key: 'prestigeBaseThreshold',     labelKey: 'adminDialog.fieldPrestigeBaseThreshold' },
  { key: 'prestigeThresholdGrowth',   labelKey: 'adminDialog.fieldPrestigeThresholdGrowth' },
  { key: 'prestigeMultiplierPerLevel',labelKey: 'adminDialog.fieldPrestigeMultiplierPerLevel' },
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
    flash(e?.message ?? t('adminDialog.loadFailed'), true)
  } finally {
    loading.value = false
  }
}

async function saveMarket() {
  saving.value = true
  try {
    Object.assign(market, await updateAdminMarketConfig(market))
    flash(t('adminDialog.marketSaved'))
  } catch (e) { flash(e?.message ?? t('common.error'), true) }
  finally { saving.value = false }
}

async function saveBalance() {
  saving.value = true
  try {
    Object.assign(balance, await updateAdminBalanceConfig(balance))
    flash(t('adminDialog.balanceSaved'))
  } catch (e) { flash(e?.message ?? t('common.error'), true) }
  finally { saving.value = false }
}

async function saveUpgrade(u) {
  saving.value = true
  try {
    await updateAdminUpgrade(u.id, u)
    flash(t('adminDialog.itemSaved', { name: u.name }))
  } catch (e) { flash(e?.message ?? t('common.error'), true) }
  finally { saving.value = false }
}

async function saveRecipe(r) {
  saving.value = true
  try {
    await updateAdminRecipe(r.id, r)
    flash(t('adminDialog.itemSaved', { name: r.name }))
  } catch (e) { flash(e?.message ?? t('common.error'), true) }
  finally { saving.value = false }
}

async function resetMarket() {
  try { await adminResetMarket(); flash(t('adminDialog.marketReset')) }
  catch (e) { flash(e?.message ?? t('common.error'), true) }
}

async function resetPlayer() {
  try {
    await adminResetPlayer(playerStore.steamId)
    await playerStore.init(playerStore.steamId)
    flash(t('adminDialog.playerReset'))
  } catch (e) { flash(e?.message ?? t('common.error'), true) }
}
</script>

<style scoped>
.ad-box { width: 900px; max-width: 95vw; max-height: 88vh; overflow: hidden; display: flex; flex-direction: column; }
.ad-scroll { flex: 1 1 auto; min-height: 0; }
.ad-loading { padding: 24px; text-align: center; color: var(--px-tan-ink); }
.ad-body { padding: 16px 20px; display: flex; flex-direction: column; gap: 16px; }

.ad-notice { padding: 8px 12px; background: #fff1a9; border: 3px solid var(--px-green); color: #56642e; font-size: 13px; }
.ad-notice.error { background: #fff1a9; border-color: var(--px-red); color: var(--px-red-dk); }

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
