<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="ld-panel">
      <div class="ld-head">
        <PixelIcon name="lager" :size="28" />
        <div class="ld-head-title">{{ t('lagerDialog.title', { level: lagerLevel }) }}</div>
        <button class="px-close" @click="emit('close')"><ShortcutSlot />&times;</button>
      </div>

      <div class="ld-body">
        <!-- Hauptlager: Gesamtkapazitaet, segmentiert nach Anteil je Rohstoff am
             aktuell gelagerten Gesamtbestand. Nur aus diesem Bestand kann verkauft
             werden -- Gebaeude-Lager (unten) ist ein davon getrennter Speicher. -->
        <div class="ld-total-row">
          <div class="ld-total-bar-col">
            <div class="ld-cap-label">{{ t('lagerDialog.capacityLabel') }}</div>
            <div class="ld-seg-bar">
              <div
                v-for="r in resourceRows" :key="r.key"
                class="ld-seg"
                :style="{ width: r.sharePct + '%', background: r.color }"
                :title="`${r.label}: ${r.amount.toFixed(0)}`"
              ></div>
            </div>
          </div>
          <div class="ld-total-side">
            <div class="ld-total-val">{{ fmtK(totalResources) }} / {{ fmtK(totalCapacity) }}</div>
            <div class="ld-total-free">{{ t('lagerDialog.freeLabel') }}: {{ freePct.toFixed(0) }}%</div>
          </div>
        </div>

        <!-- Per-resource rows -->
        <div class="ld-resources">
          <div v-for="r in resourceRows" :key="r.key" class="ld-res-row">
            <PixelIcon :name="r.icon" :size="16" />
            <div class="ld-res-name">{{ r.label }}</div>
            <div class="ld-res-bar-wrap">
              <div class="ld-res-bar">
                <div class="ld-res-fill" :style="{ width: r.pct + '%' }"></div>
              </div>
            </div>
            <div class="ld-res-val">{{ r.amount.toFixed(1) }}</div>
            <div class="ld-res-price">{{ fmt2(r.price) }} C</div>
          </div>
        </div>

        <!-- Gebaeude-Lager: sammelt unabhaengig vom Hauptlager an (auch wenn das
             oben voll ist), siehe BuildingService#settle -- hier einsammelbar, was
             ins Hauptlager uebernimmt (gedeckelt auf dessen freien Platz je Rohstoff). -->
        <div class="ld-buildings">
          <div class="ld-section-title">{{ t('lagerDialog.buildingsTitle') }}</div>
          <div v-if="buildingRows.length === 0" class="ld-bres-empty">{{ t('lagerDialog.noBuildings') }}</div>
          <div v-for="b in buildingRows" :key="b.id" class="ld-bres-row">
            <PixelIcon :name="b.icon" :size="16" />
            <div class="ld-res-name">{{ b.title }}</div>
            <div class="ld-res-bar-wrap">
              <div class="ld-res-bar">
                <div class="ld-res-fill" :style="{ width: b.pct + '%' }"></div>
              </div>
            </div>
            <div class="ld-res-val">{{ fmt(b.pending) }} / {{ fmtK(b.cap) }}</div>
            <button
              class="px-btn px-btn-accent ld-bres-collect"
              :disabled="b.pending <= 0 || collectingIds.has(b.id)"
              @click="collectBuildingRow(b)"
            >{{ t('lagerDialog.collectBtn') }}</button>
          </div>
        </div>

        <!-- Wage info -->
        <div class="ld-info">
          <div class="ld-info-text">
            {{ t('lagerDialog.wageLine', { level: lagerLevel, wage: lagerWage.toFixed(1) }) }}
            <span v-if="lagerLevel === 1" class="ld-free">{{ t('lagerDialog.free') }}</span>
          </div>
        </div>

        <!-- Upgrade -->
        <div class="ld-upgrade">
          <div class="ld-upgrade-info">
            <div class="ld-upgrade-label">{{ t('lagerDialog.upgradeLevelLine', { from: lagerLevel, to: lagerLevel + 1 }) }}</div>
            <div class="ld-upgrade-desc">{{ t('lagerDialog.upgradeDesc', { bonus: fmtK(storageBonus) }) }} {{ upgradeCost.toFixed(0) }} <PixelIcon name="cookie" :size="12" style="vertical-align:-2px" /></div>
          </div>
          <button
            class="px-btn px-btn-accent"
            :disabled="upgrading || playerStore.cookies < upgradeCost"
            @click="upgradeLager"
          ><ShortcutSlot />{{ t('lagerDialog.upgradeBtn') }}</button>
        </div>
        <div v-if="notice" class="ld-notice" :class="{ error: noticeError }">{{ notice }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePlayerStore } from '../stores/player.js'
