<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop>
    <div class="orden-wrap">

      <div class="orden-box px-panel">
        <div class="px-titlebar">
          <span>ORDEN &middot; {{ steamId }}</span>
          <button class="px-close" @click="emit('close')">&times;</button>
        </div>
        <div class="orden-filters">
          <button class="orden-filter" :class="{ active: filter === 'ALL' }" @click="filter = 'ALL'">ALLE {{ badges.length }}</button>
          <button class="orden-filter" :class="{ active: filter === 'EVENT' }" @click="filter = 'EVENT'">EVENTS {{ countOf('EVENT') }}</button>
          <button class="orden-filter" :class="{ active: filter === 'COMMUNITY' }" @click="filter = 'COMMUNITY'">COMMUNITY {{ countOf('COMMUNITY') }}</button>
          <span class="orden-sort-hint">neueste zuerst</span>
        </div>
        <div class="orden-list px-scroll">
          <div v-for="m in filtered" :key="m.id" class="orden-item">
            <div class="orden-plate" :style="{ background: m.color }">
              <PixelIcon :name="m.icon" :size="32" />
            </div>
            <div>
              <div class="orden-name">{{ m.name }}</div>
              <div class="orden-desc">{{ m.desc }}</div>
            </div>
            <div class="orden-date">
              <div class="orden-date-label">ERHALTEN</div>
              <div class="orden-date-val">{{ fmtDate(m.awardedOn) }}</div>
            </div>
          </div>
          <div v-if="!filtered.length" class="orden-empty">Keine Orden in dieser Kategorie.</div>
        </div>
      </div>

      <div class="orden-box px-panel">
        <div class="px-titlebar admin-bar">
          <span>ADMIN &middot; ORDEN VERLEIHEN</span>
          <span class="orden-admin-badge">NUR ADMIN</span>
        </div>
        <div class="orden-admin-body px-scroll">
          <div>
            <div class="orden-label">SPIELER</div>
            <div class="orden-search">
              <input v-model="playerQuery" class="orden-search-input" placeholder="Steam-Name" />
              <button class="px-btn px-btn-flat">SUCHEN</button>
            </div>
          </div>

          <div>
            <div class="orden-label">ORDEN-VORLAGE</div>
            <div class="orden-templates">
              <button
                v-for="t in templates" :key="t.id" class="orden-template"
                :class="{ active: selectedTemplate === t.id }" @click="selectedTemplate = t.id"
              >
                <PixelIcon :name="t.icon" :size="24" />
                <span>{{ t.name }}</span>
              </button>
            </div>
          </div>

          <div>
            <div class="orden-label">EVENT / BEGRÜNDUNG</div>
            <input v-model="note" class="orden-note" placeholder="z.B. Sommer-Sprint · 1. Platz" />
          </div>

          <div class="orden-date-row">
            <span>Datum</span>
            <input v-model="grantDate" type="date" class="orden-date-input" />
            <span class="orden-date-hint">wird im Profil angezeigt</span>
          </div>

          <div class="orden-admin-actions">
            <button class="px-btn px-btn-buy" :disabled="!selectedTemplate" @click="grantBadge">VERLEIHEN</button>
            <button class="px-btn px-btn-flat">ENTZIEHEN</button>
          </div>

          <div class="orden-hint">Verliehene Orden landen sofort in der Vitrine des Spielers, mit Datum und Event-Namen. Vorlagen sind einbindbar, damit Event-Gewinner in wenigen Klicks ausgezeichnet werden.</div>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import PixelIcon from './pixel/PixelIcon.vue'
import { useBadges } from '../composables/useBadges.js'
import { useAudio } from '../composables/useAudio.js'

const props = defineProps({ steamId: { type: String, default: 'DEV_PLAYER_001' } })
const emit = defineEmits(['close'])
const audio = useAudio()

onMounted(() => audio.playBookOpen())

const { badges, templates, grant } = useBadges()

const filter = ref('ALL')
const playerQuery = ref('')
const selectedTemplate = ref(null)
const note = ref('')
const grantDate = ref(new Date().toISOString().slice(0, 10))

function countOf(cat) { return badges.filter(b => b.category === cat).length }
const filtered = computed(() => filter.value === 'ALL' ? badges : badges.filter(b => b.category === filter.value))

function fmtDate(iso) {
  const [y, m, d] = iso.split('-')
  return `${d}.${m}.${y}`
}

