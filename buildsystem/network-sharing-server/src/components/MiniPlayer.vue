<template>
  <div id="player">
    <img id="player_artwork" width="48px" height="48px">
    <div class="player_info">
        <p id="title"/>
        <p id="artist"/>
    </div>
    <div class="player_controls">
        <div>
            <PlayerButton type="shuffle" id="player_shuffle"/>
            <PlayerButton type="skip_previous" id="player_previous"/>
            <PlayerButton type="replay_10" id="player_previous_10"/>
            <PlayerButton type="play_circle" id="player_play"/>
            <PlayerButton type="pause_circle" id="player_pause"/>
            <PlayerButton type="forward_10" id="player_next_10"/>
            <PlayerButton type="skip_next" id="player_next"/>
            <PlayerButton type="repeat" id="player_repeat"/>
        </div>
        <div id="player_controls_progress">
            <p id="time"/>
            <div id="progress_bar"></div>
            <p id="duration"/>
        </div>
    </div>
    <div class="player_right">
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

<style>

</style>