import { useMarketStore } from '../stores/market.js'
import { buyBuilding, collectBuilding } from '../services/api.js'
import { fmt, fmt2 } from '../utils/formatNumber.js'
import { useAudio } from '../composables/useAudio.js'
import { BUILDING_INFO, buildingTitle } from './buildings/buildingInfo.js'
import PixelIcon from './pixel/PixelIcon.vue'
import ShortcutSlot from './pixel/ShortcutSlot.vue'

const emit = defineEmits(['close'])
const playerStore = usePlayerStore()
const marketStore = useMarketStore()
const audio = useAudio()
const { t } = useI18n()

onMounted(() => audio.playBookOpen())

const upgrading   = ref(false)
const notice      = ref('')
const noticeError = ref(false)

const lagerData    = computed(() => playerStore.ownedBuildings.find(b => b.id === 'lager'))
const lagerLevel   = computed(() => lagerData.value?.level ?? 1)
const upgradeCost  = computed(() => lagerData.value?.nextLevelCost ?? 800)
const storageBonus = computed(() => lagerData.value?.storageCapBonus ?? 1000)
const lagerWage    = computed(() => lagerData.value?.wagePerMin ?? 0)

// Muss mit COLORS in MarketView.vue / RESOURCE_COLORS in PriceChart.vue uebereinstimmen --
// dieselbe Rohstoff-Farbe soll im Preis-Chart wie im Lager-Balken wiederzuerkennen sein.
const RESOURCES = [
  { key: 'sugar',     labelKey: 'lagerDialog.resSugar',     icon: 'zucker', name: 'SUGAR',     color: '#e67146' },
  { key: 'flour',     labelKey: 'lagerDialog.resFlour',     icon: 'mehl',   name: 'FLOUR',     color: '#2a7d75' },
  { key: 'eggs',      labelKey: 'lagerDialog.resEggs',      icon: 'eier',   name: 'EGGS',      color: '#349c58' },
  { key: 'butter',    labelKey: 'lagerDialog.resButter',    icon: 'butter', name: 'BUTTER',    color: '#c9c03d' },
  { key: 'chocolate', labelKey: 'lagerDialog.resChocolate', icon: 'schoko', name: 'CHOCOLATE', color: '#e67a84' },
  { key: 'milk',      labelKey: 'lagerDialog.resMilk',      icon: 'milch',  name: 'MILK',      color: '#6f6e72' },
]

const totalResources = computed(() =>
  RESOURCES.reduce((s, r) => s + (playerStore[r.key] ?? 0), 0)
)

const resourceRows = computed(() => RESOURCES.map(r => {
  const amount = playerStore[r.key] ?? 0
  return {
    ...r,
    label: t(r.labelKey),
    amount,
    pct: Math.min(100, (amount / playerStore.totalResourceCap) * 100),
    sharePct: totalResources.value > 0 ? (amount / totalResources.value) * 100 : 0,
    price: marketStore.priceOf(r.name),
  }
}))

