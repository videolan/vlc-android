<template>
  <div class="">
    <div class="footer" id="player">
      <img id="player_artwork" width="48px" height="48px">
      <div class="player_info">
        <p id="title" />
        <p id="artist" />
      </div>
      <div class="player_controls">
        <div>
          <PlayerButton type="random" id="player_shuffle" />
          <PlayerButton type="backward" id="player_previous" />
          <PlayerButton type="fast-backward" id="player_previous_10" />
          <PlayerButton type="play-circle" id="player_play" />
          <PlayerButton type="pause-circle" id="player_pause" />
          <PlayerButton type="fast-forward" id="player_next_10" />
          <PlayerButton type="forward" id="player_next" />
          <PlayerButton type="repeat" id="player_repeat" />
        </div>
        <div id="player_controls_progress">
          <p id="time" />
          <div id="progress_bar"></div>
          <p id="duration" />
        </div>
      </div>
      <div class="player_right">
      </div>
    </div>
  </div>
</template>

<script>
import PlayerButton from './PlayerButton.vue'

export default {
  components: {
    PlayerButton
  },
}
</script>

<style lang='scss'>
@import '../scss/app.scss';

#player {
  position: fixed;
  display: flex;
  grid-template-columns: repeat(3, 1fr);
  grid-gap: 10px;
  bottom: 200px;
  width: 100%;
  padding: 16px;
  background: #FF7700;
  color: #ffffff;
  border-radius-top-left: 8px;
  border-radius-top-right: 8px;
  align-items: center;
}

.player_info,
.player_right,
#progress_bar {
  flex: auto;
}

#progress_bar {
  min-width: 200px;
}

#player_controls_progress {
  display: flex;
}
</style>