<template>
  <div class="menu-bg"></div>
  <div
    v-for="(w, i) in wanderers" :key="i"
    class="menu-wanderer" :style="w.outerStyle"
  >
    <div :style="w.scaleStyle">
      <TravelingWorker
        :travel-anim="w.anim" :travel-dur="w.dur" :travel-delay="w.delay"
        :leg-dur="w.legDur" :hat="w.hat" :skin="w.skin" :torso="w.torso"
      />
    </div>
  </div>
</template>

<script setup>
import TravelingWorker from './buildings/TravelingWorker.vue'

const ANIMS = ['patrol', 'trek', 'wander', 'wander2', 'wander3', 'rowwalk', 'commute']
const HATS   = ['#c78539', '#e67146', '#349c58', '#764032']
const SKINS  = ['#fff1a9', '#ebb85b']
const TORSOS = ['#a15c34', '#6f6e72', '#349c58']

function pick(arr, i) { return arr[i % arr.length] }

const wanderers = Array.from({ length: 7 }, (_, i) => {
  const scale = 2.4 + (i % 3) * 0.5
  return {
    anim: pick(ANIMS, i),
    dur: 6 + (i % 4) * 2,
    delay: i * 0.7,
    legDur: 0.45 + (i % 3) * 0.1,
    hat: pick(HATS, i),
    skin: pick(SKINS, i),
    torso: pick(TORSOS, i + 1),
    outerStyle: {
      position: 'absolute',
      left: `${8 + (i * 13) % 84}%`,
      top: `${20 + (i * 17) % 65}%`,
      zIndex: 1,
    },
    scaleStyle: { transform: `scale(${scale})`, transformOrigin: 'bottom center' },
  }
})
</script>

<style scoped>
.menu-bg {
  position: absolute; inset: 0;
  background-color: #7e9432;
  background-image: url('../assets/tiles/grass.png');
  background-size: 100px 100px;
  image-rendering: pixelated;
}

.menu-wanderer { pointer-events: none; opacity: 0.9; }
</style>
