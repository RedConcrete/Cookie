<template>
  <span v-if="keyLabel" class="px-shortcut-slot">{{ keyLabel }}</span>
</template>

<script setup>
defineProps({
  // Keybinding label to show in the corner badge, e.g. "U". No keybinding
  // system exists yet — pass nothing and the slot stays invisible until
  // one is wired up for this button.
  keyLabel: { type: String, default: '' }
})
</script>
