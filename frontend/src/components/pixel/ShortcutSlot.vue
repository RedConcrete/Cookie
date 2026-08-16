<template>
  <span v-if="showController" class="px-shortcut-slot"><ControllerButtonIcon :index="gamepadButton" /></span>
  <span v-else-if="keyLabel" class="px-shortcut-slot">{{ keyLabel }}</span>
</template>

<script setup>
import { computed } from 'vue'
import ControllerButtonIcon from './ControllerButtonIcon.vue'
import { useInputMethod } from '../../composables/useInputMethod.js'

const props = defineProps({
  // Keybinding label to show in the corner badge, e.g. "U". Pass nothing and
  // the slot stays invisible for this button.
  keyLabel: { type: String, default: '' },
  // Gamepad button index (same numbering as GAMEPAD_BUTTON_LABELS in
  // useActionHotkeys.js) shown instead of keyLabel once a controller becomes
  // the active input method. Pass nothing to keep this badge keyboard-only.
  gamepadButton: { type: Number, default: null }
})

const input = useInputMethod()
const showController = computed(() => input.activeMethod.value === 'gamepad' && props.gamepadButton != null)
</script>
