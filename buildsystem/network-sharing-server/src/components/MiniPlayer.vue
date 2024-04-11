<template>
  <div class="footer" id="player" v-show="this.playerStore.playing" @wheel.prevent @scroll.prevent
    v-bind:class="(this.playerStore.responsivePlayerShowing) ? 'footer force-show' : 'footer'">
    <div class="progress-container">
      <p id="time"> {{ $readableDuration(new Date(this.playerStore.nowPlaying.progress)) }}
      </p>
      <p id="duration">{{ $readableDuration(new Date(this.playerStore.nowPlaying.duration))
      }}</p>
      <input type="range" ref="progress" min="0" :max="this.playerStore.nowPlaying.duration" class="player-progress"
        step="1" @change="this.progressChange($event)" @input="this.progressChange($event)"
        @touchstart="this.progressTouched = true" @touchend="this.progressTouched = false"
        @touchcancel="this.progressTouched = false">
    </div>
    <div id="player_content" class="row">
      <div class="col-12 col-md" id="media_info">
        <img ref="playerArtwork" class="player-artwork">
        <div class="player_info">
          <p id="title" class="h6 text-truncate">{{ this.playerStore.nowPlaying.title }}</p>
          <p id="artist" class="text-truncate">{{ this.playerStore.nowPlaying.artist }}</p>
        </div>
      </div>

      <div class="player_controls col-12 col-md">
        <span class="flex1" />
        <ImageButton type="shuffle" id="player_shuffle" ref="shuffle" v-on:click="shuffle()"
          v-bind:class="(this.playerStore.nowPlaying.shuffle) ? 'active' : ''" />
        <ImageButton type="skip_previous" id="player_previous" ref="previous" v-on:click="previous()" />
        <ImageButton type="replay_10" id="player_previous_10" ref="previous10" v-on:click="previous10()" />
        <ImageButton type="play_circle" class="big" id="player_play" ref="play"
          v-show="!this.playerStore.nowPlaying.playing" v-on:click="play()" />
        <ImageButton type="pause_circle" class="big" id="player_pause" ref="pause"
          v-show="this.playerStore.nowPlaying.playing" v-on:click="pause()" />
        <ImageButton type="forward_10" id="player_next_10" ref="next10" v-on:click="next10()" />
        <ImageButton type="skip_next" id="player_next" ref="next" v-on:click="next()" />
        <ImageButton id="player_repeat" ref="repeat" @click.stop="repeat()"
          v-bind:type="(this.playerStore.nowPlaying.repeat != 1) ? 'repeat' : 'repeat_one'"
          v-bind:class="(this.playerStore.nowPlaying.repeat != 0) ? 'active' : ''" />
        <span class="flex1" />
      </div>
      <div class="player_right col-12 col-md">
        <div class="player-right-container playqueue-container">
          <ImageButton type="queue_music" class="medium" id="playqueue" ref="playqueueButton"
            @click="togglePlayQueue($event)" v-bind:class="(this.playerStore.playqueueShowing) ? 'active' : ''" />
        </div>
        <div class="player-right-container volume-container">
          <ImageButton type="volume" class="medium" id="volume_icon" v-show="(this.playerStore.volume != 0)" v-on:click="mute()"/>
          <ImageButton type="volume_off" class="medium" id="volume_icon" v-show="(this.playerStore.volume == 0)" v-on:click="mute()"/>
          <input type="range" ref="volume" min="0" max="100" step="1" @change="this.volumeChange($event)"
            @input="this.volumeChange($event)" @touchstart="this.volumeTouched = true"
            @touchend="this.volumeTouched = false" @touchcancel="this.volumeTouched = false">
        </div>
      </div>
    </div>
  </div>
  <div class="mini-player-fab" v-on:click.stop="showResponsivePlayer()"
    v-show="!this.playerStore.responsivePlayerShowing && this.playerStore.playing">
    <div class="playing">
      <span class="playing-bar playing-bar1"></span>
      <span class="playing-bar playing-bar2"></span>
      <span class="playing-bar playing-bar3"></span>
    </div>

  </div>
