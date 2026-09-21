<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="bsd-box px-panel" :style="dialogStyle">
      <div class="px-titlebar" @pointerdown="onDragStart">
        <span>{{ t('buildShareDialog.title') }}</span>
        <button class="px-close" @click="emit('close')"><ShortcutSlot />&times;</button>
      </div>

      <div class="bsd-tabs">
        <button class="bsd-tab" :class="{ active: activeTab === 'export' }" @click="activeTab = 'export'">{{ t('buildShareDialog.tabExport') }}</button>
        <button class="bsd-tab" :class="{ active: activeTab === 'import' }" @click="activeTab = 'import'">{{ t('buildShareDialog.tabImport') }}</button>
      </div>

      <!-- ── Export: reiner Frontend-Vorgang, kein Server-Call ── -->
      <div v-if="activeTab === 'export'" class="bsd-body">
        <textarea
          class="bsd-code-box" readonly :value="exportCode"
          @click="$event.target.select()"
        ></textarea>
        <button class="px-btn px-btn-accent" @click="copyExportCode">
          <ShortcutSlot />{{ t('buildShareDialog.copyLabel') }}
        </button>
        <div class="bsd-meta">
          {{ t('buildShareDialog.exportMeta', {
            season: tree.activeSeasonName || t('buildShareDialog.noSeason'),
            author: playerStore.displayName || t('buildShareDialog.anonymous'),
          }) }}
        </div>
      </div>

      <!-- ── Import: Vorschau (Dry-Run) MUSS vor der echten Anwendung bestaetigt werden --
           Import bewegt Skillpunkte/Cookies, braucht denselben Schutz wie HardResetDialog. ── -->
      <div v-else class="bsd-body">
        <textarea
          class="bsd-code-input" v-model="importCodeInput"
          :placeholder="t('buildShareDialog.importPlaceholder')"
          @input="onImportCodeInput"
        ></textarea>
        <div v-if="importCodeInvalid" class="bsd-error">{{ t('buildShareDialog.invalidCodeNotice') }}</div>
        <div v-else-if="importDecoded" class="bsd-meta">
          {{ t('buildShareDialog.importMeta', {
            season: importDecoded.season || t('buildShareDialog.noSeason'),
            author: importDecoded.author || t('buildShareDialog.anonymous'),
          }) }}
        </div>

        <button
          class="px-btn" :disabled="!importDecoded || previewing"
          @click="previewImport"
        >{{ t('buildShareDialog.previewLabel') }}</button>

        <div v-if="previewResult" class="bsd-preview">
          <div v-if="previewResult.unknownNodeIds.length" class="bsd-warning">
            {{ t('buildShareDialog.unknownNodesWarning', { count: previewResult.unknownNodeIds.length }) }}
          </div>
          <div class="bsd-preview-row">
            {{ t('buildShareDialog.previewDiff', { added: previewResult.nodesAdded, removed: previewResult.nodesRemoved }) }}
          </div>
          <div class="bsd-preview-row" :class="{ 'bsd-error': !previewResult.canAfford }">
            {{ t('buildShareDialog.previewCost') }}: {{ fmt(previewResult.totalCost) }}<PixelIcon name="cookie" :size="12" style="margin-left:4px;vertical-align:-2px" />
          </div>
          <button
            class="px-btn px-btn-accent" :disabled="!previewResult.canAfford || applying"
            @click="applyImport"
          ><ShortcutSlot />{{ t('buildShareDialog.applyLabel') }}</button>
        </div>
      </div>

      <div v-if="notice" class="bsd-notice" :class="{ error: noticeError }">{{ notice }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePlayerStore } from '../stores/player.js'
import { importSkillBuild, getPlayer } from '../services/api.js'
import { fmt2 as fmt } from '../utils/formatNumber.js'
import PixelIcon from './pixel/PixelIcon.vue'
import ShortcutSlot from './pixel/ShortcutSlot.vue'
import { useDraggableDialog } from '../composables/useDraggableDialog.js'

const emit = defineEmits(['close'])
const { t } = useI18n()
const playerStore = usePlayerStore()
const { dialogStyle, onDragStart } = useDraggableDialog()

const tree = computed(() => playerStore.skillTree)
const activeTab = ref('export')

const notice = ref('')
const noticeError = ref(false)
function flash(msg, isError = false) {
  notice.value = msg
  noticeError.value = isError
  setTimeout(() => { notice.value = '' }, 2500)
}

