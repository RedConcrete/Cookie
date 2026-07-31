<template>
  <div class="butter-scene">
    <div class="roof"></div>
    <div class="wall"></div>
    <div class="door"></div>
    <div class="window window-l"></div>
    <div class="window window-r"></div>
    <div class="bench"></div>
    <div class="deck"></div>
    <div class="barrel"></div>
    <div class="barrel-ring"></div>
    <div class="milk-can"></div>
    <div class="milk-lid"></div>
    <div v-if="workers > 0" class="churner">
      <PixelWorker anim="bob" :dur="0.7" hat="#fffaf0" torso="#4a3f7a"
        :tool="{ anim: 'churn', dur: 0.7, color: '#a97b4a', top: '4px', height: '18px' }" />
    </div>
  </div>
</template>

<script setup>
import PixelWorker from '../pixel/PixelWorker.vue'

defineProps({ workers: { type: Number, default: 0 } })
</script>

<style scoped>
.butter-scene {
  position: absolute; inset: 0;
  background: #b98f57;
  background-image: repeating-linear-gradient(0deg, rgba(0,0,0,.1) 0 8px, transparent 8px 16px);
}
.roof { position: absolute; left: 26px; top: 10px; width: 98px; height: 12px; background: #e8b93c; box-shadow: 0 0 0 2px #3a2a1c; }
.wall { position: absolute; left: 30px; top: 22px; width: 90px; height: 58px; background: #fff8e0; box-shadow: 0 0 0 2px #3a2a1c; background-image: repeating-linear-gradient(0deg, rgba(0,0,0,.1) 0 6px, transparent 6px 12px); }
.door   { position: absolute; left: 69px; top: 62px; width: 12px; height: 18px; background: #8d6a3d; }
.window { position: absolute; top: 30px; width: 9px; height: 8px; background: #e8b93c; box-shadow: 0 0 0 2px #3a2a1c; }
.window-l { left: 35px; }
.window-r { left: 96px; }
.bench { position: absolute; left: 44px; top: 76px; width: 62px; height: 10px; background: #f3d060; box-shadow: 0 0 0 2px #3a2a1c; }
.deck  { position: absolute; left: 0; right: 0; bottom: 0; height: 22px; background: #a8823f; background-image: repeating-linear-gradient(90deg, rgba(0,0,0,.12) 0 10px, transparent 10px 20px); }
.barrel { position: absolute; left: 8px; bottom: 22px; width: 20px; height: 28px; background: #a97b4a; box-shadow: 0 0 0 2px #3a2a1c; background-image: repeating-linear-gradient(0deg, #8d6a3d 0 3px, transparent 3px 10px); }
.barrel-ring { position: absolute; left: 6px; bottom: 48px; width: 24px; height: 5px; background: #6b4f2a; box-shadow: 0 0 0 2px #3a2a1c; }
.milk-can { position: absolute; right: 10px; bottom: 24px; width: 14px; height: 20px; background: #cfe6f5; box-shadow: 0 0 0 2px #3a2a1c; }
.milk-lid { position: absolute; right: 14px; bottom: 43px; width: 6px; height: 6px; background: #9fb3c2; box-shadow: 0 0 0 2px #3a2a1c; }
.churner { position: absolute; left: 32px; bottom: 22px; z-index: 3; }
</style>
