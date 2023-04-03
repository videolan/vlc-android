<template>
  <div id="progress_bar_background" v-show="playerStore.duration != 0">
    <div id="progress_bar_progress" :style="{ width: (this.playerStore.nowPlaying.progress / this.playerStore.nowPlaying.duration)*100 + '%' }"/>
  </div>
</template>

<script>
import { playerStore } from '../stores/PlayerStore'
import { mapStores } from 'pinia'

export default {
  computed: {
    ...mapStores(playerStore),
  },
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