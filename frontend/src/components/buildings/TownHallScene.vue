<template>
  <div >
    <div class="flagpole"></div>
    <div class="flag"></div>
    <div class="roof"></div>
    <div class="wall"></div>
    <div class="door"></div>
    <div class="window window-l"></div>
    <div class="window window-r"></div>
    <div class="notice"></div>
    <div class="notice-line notice-line-1"></div>
    <div class="notice-line notice-line-2"></div>
    <div class="bush"></div>
  </div>
</template>

<script setup>
import TravelingWorker from './TravelingWorker.vue'
</script>

<style scoped>
.th-scene {
  position: absolute; inset: 0;
  background: #e2d3b4;
  background-image: repeating-linear-gradient(0deg, rgba(0,0,0,.07) 0 9px, transparent 9px 18px);
}
.flagpole { position: absolute; left: 88px; top: 0; width: 6px; height: 24px; background: #6b4f2a; }
.flag     { position: absolute; left: 94px; top: 2px; width: 26px; height: 14px; background: #b83232; box-shadow: 0 0 0 2px #3a2a1c; animation: px-sway 2.8s ease-in-out infinite; transform-origin: 0 50%; }
.roof { position: absolute; left: 56px; top: 24px; width: 116px; height: 12px; background: #b83232; box-shadow: 0 0 0 2px #3a2a1c; }
.wall { position: absolute; left: 60px; top: 36px; width: 108px; height: 62px; background: #fbf3e2; box-shadow: 0 0 0 2px #3a2a1c; background-image: repeating-linear-gradient(0deg, rgba(0,0,0,.1) 0 6px, transparent 6px 12px); }
.door   { position: absolute; left: 108px; top: 80px; width: 12px; height: 18px; background: #6b4f2a; }
.window { position: absolute; top: 44px; width: 9px; height: 8px; background: #e8b93c; box-shadow: 0 0 0 2px #3a2a1c; }
.window-l { left: 65px; }
.window-r { left: 112px; }
.notice { position: absolute; left: 14px; bottom: 10px; width: 44px; height: 30px; background: #c9a86f; box-shadow: 0 0 0 2px #3a2a1c; }
.notice-line { position: absolute; left: 20px; height: 3px; background: #5a4023; }
.notice-line-1 { bottom: 32px; width: 32px; }
.notice-line-2 { bottom: 25px; width: 24px; }
.bush { position: absolute; left: 190px; bottom: 10px; width: 26px; height: 32px; background: #8fae5c; box-shadow: 0 0 0 2px #3a2a1c; }
.messenger { position: absolute; left: 14px; bottom: 2px; z-index: 3; }
</style>
