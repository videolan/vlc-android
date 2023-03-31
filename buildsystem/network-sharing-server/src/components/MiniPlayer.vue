<template>
  <div v-show="playing" class="mdc-typography">
    <PlayQueue :medias="playqueueData.medias" :show="playqueueShowing" />
    <div class="footer" id="player">
      <div class="time-duration-container">
        <p id="time" class="mdc-typography--subtitle2" />
        <p id="duration" class="mdc-typography--subtitle2" />
      </div>
      <PlayerProgress ref="playerProgress" id="player_progress" />
      <div id="player_content" class="row">
        <div class="col" id="media_info">
          <img id="player_artwork">
          <div class="player_info">
            <p id="title" class="mdc-typography--headline6 text-truncate" />
            <p id="artist" class="mdc-typography--subtitle2 text-truncate" />
          </div>
        </div>

        <div class="player_controls col">
          <span class="flex1" />
          <PlayerButton type="shuffle" id="player_shuffle" ref="shuffle" />
          <PlayerButton type="skip_previous" id="player_previous" ref="previous" />
          <PlayerButton type="replay_10" id="player_previous_10" ref="previous10" />
          <PlayerButton type="play_circle" class="big" id="player_play" ref="play" />
          <PlayerButton type="pause_circle" class="big" id="player_pause" ref="pause" />
          <PlayerButton type="forward_10" id="player_next_10" ref="next10" />
          <PlayerButton type="skip_next" id="player_next" ref="next" />
          <PlayerButton type="repeat" id="player_repeat" ref="repeat" />
          <span class="flex1" />
        </div>
        <div class="player_right col">
          <div class="player_right_container">
            <PlayerButton type="queue_music" class="medium" id="playqueue" ref="playqueueButton" />
          </div>
          <div class="player_right_container">
            <input type="range" ref="volume" name="volume" min="0" max="100">
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import PlayerButton from './PlayerButton.vue'
import PlayerProgress from './PlayerProgress.vue'
import PlayQueue from './PlayQueue.vue'
import { API_IP, API_URL } from '../config.js'

export default {
  components: {
    PlayerButton,
    PlayerProgress,
    PlayQueue,
  },
  data() {
    return {
      playerWS: WebSocket,
      playing: false,
      playqueueData:Object,
      playqueueShowing: false
    }
  },
  computed: {
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
      this.playerWS.send("play");
      console.log("Sending play");
    },
    pause() {
      this.playerWS.send("pause");
      console.log("Sending pause");
    },
    previous() {
      this.playerWS.send("previous");
      console.log("Sending previous");
    },
    next() {
      this.playerWS.send("next");
      console.log("Sending next");
    },
    shuffle() {
      this.playerWS.send("shuffle");
      console.log("Sending shuffle");
    },
    repeat() {
      this.playerWS.send("repeat");
      console.log("Sending repeat");
    },
    previous10() {
      this.playerWS.send("previous10");
      console.log("Sending previous10");
    },
    next10() {
      this.playerWS.send("next10");
      console.log("Sending next10");
    },
    volumeChange(event) {
      this.playerWS.send("set-volume:" + event.target.value);
    },
    togglePlayQueue() {
      this.playqueueShowing = !this.playqueueShowing
    },

    initEventListeners() {
      this.$refs.play.$el.addEventListener('click', this.play);
      this.$refs.pause.$el.addEventListener('click', this.pause);
      this.$refs.previous.$el.addEventListener('click', this.previous);
      this.$refs.next.$el.addEventListener('click', this.next);
      this.$refs.shuffle.$el.addEventListener('click', this.shuffle);
      this.$refs.repeat.$el.addEventListener('click', this.repeat);
      this.$refs.previous10.$el.addEventListener('click', this.previous10);
      this.$refs.next10.$el.addEventListener('click', this.next10);
      this.$refs.volume.addEventListener('change', this.volumeChange);
      this.$refs.playqueueButton.$el.addEventListener('click', this.togglePlayQueue);
    },
    removeEventListeners() {
      this.$refs.play.$el.removeEventListener('click', this.play)
      this.$refs.pause.$el.removeEventListener('click', this.pause)
      this.$refs.previous.$el.removeEventListener('click', this.previous)
      this.$refs.next.$el.removeEventListener('click', this.next)
      this.$refs.shuffle.$el.removeEventListener('click', this.shuffle)
      this.$refs.repeat.$el.removeEventListener('click', this.repeat)
      this.$refs.previous10.$el.removeEventListener('click', this.previous10)
      this.$refs.next10.$el.removeEventListener('click', this.next10)
      this.$refs.volume.removeEventListener('change', this.volumeChange);
      this.$refs.playqueueButton.$el.removeEventListener('click', this.togglePlayQueue);
    },
  },
  mounted: function () {
    this.playerWS = new WebSocket("ws://" + API_IP + "/echo", "player");
    var lastLoadedMediaUri = ""
    this.playerWS.onmessage = (event) => {
      if (event.data === "Stopped") {
        console.log("Stopping Player ...")
        this.playing = false;
        this.removeEventListeners();
      } else {
        if (this.playing == false) {
          console.log("Starting player ...")
          this.playing = true;
          this.initEventListeners();
          this.playerWS.send("get-volume");
          console.log("Volume asked");
        }
        const msg = JSON.parse(event.data);

        switch (msg.type) {
          case 'volume':
            this.$refs.volume.value = msg.volume
            break;
          case 'now-playing':
            console.log("Changing title to: "+msg.title)
            this.$el.querySelector("#title").textContent = msg.title
            this.$el.querySelector("#artist").textContent = msg.artist
            this.$el.querySelector("#time").textContent = this.msecToTime(new Date(msg.progress))
            this.$el.querySelector("#duration").textContent = this.msecToTime(new Date(msg.duration))
            if (lastLoadedMediaUri != msg.uri) {
              console.log("Loading image: " + API_URL + "/artwork?randomizer=" + Date.now());
              this.$el.querySelector("#player_artwork").src = API_URL + "/artwork?randomizer=" + Date.now()
              lastLoadedMediaUri = msg.uri
            }

            if (msg.playing) {
              this.$refs.play.$el.style.display = "none";
              this.$refs.pause.$el.style.display = "inline-block";
            } else {
              this.$refs.play.$el.style.display = "inline-block";
              this.$refs.pause.$el.style.display = "none";
            }
            this.$refs.playerProgress.progress = msg.progress;
            this.$refs.playerProgress.duration = msg.duration;
            this.$refs.volume.value = msg.volume
            break;
          case 'play-queue':
            this.playqueueData = msg
            break;
        }
      }
    }
  }
}
</script>

<style lang='scss'>
@import '../scss/app.scss';
:root{
  --playerHeight:122px;
}

#player {
  position: fixed;
  bottom: 0;
  width: 100%;
  color: #000;
  border-radius-top-left: 8px;
  border-radius-top-right: 8px;
  align-items: center;
  height: var(--playerHeight);
}

#player_content {
  display: flex;
  grid-template-columns: repeat(3, 1fr);
  grid-gap: 10px;
  padding: 16px;
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

#media_info {
  overflow: auto;
}

.player_info {
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
  flex: 1;
  padding-left: 16px;
}

#duration {
  flex: none;
  padding-right: 16px;
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
  align-items: center;
}

.flex1 {
  flex: 1;
}

#title,#artist {
  padding-left: 16px;
}
</style>