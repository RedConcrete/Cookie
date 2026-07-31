<template>
  <div class="farm-scene">
    <div class="grass"></div>
    <div class="roof"></div>
    <div class="wall"></div>
    <div class="door"></div>
    <div class="window"></div>
    <div class="barn-wall"></div>
    <div class="barn-roof"></div>
    <div class="barn-door"></div>
    <div class="row row-1"></div>
    <div class="row row-2"></div>
    <div class="row row-mowed"></div>
    <div class="fence"></div>
    <div class="hay"></div>
    <template v-if="workers > 0">
      <div class="mower mower-1">
        <PixelWorker anim="bend" :dur="0.9" hat="#e8c766" torso="#8a5a34"
          :tool="{ anim: 'swing', dur: 0.9, color: '#c9b06a', height: '17px' }" />
      </div>
      <div class="mower mower-2">
        <PixelWorker anim="bend" :dur="0.9" :delay="0.45" hat="#c9702a" skin="#e8b489" torso="#4a3f7a"
          :tool="{ anim: 'swing', dur: 0.9, delay: 0.45, color: '#c9b06a', height: '17px' }" />
      </div>
      <div class="commute">
        <TravelingWorker travel-anim="commute" :travel-dur="8" hat="#4a7c2f" torso="#6b4f2a" />
      </div>
    </template>
  </div>
</template>

<script setup>
import PixelWorker from '../pixel/PixelWorker.vue'
import TravelingWorker from './TravelingWorker.vue'

defineProps({ workers: { type: Number, default: 0 } })
</script>

<style scoped>
.farm-scene { position: absolute; inset: 0; background: #c8a95e; }
.grass { position: absolute; left: 0; top: 0; width: 120px; height: 100%; background: #8fae5c; background-image: repeating-linear-gradient(0deg, rgba(0,0,0,.06) 0 8px, transparent 8px 16px); }
.roof  { position: absolute; left: 8px;  top: 12px; width: 82px; height: 12px; background: #b83232; box-shadow: 0 0 0 2px #3a2a1c; }
.wall  { position: absolute; left: 12px; top: 24px; width: 74px; height: 54px; background: #f7f0d6; box-shadow: 0 0 0 2px #3a2a1c; background-image: repeating-linear-gradient(0deg, rgba(0,0,0,.1) 0 6px, transparent 6px 12px); }
.door   { position: absolute; left: 43px; top: 60px; width: 12px; height: 18px; background: #6b4f2a; }
.window { position: absolute; left: 17px; top: 32px; width: 9px; height: 8px; background: #e8b93c; box-shadow: 0 0 0 2px #3a2a1c; }
.barn-wall { position: absolute; left: 14px; top: 84px; width: 58px; height: 10px; background: #a97b4a; box-shadow: 0 0 0 2px #3a2a1c; }
.barn-roof { position: absolute; left: 14px; top: 94px; width: 58px; height: 26px; background: #c9702a; box-shadow: 0 0 0 2px #3a2a1c; background-image: repeating-linear-gradient(0deg, rgba(0,0,0,.12) 0 5px, transparent 5px 10px); }
.barn-door { position: absolute; left: 36px; top: 104px; width: 14px; height: 16px; background: #5a4023; }
.row { position: absolute; left: 124px; right: 0; height: 38px; }
.row-1 { top: 6px;  background-image: repeating-linear-gradient(90deg, #f0d179 0 4px, #c9a23f 4px 6px, transparent 6px 10px); }
.row-2 { top: 50px; background-image: repeating-linear-gradient(90deg, #e8c766 0 4px, #b8912f 4px 6px, transparent 6px 10px); }
.row-mowed { top: 94px; right: 80px; height: 14px; background-image: repeating-linear-gradient(90deg, #b8912f 0 4px, #8d6a3d 4px 6px, transparent 6px 10px); }
.fence { position: absolute; left: 120px; top: 0; width: 5px; height: 100%; background: #b98f57; }
.hay { position: absolute; left: 128px; bottom: 6px; width: 28px; height: 16px; background: #e8c766; box-shadow: 0 0 0 2px #8d6a3d; background-image: repeating-linear-gradient(90deg, rgba(0,0,0,.16) 0 3px, transparent 3px 6px); }
.mower { position: absolute; z-index: 3; }
.mower-1 { left: 186px; top: 52px; }
.mower-2 { left: 236px; top: 14px; }
.commute { position: absolute; left: 92px; bottom: 4px; z-index: 3; }
</style>
