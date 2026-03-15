<template>
  <div class="cookie-spinner"></div>
</template>

<style scoped>
.cookie-spinner {
  width: 75px;
  height: 75px;
  background-image: url('@/assets/Sprites/CookieAnimationSpirte.png');
  background-repeat: no-repeat;
  background-size: 800% auto;
  background-position: 0px 0px;
  animation: cookie-frames 0.9s steps(8, end) infinite;
  image-rendering: pixelated;
}

@keyframes cookie-frames {
  from { background-position:    0px 0px; }
  to   { background-position: -600px 0px; }
}
</style>