// UTF-8-sicheres Base64 (Season-/Anzeigenamen koennen Umlaute etc. enthalten) -- kein Server-
// Roundtrip fuers reine Encodieren, die Node-Auswahl liegt schon im Store (SkillNodeStatusDto).
function encodeBuildCode(payload) {
  return btoa(unescape(encodeURIComponent(JSON.stringify(payload))))
}
function decodeBuildCode(code) {
  return JSON.parse(decodeURIComponent(escape(atob(code))))
}

const exportCode = computed(() => {
  const nodeIds = tree.value.nodes.filter(n => n.allocated && !n.root).map(n => n.id)
  return encodeBuildCode({
    nodeIds,
    season: tree.value.activeSeasonName || null,
    author: playerStore.displayName || null,
  })
})

async function copyExportCode() {
  try {
    await navigator.clipboard.writeText(exportCode.value)
    flash(t('buildShareDialog.copiedNotice'))
  } catch {
    flash(t('buildShareDialog.copyFailedNotice'), true)
  }
}

// ── Import ── Meta-Infos (season/author) aus dem Code sind reine Anzeige, fliessen NICHT in
// die Server-Validierung ein -- die prueft ausschliesslich die Node-ID-Liste (siehe Plan).
const importCodeInput = ref('')
const importDecoded = ref(null)
const importCodeInvalid = ref(false)
const previewResult = ref(null)
const previewing = ref(false)
const applying = ref(false)

function onImportCodeInput() {
  previewResult.value = null
  const raw = importCodeInput.value.trim()
  if (!raw) { importDecoded.value = null; importCodeInvalid.value = false; return }
  try {
    const parsed = decodeBuildCode(raw)
    if (!Array.isArray(parsed.nodeIds)) throw new Error('invalid build code')
    importDecoded.value = parsed
    importCodeInvalid.value = false
  } catch {
    importDecoded.value = null
    importCodeInvalid.value = true
  }
}

async function previewImport() {
  if (!importDecoded.value || previewing.value) return
  previewing.value = true
  previewResult.value = null
  try {
    previewResult.value = await importSkillBuild(playerStore.steamId, importDecoded.value.nodeIds, true)
  } catch (e) {
    flash(e.message, true)
  } finally {
    previewing.value = false
  }
}

async function applyImport() {
  if (!importDecoded.value || applying.value) return
  applying.value = true
  try {
    const result = await importSkillBuild(playerStore.steamId, importDecoded.value.nodeIds, false)
    playerStore.skillTree = result.tree
    playerStore.updateFromDto(await getPlayer(playerStore.steamId))
    flash(t('buildShareDialog.appliedNotice', { added: result.nodesAdded, removed: result.nodesRemoved }))
    previewResult.value = null
    importCodeInput.value = ''
    importDecoded.value = null
  } catch (e) {
    flash(e.message, true)
  } finally {
    applying.value = false
  }
}
</script>

<style scoped>
.bsd-box { width: 440px; max-width: 92vw; }

.bsd-tabs { display: flex; gap: 6px; padding: 14px 20px 0; }
.bsd-tab {
  flex: 1; padding: 8px; border: 3px solid var(--px-brown2); background: var(--px-cream);
  font-family: 'Silkscreen', monospace; font-size: 10px; letter-spacing: 1px; color: var(--px-tan-ink);
  cursor: pointer;
}
.bsd-tab.active { background: var(--px-gold); border-color: var(--px-ink); color: var(--px-ink-txt); }

.bsd-body { padding: 16px 20px 20px; display: flex; flex-direction: column; gap: 10px; }

.bsd-code-box, .bsd-code-input {
  width: 100%; height: 80px; resize: vertical; box-sizing: border-box;
  font-family: 'Silkscreen', monospace; font-size: 11px; line-height: 1.4;
  background: var(--px-cream); color: var(--px-ink); border: 2px solid var(--px-ink);
  padding: 8px; word-break: break-all;
}
.bsd-code-box { cursor: text; }

.bsd-meta { font-size: 11px; color: var(--px-tan-ink); font-style: italic; }
.bsd-error { font-size: 12px; color: var(--px-red-dk); }
.bsd-warning { font-size: 12px; color: var(--px-red-dk); }

.bsd-preview {
  display: flex; flex-direction: column; gap: 8px;
  padding: 10px; background: rgba(16,11,7,.06); border: 2px solid var(--px-ink);
}
.bsd-preview-row { font-size: 13px; color: var(--px-ink-txt); }
.bsd-preview-row.bsd-error { color: var(--px-red-dk); font-weight: 700; }

.bsd-notice {
  margin: 0 20px 16px; padding: 9px 16px;
  background: var(--px-cream); border: 3px solid var(--px-green);
  color: #56642e; font-size: 13px;
}
.bsd-notice.error { border-color: var(--px-red); color: var(--px-red-dk); }
</style>
