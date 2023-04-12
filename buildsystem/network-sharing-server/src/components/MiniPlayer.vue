<template>
  <div v-show="this.playerStore.playing">
    <PlayQueue :medias="this.playerStore.playqueueData.medias" :show="this.playerStore.playqueueShowing" />
    <div class="footer" id="player" v-bind:class="(this.playerStore.responsivePlayerShowing) ? 'footer force-show' : 'footer'">
      <div class="time-duration-container">
        <p id="time"> {{ msecToTime(new Date(this.playerStore.nowPlaying.progress)) }}
        </p>
        <div class="flex1">&nbsp;</div>
        <p id="duration">{{ msecToTime(new Date(this.playerStore.nowPlaying.duration))
        }}</p>
      </div>
      <PlayerProgress ref="playerProgress" id="player_progress" progress="{{ this.playerStore.nowPlaying.progress }}"
        duration="{{ this.playerStore.nowPlaying.duration }}" />
      <div id="player_content" class="row">
        <div class="col-12 col-md" id="media_info">
          <img id="player_artwork">
          <div class="player_info">
            <p id="title" class="h6 text-truncate">{{ this.playerStore.nowPlaying.title }}</p>
            <p id="artist" class="text-truncate">{{ this.playerStore.nowPlaying.artist }}</p>
          </div>
        </div>

        <div class="player_controls col-12 col-md">
          <span class="flex1" />
          <PlayerButton type="shuffle" id="player_shuffle" ref="shuffle" v-on:click="shuffle()" />
          <PlayerButton type="skip_previous" id="player_previous" ref="previous" v-on:click="previous()" />
          <PlayerButton type="replay_10" id="player_previous_10" ref="previous10" v-on:click="previous10()" />
          <PlayerButton type="play_circle" class="big" id="player_play" ref="play"
            v-show="!this.playerStore.nowPlaying.playing" v-on:click="play()" />
          <PlayerButton type="pause_circle" class="big" id="player_pause" ref="pause"
            v-show="this.playerStore.nowPlaying.playing" v-on:click="pause()" />
          <PlayerButton type="forward_10" id="player_next_10" ref="next10" v-on:click="next10()" />
          <PlayerButton type="skip_next" id="player_next" ref="next" v-on:click="next()" />
          <PlayerButton type="repeat" id="player_repeat" ref="repeat" @click.stop="repeat()" />
          <span class="flex1" />
        </div>
        <div class="player_right col-12 col-md">
          <div class="player_right_container">
            <PlayerButton type="queue_music" class="medium" id="playqueue" ref="playqueueButton"
              @click="togglePlayQueue($event)" />
          </div>
          <div class="player_right_container">
            <input type="range" ref="volume" name="volume" min="0" max="100" step="1" @change="this.volumeChange($event)">
          </div>
        </div>
      </div>
    </div>
    <div class="mini-player-fab" v-on:click.stop="showResponsivePlayer()" v-show="!this.playerStore.responsivePlayerShowing">
      <div class="playing">
        <span class="playing-bar playing-bar1"></span>
        <span class="playing-bar playing-bar2"></span>
        <span class="playing-bar playing-bar3"></span>
      </div>

    </div>
  </div>
</template>

<script>
import PlayerButton from './PlayerButton.vue'
import PlayerProgress from './PlayerProgress.vue'
import PlayQueue from './PlayQueue.vue'
import { playerStore } from '../stores/PlayerStore'
import { mapStores } from 'pinia'
import { mapState } from 'pinia'
import { API_URL } from '../config.js'