// Cap gilt pro Rohstoff, nicht als gemeinsamer Topf (siehe UserService#harvest) -- die
// Gesamtkapazitaet fuer diese Uebersicht ist deshalb der Deckel mal Rohstoffanzahl, sonst
// zeigt der Balken "voll" schon bei einem Bruchteil der echten Kapazitaet, obwohl einzelne
// Rohstoffe (und damit deren Hover-Ernte) noch laengst nicht am Limit sind.
const totalCapacity = computed(() => playerStore.totalResourceCap * RESOURCES.length)
const totalPct = computed(() =>
  totalCapacity.value > 0 ? Math.min(100, (totalResources.value / totalCapacity.value) * 100) : 0
)
const freePct = computed(() => Math.max(0, 100 - totalPct.value))

// Live-Ticker fuer die Gebaeude-Lager-Extrapolation (lokale Vorschau zwischen echten
// Server-Snapshots) -- mirrors BuildingDetailDialog's nowTick.
const nowTick = ref(Date.now())
let tickTimer = null
onMounted(() => { tickTimer = setInterval(() => { nowTick.value = Date.now() }, 1000) })
onUnmounted(() => clearInterval(tickTimer))

function isResourceFull(resourceName) {
  if (!resourceName) return false
  return (playerStore[resourceName.toLowerCase()] ?? 0) >= playerStore.totalResourceCap
}

function livePending(owned, resourceName) {
  if (!owned || !owned.storageCapacity) return 0
  if (playerStore.workersIdle || isResourceFull(resourceName)) return owned.pendingAmount ?? 0
  const elapsedSeconds = Math.max(0, (nowTick.value - (owned.lastSettledAtEpochMs || nowTick.value)) / 1000)
  return Math.min(owned.storageCapacity, (owned.pendingAmount ?? 0) + (owned.passiveRatePerSec ?? 0) * elapsedSeconds)
}

// Produktionsgebaeude (die 6 mit eigenem Rohstoff) mit ihrem jeweils eigenen, vom
// Hauptlager unabhaengigen Bestand -- siehe ld-buildings im Template.
const buildingRows = computed(() => Object.entries(BUILDING_INFO)
  .filter(([, def]) => def.resource)
  .map(([id, def]) => {
    const owned = playerStore.ownedBuildings.find(b => b.id === id)
    if (!owned || owned.level === 0) return null
    const cap = owned.storageCapacity ?? 0
    const pending = livePending(owned, def.resource)
    return {
      id, icon: def.icon, title: buildingTitle(id, t),
      pending, cap, pct: cap > 0 ? Math.min(100, (pending / cap) * 100) : 0,
    }
  })
  .filter(Boolean))

const collectingIds = reactive(new Set())
async function collectBuildingRow(b) {
  if (collectingIds.has(b.id) || b.pending <= 0) return
  collectingIds.add(b.id)
  try {
    const updated = await collectBuilding(playerStore.steamId, b.id)
    playerStore.updateFromDto(updated)
    await playerStore.loadBuildings()
  } catch (e) {
    // Cooldown (400) oder Optimistic-Lock-Konflikt (409) -- einfach ignorieren, naechster
    // Klick nach Ablauf geht wieder durch (siehe FarmGridView#onCollectBuilding).
    if (e?.status !== 400 && e?.status !== 409) {
      notice.value = t('lagerDialog.errorCollecting')
      noticeError.value = true
      setTimeout(() => { notice.value = '' }, 2000)
    }
  } finally {
    collectingIds.delete(b.id)
  }
}

function fmtK(v) {
  if (v >= 1000) return (v / 1000).toFixed(1) + 'K'
  return Number(v).toFixed(0)
}

