<template>
  <div class="bh-scene">
    <!-- Backhaus walls -->
    <div class="bh-wall"></div>
    <div class="bh-roof"></div>
    <div class="bh-roof-ridge"></div>

    <!-- Chimney with animated smoke -->
    <div class="bh-chimney">
      <div class="bh-smoke bh-smoke-1"></div>
      <div class="bh-smoke bh-smoke-2"></div>
      <div class="bh-smoke bh-smoke-3"></div>
    </div>

    <!-- Oven mouth in wall -->
    <div class="bh-oven-surround">
      <div class="bh-oven-mouth">
        <div class="bh-flame bh-flame-1"></div>
        <div class="bh-flame bh-flame-2"></div>
      </div>
    </div>

    <!-- Window -->
    <div class="bh-window">
      <div class="bh-window-cross-h"></div>
      <div class="bh-window-cross-v"></div>
    </div>

    <!-- Bread loaves on wooden plank outside -->
    <div class="bh-shelf">
      <div class="bh-loaf bh-loaf-1"></div>
      <div class="bh-loaf bh-loaf-2"></div>
      <div class="bh-loaf bh-loaf-3"></div>
    </div>

    <!-- Baker -->
    <div class="bh-baker">
      <PixelWorker anim="knead" :dur="0.6" hat="#fffaf0" torso="#c8a86a"
        :tool="{ anim: 'churn', dur: 0.6, color: '#8d6a3d' }" />
    </div>
  </div>
</template>

<script setup>
import PixelWorker from '../pixel/PixelWorker.vue'
</script>

<style scoped>
.bh-scene {
  position: absolute; inset: 0;
  background: #8a5a28;   /* dirt/cobblestone ground */
  background-image: repeating-linear-gradient(0deg, rgba(0,0,0,.12) 0 6px, transparent 6px 12px),
                    repeating-linear-gradient(90deg, rgba(0,0,0,.08) 0 10px, transparent 10px 20px);
}

/* Stone/brick building */
.bh-wall {
  position: absolute; left: 14px; top: 18px; right: 26px; bottom: 20px;
  background: #c8b08a;
  background-image:
    repeating-linear-gradient(0deg, rgba(0,0,0,.15) 0 1px, transparent 1px 14px),
    repeating-linear-gradient(90deg, rgba(0,0,0,.10) 0 1px, transparent 1px 22px);
  box-shadow: 0 0 0 3px #4a3020;
}

/* Wooden gable roof */
.bh-roof {
  position: absolute; left: 8px; right: 20px; top: 4px; height: 20px;
  background: #6b4425;
  clip-path: polygon(0 100%, 50% 0%, 100% 100%);
  box-shadow: none;
}
.bh-roof-ridge {
  position: absolute; left: 8px; right: 20px; top: 12px; height: 10px;
  background: #8d6a3d;
  box-shadow: 0 0 0 2px #3a2a1c;
}

/* Chimney */
.bh-chimney {
  position: absolute; right: 32px; top: 0; width: 14px; height: 26px;
  background: #8a6a48; box-shadow: 0 0 0 2px #3a2a1c;
  overflow: visible;
}
.bh-smoke {
  position: absolute; left: 2px; width: 10px; height: 10px;
  border-radius: 50%; background: rgba(200,190,180,.55);
  animation: bh-rise 2s ease-out infinite;
}
.bh-smoke-1 { bottom: 100%; animation-delay: 0s; }
.bh-smoke-2 { bottom: 100%; animation-delay: 0.7s; opacity: 0.7; }
.bh-smoke-3 { bottom: 100%; animation-delay: 1.4s; opacity: 0.4; }
@keyframes bh-rise {
  0%   { transform: translateY(0)   scale(0.6); opacity: 0.6; }
  100% { transform: translateY(-28px) scale(1.4); opacity: 0; }
}

/* Stone oven arch in wall */
.bh-oven-surround {
  position: absolute; left: 18px; bottom: 20px; width: 38px; height: 38px;
  background: #6b4a28; box-shadow: 0 0 0 3px #3a2a1c;
  border-radius: 4px 4px 0 0;
}
.bh-oven-mouth {
  position: absolute; left: 5px; top: 6px; width: 28px; height: 26px;
  background: #1a0e06; border-radius: 3px 3px 0 0;
}
.bh-flame { position: absolute; animation: px-flick .5s ease-in-out infinite; transform-origin: 50% 100%; }
.bh-flame-1 { left: 6px;  top: 8px; width: 16px; height: 8px;  background: #ffd76b; animation-delay: .05s; }
.bh-flame-2 { left: 2px;  top: 14px; width: 24px; height: 6px; background: #e05a4a; animation-delay: .18s; }

/* Window */
.bh-window {
  position: absolute; right: 40px; top: 26px; width: 16px; height: 16px;
  background: #c8e8f8; box-shadow: 0 0 0 3px #3a2a1c;
}
.bh-window-cross-h { position: absolute; left: 0; right: 0; top: 7px; height: 2px; background: #3a2a1c; }
.bh-window-cross-v { position: absolute; top: 0; bottom: 0; left: 7px; width: 2px; background: #3a2a1c; }

/* Wooden shelf with bread loaves */
.bh-shelf {
  position: absolute; right: 8px; top: 28px; width: 16px;
  background: #8d6a3d; box-shadow: 0 0 0 2px #3a2a1c; height: 4px;
  display: flex; gap: 2px; align-items: flex-end;
}
.bh-loaf {
  position: absolute; width: 12px; height: 9px;
  background: #d4883a; border-radius: 4px 4px 0 0; box-shadow: 0 0 0 2px #8d4a10;
}
.bh-loaf-1 { right: 20px; top: -13px; }
.bh-loaf-2 { right: 34px; top: -11px; }
.bh-loaf-3 { right: 50px; top: -14px; }

/* Baker worker */
.bh-baker { position: absolute; right: 8px; bottom: 4px; z-index: 3; }

@keyframes px-flick {
  0%, 100% { transform: scaleX(1) scaleY(1); }
  50%       { transform: scaleX(.85) scaleY(1.15); }
}
</style>