export default {
  components: {
    PlayerButton,
    PlayerProgress,
    PlayQueue,
  },
  data() {
    return {
      playing: false,
      loadedArtworkUrl: ""
    }
  },
  computed: {
    ...mapStores(playerStore),
    ...mapState(playerStore, ['nowPlaying'])
  },
  methods: {
    msecToTime(ms) {
      const seconds = Math.floor((ms / 1000) % 60)
      const minutes = Math.floor((ms / (60 * 1000)) % 60)
      const hours = Math.floor((ms / (3600 * 1000)) % 3600)
      return `${hours < 10 ? '0' + hours : hours}:${minutes < 10 ? '0' + minutes : minutes}:${seconds < 10 ? '0' + seconds : seconds
        }`
    },
    play() {
      this.$root.connection.send("play");
      console.log("Sending play");
    },
    pause() {
      this.$root.connection.send("pause");
      console.log("Sending pause");
    },
    previous() {
      this.$root.connection.send("previous");
      console.log("Sending previous");
    },
    next() {
      this.$root.connection.send("next");
      console.log("Sending next");
    },
    shuffle() {
      this.$root.connection.send("shuffle");
      console.log("Sending shuffle");
    },
    repeat() {
      this.$root.connection.send("repeat");
      console.log("Sending repeat");
    },
    previous10() {
      this.$root.connection.send("previous10");
      console.log("Sending previous10");
    },
    next10() {
      this.$root.connection.send("next10");
      console.log("Sending next10");
    },
    volumeChange(event) {
      this.$root.connection.send("set-volume:" + event.target.value);
      this.$refs.volume.value = event.target.value
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
      if (this.$refs.volume.value != this.nowPlaying.volume) this.$refs.volume.value = this.nowPlaying.volume
      if (this.loadedArtworkUrl != this.nowPlaying.uri) {
        this.loadedArtworkUrl = this.nowPlaying.uri
        this.$el.querySelector("#player_artwork").src = API_URL + "/artwork?randomizer=" + Date.now()
      }
    },
  },
}
</script>

<style lang='scss'>
@import '../scss/app.scss';

:root {
  --playerHeight: 156px;
}

@media screen and (min-width: 768px) {
  :root {
    --playerHeight: 92px;
  }

  #player {
    display: block !important;
  }

  .mini-player-fab {
    display: none;
  }

  #media_info {
    overflow: auto;
  }

  #time, #duration {
    background-color: transparent !important;
  }

}

.force-show {
  display: block !important;
}


#player {
  position: fixed;
  bottom: 0;
  width: 100%;
  color: #000;
  display: none;
  border-radius-top-left: 8px;
  border-radius-top-right: 8px;
  align-items: center;
  height: var(--playerHeight);
}

#player_content {
  display: flex;
  grid-template-columns: repeat(3, 1fr);
  grid-gap: 10px;
  padding-left: 16px;
  padding-right: 16px;
  padding-top: 8px;
  background: $light-grey;
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

#player_artwork {
  width: 54px;
  height: 54px;
  float: left;
  border-radius: 6px;
}

.time-duration-container {
  display: flex;
}

#time {
  padding-left: 8px;
  padding-right: 8px;
  margin-left: 8px;
}

#duration {
  flex: none;
  padding-right: 8px;
  padding-left: 8px;
  margin-right: 8px;
}

#time, #duration {
  background-color: $light-grey;
  border-radius: 8px 8px 0px 0px;
}

.player_right>* {
  float: right;
}

.player_right_container {
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


.mini-player-fab {
  position: absolute;
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


//animation

.playing {
  background: rgba(0, 0, 0, .3);
  width: 2rem;
  height: 2rem;
  border-radius: 1rem;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding: .5rem;
  box-sizing: border-box;
  position: absolute;
  margin-left: 5px;
  margin-top: 5px;
}

.playing-bar {
  display: inline-block;
  background: white;
  width: 30%;
  height: 100%;
  animation: up-and-down 1.3s ease infinite alternate;
}

.playing-bar1 {
  height: 60%;
}

.playing-bar2 {
  height: 30%;
  animation-delay: -2.2s;
}

.playing-bar3 {
  height: 75%;
  animation-delay: -3.7s;
}

@keyframes up-and-down {
  10% {
    height: 20%;
  }

  30% {
    height: 100%;
  }

  60% {
    height: 30%;
  }

  80% {
    height: 75%;
  }

  100% {
    height: 60%;
  }
}
</style>