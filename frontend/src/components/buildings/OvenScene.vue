<template>
  <div class="oven-scene">
    <div class="mouth-a"></div>
    <div class="mouth-b"></div>
    <div class="mouth-c"></div>
    <div class="flame flame-1"></div>
    <div class="flame flame-2"></div>
    <div class="flame flame-3"></div>
    <div class="ledge"></div>
    <div class="shelf"></div>
    <div class="cookie cookie-1"></div>
    <div class="cookie cookie-2"></div>
    <div class="cookie cookie-3"></div>
    <div class="baker">
      <PixelWorker anim="knead" :dur="0.55" hat="#fffaf0" torso="#8a5a34"
        :tool="{ anim: 'churn', dur: 0.55, color: '#8d6a3d' }" />
    </div>
  </div>
</template>

<script setup>
import PixelWorker from '../pixel/PixelWorker.vue'
</script>

<style scoped>
.oven-scene {
  position: absolute; inset: 0;
  background: #a5713d;
  background-image:
    repeating-linear-gradient(0deg, rgba(0,0,0,.18) 0 7px, transparent 7px 14px),
    repeating-linear-gradient(90deg, rgba(0,0,0,.18) 0 13px, transparent 13px 26px);
}
.mouth-a { position: absolute; left: 30px; top: 14px; width: 36px; height: 6px;  background: #3a2510; }
.mouth-b { position: absolute; left: 24px; top: 20px; width: 48px; height: 8px;  background: #3a2510; }
.mouth-c { position: absolute; left: 20px; top: 28px; width: 56px; height: 34px; background: #3a2510; }
.flame { position: absolute; animation: px-flick .6s ease-in-out infinite; transform-origin: 50% 100%; }
.flame-1 { left: 38px; top: 34px; width: 20px; height: 8px;  background: #ffd76b; }
.flame-2 { left: 32px; top: 42px; width: 32px; height: 8px;  background: #e8b93c; animation-delay: .1s; }
.flame-3 { left: 26px; top: 50px; width: 44px; height: 10px; background: #e05a4a; animation-delay: .2s; }
.ledge  { position: absolute; left: 20px; top: 62px; width: 56px; height: 6px; background: #6b4425; }
.shelf  { position: absolute; right: 8px; top: 20px; width: 58px; height: 5px; background: #8d6a3d; box-shadow: 0 0 0 2px #3a2a1c; }
.cookie { position: absolute; top: 11px; width: 14px; height: 9px; box-shadow: 0 0 0 2px #4a2c14; }
.cookie-1 { right: 12px; background: #d98b3f; }
.cookie-2 { right: 32px; background: #d98b3f; }
.cookie-3 { right: 52px; background: #c9702a; }
.baker { position: absolute; right: 14px; bottom: 4px; z-index: 3; }
</style>
