<template>
  <div id="progress_bar_background" v-show="duration != 0">
    <div id="progress_bar_progress" :style="{ width: (progress / duration)*100 + '%' }"/>
  </div>
</template>

<script>
export default {
  data() {
    return {
      progress: 0,
      duration: 0
    }
  }
}
</script>

<style lang="scss">
@import '../scss/app.scss';

#player_progress{
  background-color: $light-grey;
  height: 4px;
  width: 100%;
  // position: absolute;
    top: 0;
    left: 0;
}

#progress_bar_progress {
  background-color: $primary-color-dark;
  height: 4px;
  border-top-right-radius: 2px;
  border-bottom-right-radius: 2px;
}


</style>