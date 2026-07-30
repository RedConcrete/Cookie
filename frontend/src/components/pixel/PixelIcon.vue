<template>
  <div class="pixel-icon" :style="{ width: size + 'px', height: size + 'px' }">
    <div class="pixel-icon-grid" :style="{ width: unit + 'px', height: unit + 'px', boxShadow: shadow }"></div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { iconBoxShadow } from './iconData.js'

const props = defineProps({
  name: { type: String, required: true }, // key into GRID/PAL, e.g. 'zucker', 'cookie', 'einw'
  size: { type: Number, default: 16 },     // rendered box size in px (8 grid cells)
})

const unit = computed(() => props.size / 8)
const shadow = computed(() => iconBoxShadow(props.name, unit.value))
</script>

<style scoped>
.pixel-icon { position: relative; flex-shrink: 0; }
.pixel-icon-grid { position: absolute; left: 0; top: 0; }
</style>
