<template>
  <div class="cow-scene">
    <div class="roof"></div>
    <div class="wall"></div>
    <div class="door"></div>
    <div class="window"></div>
    <div class="feed-line"></div>

    <div v-for="(c, i) in cows" :key="i" class="cow-wrap" :style="{ left: c.left + 'px', bottom: c.bottom + 'px' }">
      <div class="cow" :style="{ animationDelay: c.delay + 's' }">
        <div class="cow-body" :style="{ background: c.body }"></div>
        <div class="cow-spot cow-spot-1" :style="{ background: c.spot }"></div>
        <div class="cow-spot cow-spot-2" :style="{ background: c.spot }"></div>
        <div class="cow-head" :style="{ background: c.body }"></div>
        <div class="cow-snout"></div>
        <div class="cow-leg cow-leg-l" :style="{ background: c.spot }"></div>
        <div class="cow-leg cow-leg-r" :style="{ background: c.spot }"></div>
      </div>
    </div>

    <div class="trough"></div>
    <div class="fence-posts"></div>
    <div class="fence-rail fence-rail-top"></div>
    <div class="fence-rail fence-rail-mid"></div>

    <div class="milker">
      <PixelWorker anim="milk" :dur="1.1" hat="#5aa0e0" torso="#4a3f7a"
        :tool="{ anim: 'tap', dur: 1.1, color: '#9fb3c2', top: '12px', height: '9px', width: '7px' }" />
    </div>
  </div>
</template>

<script setup>
import PixelWorker from '../pixel/PixelWorker.vue'

const cows = [
  { left: 12, bottom: 46, body: '#fffaf0', spot: '#3a2a1c', delay: 0 },
  { left: 66, bottom: 42, body: '#f3e6cc', spot: '#6b4f2a', delay: 0.9 },
]
</script>

<style scoped>
.cow-scene {
  position: absolute; inset: 0;
  background: #8fae5c;
  background-image: repeating-linear-gradient(0deg, rgba(0,0,0,.06) 0 8px, transparent 8px 16px);
}
.roof { position: absolute; left: 146px; top: 8px;  width: 86px; height: 12px; background: #b83232; box-shadow: 0 0 0 2px #3a2a1c; }
.wall { position: absolute; left: 150px; top: 20px; width: 78px; height: 52px; background: #f7edd4; box-shadow: 0 0 0 2px #3a2a1c; background-image: repeating-linear-gradient(0deg, rgba(0,0,0,.1) 0 6px, transparent 6px 12px); }
.door      { position: absolute; left: 183px; top: 54px; width: 12px; height: 18px; background: #5a4023; }
.window    { position: absolute; left: 155px; top: 28px; width: 9px;  height: 8px;  background: #e8b93c; box-shadow: 0 0 0 2px #3a2a1c; }
.feed-line { position: absolute; left: 158px; top: 64px; width: 62px; height: 8px;  background: #8d6a3d; }

.cow-wrap { position: absolute; }
.cow { position: relative; width: 30px; height: 20px; animation: px-chew 2.4s ease-in-out infinite; }
.cow-body   { position: absolute; left: 0;  top: 4px;  width: 22px; height: 12px; box-shadow: 0 0 0 2px #3a2a1c; }
.cow-spot   { position: absolute; }
.cow-spot-1 { left: 4px;  top: 6px;  width: 7px; height: 5px; }
.cow-spot-2 { left: 14px; top: 10px; width: 5px; height: 4px; }
.cow-head   { position: absolute; left: 20px; top: 6px; width: 9px; height: 8px; box-shadow: 0 0 0 2px #3a2a1c; }
.cow-snout  { position: absolute; left: 26px; top: 10px; width: 4px; height: 4px; background: #f0b8b8; }
.cow-leg    { position: absolute; top: 16px; width: 3px; height: 4px; }
.cow-leg-l { left: 2px; }
.cow-leg-r { left: 15px; }

.trough { position: absolute; left: 110px; bottom: 40px; width: 30px; height: 8px; background: #8d6a3d; box-shadow: 0 0 0 2px #3a2a1c; background-image: repeating-linear-gradient(90deg, #8fae5c 0 3px, transparent 3px 6px); }
.fence-posts { position: absolute; left: 0; right: 0; bottom: 6px; height: 26px; background-image: repeating-linear-gradient(90deg, #a97b4a 0 5px, transparent 5px 26px); }
.fence-rail { position: absolute; left: 0; right: 0; height: 4px; background: #8d6a3d; }
.fence-rail-top { bottom: 26px; }
.fence-rail-mid { bottom: 14px; }
.milker { position: absolute; left: 44px; bottom: 38px; z-index: 4; }
</style>
