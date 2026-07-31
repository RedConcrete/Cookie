<template>
  <div >
    <img class="scene-bg" :src="bgSrc" alt="" />
  </div>
</template>

<script setup>
import bgSrc from '../../assets/buildings/rathaus.svg'
</script>

<style scoped>
.scene-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; image-rendering: pixelated; }
</style>