async function upgradeLager() {
  if (upgrading.value) return
  upgrading.value = true
  notice.value = ''
  try {
    const updated = await buyBuilding(playerStore.steamId, 'lager')
    playerStore.ownedBuildings.splice(0, playerStore.ownedBuildings.length, ...updated)
    // Refresh cap in store
    const newLager = updated.find(b => b.id === 'lager')
    if (newLager) playerStore.totalResourceCap = 100 + newLager.level * 1000
    notice.value = t('lagerDialog.upgradeSuccess', { level: lagerLevel.value })
    noticeError.value = false
  } catch {
    notice.value = t('lagerDialog.notEnoughCookies')
    noticeError.value = true
  } finally {
    upgrading.value = false
    setTimeout(() => { notice.value = '' }, 2500)
  }
}
</script>

<style scoped>
.ld-panel {
  width: 520px; max-width: 96vw;
  background: var(--px-cream2); border: 4px solid var(--px-ink);
  box-shadow: inset -3px -3px 0 var(--px-tan), inset 3px 3px 0 var(--px-cream), 0 10px 0 rgba(0,0,0,.45);
}
.ld-head {
  display: flex; align-items: center; gap: 12px; padding: 14px 18px;
  border-bottom: 4px solid var(--px-ink); background: var(--px-cream3);
}
.ld-head-title { font-family: 'Silkscreen', monospace; font-size: 13px; color: var(--px-ink-txt); flex: 1; }

.ld-body { padding: 18px; display: flex; flex-direction: column; gap: 14px; }

.ld-total-row { display: flex; align-items: flex-end; gap: 12px; }
.ld-total-bar-col { flex: 1; min-width: 0; }
.ld-cap-label { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-tan-hd); margin-bottom: 4px; }
.ld-seg-bar { height: 14px; background: var(--px-ink); border: 3px solid var(--px-ink); display: flex; overflow: hidden; }
.ld-seg { height: 100%; }
.ld-total-side { flex-shrink: 0; text-align: right; }
.ld-total-val  { font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-paper-txt); }
.ld-total-free { font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-green-txt); margin-top: 2px; }

.ld-resources { display: flex; flex-direction: column; gap: 6px; }
.ld-res-row { display: flex; align-items: center; gap: 8px; padding: 6px 8px; background: var(--px-cream); border: 3px solid var(--px-brown2); }
.ld-res-name { font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-ink-txt); min-width: 68px; }
.ld-res-bar-wrap { flex: 1; }
.ld-res-bar { height: 8px; background: var(--px-ink); border: 2px solid var(--px-ink); }
.ld-res-fill { height: 100%; background: var(--px-gold); }
.ld-res-val   { font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-gold-txt); min-width: 48px; text-align: right; }
.ld-res-price { font-family: 'Silkscreen', monospace; font-size: 8px; color: var(--px-muted); min-width: 52px; text-align: right; }

.ld-buildings { display: flex; flex-direction: column; gap: 6px; }
.ld-section-title { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-tan-hd); }
.ld-bres-empty { font-size: 11px; color: var(--px-muted); padding: 4px 2px; }
.ld-bres-row { display: flex; align-items: center; gap: 8px; padding: 6px 8px; background: var(--px-cream); border: 3px solid var(--px-brown2); }
.ld-bres-collect {
  font-family: 'Silkscreen', monospace; font-size: 8px; padding: 5px 8px; flex-shrink: 0;
}

.ld-info { padding: 10px 12px; background: #fff1a9; border: 3px solid var(--px-orange); font-size: 13px; color: var(--px-wood-lt); line-height: 1.5; }
.ld-info-text  { font-size: 12px; }
.ld-free { color: var(--px-green-txt); font-weight: bold; }

.ld-upgrade { display: flex; align-items: center; gap: 12px; padding: 12px; background: var(--px-cream3); border: 3px solid var(--px-brown2); }
.ld-upgrade-info { flex: 1; }
.ld-upgrade-label { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-gold); }
.ld-upgrade-desc  { font-size: 13px; color: var(--px-tan-ink); margin-top: 3px; }
.ld-notice { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-green-txt); }
.ld-notice.error  { color: var(--px-red); }
</style>
