<template>
  <div class="all-center container">
    <div class="error-container all-center card">
      <img v-bind:src="$getAppAsset('ic_empty', 64, true)">
      <p>{{ message }}</p>
    </div>
  </div>
</template>

<script>

export default {
  props: {
    message: String
  },
}
</script>

<style lang='scss'>
@import '../scss/colors.scss';

.all-center {
  height: 108px;
  display: flex;
  vertical-align: middle;
  justify-content: center;
  flex-direction: column;
  align-items: center;
  margin-bottom: 24px;
}

.error-container {
  padding: 24px;
  border-radius: 8px;
}

.all-center p {
  margin-top: 8px;
  color: var(--secondary-text);
  font-size: 0.9em;
}
</style>