</template>

<script>
import ImageButton from './ImageButton.vue'
import { usePlayerStore } from '../stores/PlayerStore'
import { mapStores } from 'pinia'
import { mapState } from 'pinia'
import { vlcApi } from '../plugins/api.js'

export default {
  components: {
    ImageButton,
  },
  data() {
    return {
      playing: false,
      loadedArtworkUrl: "",
      volumeTouched: false,
      progressTouched: false,
      volumeTimeoutId: 0,
      oldVolume: -1,
    }
  },
  computed: {
    ...mapStores(usePlayerStore),
    ...mapState(usePlayerStore, ['nowPlaying'])
  },
  methods: {
    changeVolumeIfNeeded() {
      this.updateVolumeBackground()
      if (this.$refs.volume.matches(':hover') || this.volumeTouched) return
      if (this.$refs.volume.value != this.nowPlaying.volume) this.$refs.volume.value = this.nowPlaying.volume
      this.updateVolumeBackground()
    },
    //Mute or unmute depending on the current volume
    mute() {
      if (this.$refs.volume.value == 0) {
        if (this.oldVolume == -1) {
          this.$root.sendMessage("set-volume", this.$refs.volume.max / 2);
          this.$refs.volume.value = this.$refs.volume.max / 2
        } else {
          this.$root.sendMessage("set-volume", this.oldVolume);
          this.$refs.volume.value = this.oldVolume
        }
      } else {
        this.oldVolume = this.$refs.volume.value
          this.$root.sendMessage("set-volume", 0);
        this.$refs.volume.value = 0
      }
      this.updateVolumeBackground() 
    },
    changeProgressIfNeeded() {
      this.updateProgressBackground()
      if (this.$refs.progress.matches(':hover') || this.volumeTouched) return
      if (this.$refs.progress.value != this.nowPlaying.progress) this.$refs.progress.value = this.nowPlaying.progress
      this.updateProgressBackground()
    },
    updateVolumeBackground() {
      let target = this.$refs.volume
      const min = target.min
      const max = target.max
      const val = target.value

      this.playerStore.volume = this.$refs.volume.value
      target.style.backgroundSize = (val - min) * 100 / (max - min) + '% 100%'
    },
    updateProgressBackground() {
      let target = this.$refs.progress
      const min = target.min
      const max = target.max
      const val = target.value

      target.style.backgroundSize = (val - min) * 100 / (max - min) + '% 100%'
    },
    play() {
      this.$root.sendMessage("play");
      this.$log.log("Sending play");
    },
    pause() {
      this.$root.sendMessage("pause");
      this.$log.log("Sending pause");
    },
    previous() {
      this.$root.sendMessage("previous");
      this.$log.log("Sending previous");
    },
    next() {
      this.$root.sendMessage("next");
      this.$log.log("Sending next");
    },
    shuffle() {
      this.nowPlaying.shuffle = !this.nowPlaying.shuffle
      this.$root.sendMessage("shuffle");
      this.$log.log("Sending shuffle");
    },
    repeat() {
      let newRepeat = this.nowPlaying.repeat + 1
      if (newRepeat > 2) newRepeat = 0
      this.nowPlaying.repeat = newRepeat
      this.$root.sendMessage("repeat");
      this.$log.log("Sending repeat");
    },
    previous10() {
      this.$root.sendMessage("previous10");
      this.$log.log("Sending previous10");
    },
    next10() {
      this.$root.sendMessage("next10");
      this.$log.log("Sending next10");
    },
    volumeChange(event) {
      clearTimeout(this.volumeTimeoutId)
      this.updateVolumeBackground()
      this.volumeTimeoutId = setTimeout(() => {
        this.updateVolumeBackground()
        this.$root.sendMessage("set-volume", event.target.value);
        this.changeVolumeIfNeeded()
      }, "50");

      this.playerStore.volume = event.target.value

    },
    progressChange(event) {
      this.updateProgressBackground()
      this.$root.sendMessage("set-progress", event.target.value);
      this.changeProgressIfNeeded()
    },
    togglePlayQueue() {
      this.playerStore.playqueueShowing = !this.playerStore.playqueueShowing
    },
    showResponsivePlayer() {
      this.playerStore.playqueueShowing = !this.playerStore.playqueueShowing
      this.playerStore.responsivePlayerShowing = !this.responsivePlayerShowing
    },
  },
  watch: {
    nowPlaying() {
      this.changeVolumeIfNeeded()
      this.changeProgressIfNeeded()
      if (this.loadedArtworkUrl != this.nowPlaying.uri) {
        this.loadedArtworkUrl = this.nowPlaying.uri
        this.$refs.playerArtwork.src = vlcApi.artwork(null, null, null, Date.now())
      }
    },
  },
}
</script>

