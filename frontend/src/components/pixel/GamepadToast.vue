<template>
  <Transition name="gp-toast-fade">
    <div v-if="visible" class="gp-toast">{{ t('gamepadToast.connected', { family: familyLabel }) }}</div>
  </Transition>
</template>

<script setup>
import { ref, computed, watch, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useInputMethod } from '../../composables/useInputMethod.js'

const { t } = useI18n()
const input = useInputMethod()

const visible = ref(false)
let hideTimer = null

const familyLabel = computed(() => t(`gamepadToast.family.${input.controllerFamily.value}`))

// Watches connectNonce (not controllerFamily) so re-plugging the same
// controller re-triggers the toast instead of a no-op on an unchanged value.
// No appear-delay -- shows the instant a connect fires, just a brief
// auto-dismiss afterwards (not a hover-tooltip, doesn't need one).
watch(() => input.connectNonce.value, () => {
  visible.value = true
  clearTimeout(hideTimer)
  hideTimer = setTimeout(() => { visible.value = false }, 2500)
})

onUnmounted(() => clearTimeout(hideTimer))
</script>

<style scoped>
.gp-toast {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 601;
  font-family: 'Silkscreen', monospace;
  font-size: 12px;
  padding: 10px 16px;
  background: var(--px-wood);
  border: 3px solid var(--px-ink);
  color: var(--px-cream);
  box-shadow: 0 4px 0 rgba(0,0,0,.4);
  pointer-events: none;
  white-space: nowrap;
}
.gp-toast-fade-enter-active, .gp-toast-fade-leave-active { transition: opacity .15s ease; }
.gp-toast-fade-enter-from, .gp-toast-fade-leave-to { opacity: 0; }
</style>
