<template>
  <div class="lang-picker">
    <div v-if="open" class="lang-picker-backdrop" @click="open = false"></div>

    <div v-if="open" class="lang-picker-menu px-panel">
      <button
        v-for="lang in SUPPORTED_LOCALES" :key="lang"
        class="lang-picker-item"
        :class="{ active: locale === lang }"
        @click="select(lang)"
      >
        <PixelIcon :name="`flag-${lang}`" :size="20" />
        <span>{{ lang.toUpperCase() }}</span>
      </button>
    </div>

    <button class="lang-picker-toggle px-btn" @click="open = !open" :aria-label="t('languagePicker.toggle')">
      <PixelIcon :name="`flag-${locale}`" :size="20" />
    </button>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { setLocale, SUPPORTED_LOCALES } from '../i18n/index.js'
import PixelIcon from './pixel/PixelIcon.vue'

const { t, locale } = useI18n()
const open = ref(false)

function select(lang) {
  setLocale(lang)
  open.value = false
}
</script>

<style scoped>
.lang-picker {
  position: fixed;
  right: 20px; bottom: 20px;
  z-index: 20;
}

.lang-picker-backdrop {
  position: fixed; inset: 0;
  z-index: 20;
}

.lang-picker-toggle {
  position: relative; z-index: 21;
  width: 44px; height: 44px;
  padding: 0;
  display: flex; align-items: center; justify-content: center;
}

.lang-picker-menu {
  position: absolute;
  right: 0; bottom: 52px;
  z-index: 21;
  display: flex; flex-direction: column;
  min-width: 96px;
  padding: 6px;
  gap: 4px;
}

.lang-picker-item {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 8px;
  background: transparent; border: none;
  font-family: 'Silkscreen', monospace; font-size: 12px;
  color: var(--px-ink-txt);
  cursor: pointer;
}
.lang-picker-item:hover { background: var(--px-wood-lt); }
.lang-picker-item.active { background: var(--px-gold); color: var(--px-ink); }
</style>