<style lang='scss'>
@import '../scss/colors.scss';

:root {
  --playerHeight: 156px;
}

@media screen and (max-width: 768px) {
  #player {

    display: none;
  }

  .player-right-container.playqueue-container {
    display: none;
  }

  .player-right-container.volume-container {
    float: none;
  }
}

@media screen and (max-width: 450px) {
  .player_controls .image-button {

    width: 40px;
  }

  .player_controls .image-button.big {
    width: 58px;
  }
}

@media screen and (max-width: 330px) {
  .player_controls .image-button {

    width: 32px;
  }

  .player_controls .image-button.big {
    width: 48px;
  }
}

@media screen and (min-width: 768px) {
  :root {
    --playerHeight: 98px;
  }

  .mini-player-fab {
    display: none;
  }

  #media_info {
    overflow: auto;
  }

  #time,
  #duration {
    background-color: transparent !important;
  }


}

.force-show {
  display: block !important;
}


#player {
  width: 100%;
  color: var(--bs-heading-color);
  align-items: center;
  z-index: 1022;
}

#player_content {
  display: flex;
  grid-template-columns: repeat(3, 1fr);
  grid-gap: 10px;
  padding-left: 16px;
  padding-right: 16px;
  padding-top: 8px;
  padding-bottom: 8px;
  background: var(--bs-card-bg);
}

.progress-container {
  background: linear-gradient(transparent 60%, var(--bs-card-bg) 60%);
  position: absolute;
  margin-top: -14px;
  width: 100vw;
}

#progress_bar {
  min-width: 200px;
}

#player_controls_progress {
  display: flex;
  width: 100%;
  // position: absolute;
  top: 0;
  left: 0;
}

.player-artwork {
  width: 54px;
  height: 54px;
  float: left;
  border-radius: 6px;
}

.player-progress {
  position: relative;
  z-index: 1;
}

#time {
  left: 8px;
}

#duration {
  right: 8px;
}

#time,
#duration {
  padding-left: 8px;
  padding-right: 8px;
  background-color: var(--light-gray);
  border-radius: 8px 8px 0px 0px;
  position: absolute;
  top: -10px;
}

.player_right>* {
  float: right;
}

.player-right-container {
  height: 100%;
  display: flex;
  align-items: center;
}

.player_controls {
  display: flex;
  align-items: baseline;
}

.flex1 {
  flex: 1;
}

#title,
#artist {
  padding-left: 16px;
  margin-bottom: 0;
  height: 27px;
  vertical-align: middle;
  line-height: 27px;
}

#artist {
  color: var(--secondary-text);
}


.mini-player-fab {
  position: fixed;
  bottom: 16px;
  right: 16px;
  width: 42px;
  height: 42px;
  background-color: $primary-color;
  border-radius: 50%;
}

.mini-player-fab .playing {
  background: none;
}
</style>