function grantBadge() {
  if (!selectedTemplate.value) return
  grant(selectedTemplate.value, note.value, grantDate.value)
  note.value = ''
  selectedTemplate.value = null
}
</script>

<style scoped>
.orden-wrap { display: flex; gap: 24px; align-items: stretch; max-width: 95vw; }
.orden-box { width: 520px; max-height: 85vh; display: flex; flex-direction: column; }

.orden-filters { display: flex; align-items: center; gap: 10px; padding: 12px 16px; background: var(--px-cream3); border-bottom: 4px solid var(--px-ink); }
.orden-filter { font-family: 'Silkscreen', monospace; font-size: 10px; padding: 5px 8px; background: var(--px-cream3); border: 3px solid var(--px-tan); color: var(--px-tan-ink); cursor: pointer; }
.orden-filter.active { background: var(--px-cream); border-color: var(--px-brown2); color: var(--px-ink-txt); }
.orden-sort-hint { margin-left: auto; font-size: 13px; color: var(--px-tan-ink); }

.orden-list { flex: 1; min-height: 0; overflow-y: auto; padding: 14px 16px; display: flex; flex-direction: column; gap: 10px; }
.orden-item { display: grid; grid-template-columns: 56px 1fr 116px; gap: 14px; align-items: center; padding: 12px; background: var(--px-cream2); border: 3px solid var(--px-brown2); }
.orden-item:hover { background: #fff3c4; }
.orden-plate { width: 48px; height: 48px; border: 3px solid var(--px-ink); box-shadow: inset -2px -2px 0 rgba(0,0,0,.25), inset 2px 2px 0 rgba(255,255,255,.35); display: flex; align-items: center; justify-content: center; }
.orden-name { font-size: 17px; font-weight: 600; color: var(--px-ink-txt); }
.orden-desc { font-size: 14px; color: var(--px-tan-ink); margin-top: 2px; }
.orden-date { text-align: right; }
.orden-date-label { font-size: 11px; color: var(--px-tan-ink); }
.orden-date-val   { font-family: 'Silkscreen', monospace; font-size: 12px; color: var(--px-ink-txt); margin-top: 4px; }
.orden-empty { color: var(--px-tan-ink); text-align: center; padding: 20px; font-style: italic; }

.admin-bar { background: #5a2a2a; }
.orden-admin-badge { font-family: 'Silkscreen', monospace; font-size: 9px; padding: 4px 7px; background: var(--px-red); color: #fff6e0; border: 3px solid var(--px-ink); }
.orden-admin-body { padding: 18px; display: flex; flex-direction: column; gap: 14px; overflow-y: auto; }
.orden-label { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-tan-hd); letter-spacing: 1px; margin-bottom: 7px; }

.orden-search { display: flex; border: 3px solid var(--px-ink); }
.orden-search-input { flex: 1; padding: 10px 12px; background: var(--px-cream2); border: none; font-size: 15px; color: var(--px-ink-txt); }
.orden-search-input:focus { outline: none; }

.orden-templates { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 8px; }
.orden-template { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 12px 6px; background: var(--px-cream2); border: 3px solid var(--px-brown2); cursor: pointer; }
.orden-template:hover { background: #fff3c4; }
.orden-template.active { background: var(--px-gold); border-color: var(--px-ink); }
.orden-template span { font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-ink-txt); text-align: center; }

.orden-note { padding: 10px 12px; background: var(--px-cream2); border: 3px solid var(--px-brown2); font-size: 15px; color: var(--px-ink-txt); width: 100%; }
.orden-note:focus { outline: none; }

.orden-date-row { display: flex; align-items: center; gap: 10px; font-size: 15px; color: var(--px-ink-txt); }
.orden-date-input { padding: 8px 10px; background: var(--px-cream2); border: 3px solid var(--px-brown2); font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-ink-txt); }
.orden-date-hint { margin-left: auto; font-size: 13px; color: var(--px-tan-ink); }

.orden-admin-actions { display: flex; gap: 10px; }
.orden-admin-actions .px-btn { flex: 1; text-align: center; }
.orden-hint { padding: 12px 14px; background: #fff3c4; border: 3px solid var(--px-orange); font-size: 14px; line-height: 1.55; color: var(--px-wood-lt); }

@media (max-width: 1100px) {
  .orden-wrap { flex-direction: column; }
  .orden-box { width: min(560px, 90vw); }
}
</style>
