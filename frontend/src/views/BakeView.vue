<template>
  <div class="bake-view">
    <RecipeCard />
  </div>
</template>

<script setup>
import RecipeCard from '../components/RecipeCard.vue'
</script>

<style scoped>
.bake-view {
  display: flex;
  justify-content: center;
  padding: 24px;
}
</style>
