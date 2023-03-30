<template>
  <div v-show="playing">
    <div class="footer" id="player">
      <PlayerProgress ref="playerProgress" id="player_progress" />
      <img id="player_artwork">
      <div class="player_info">
        <p id="title" />
        <p id="artist" />
      </div>
      <div class="player_controls">
        <div>
          <PlayerButton type="shuffle" id="player_shuffle" ref="shuffle" />
          <PlayerButton type="skip_previous" id="player_previous" ref="previous" />
          <PlayerButton type="replay_10" id="player_previous_10" ref="previous10" />
          <PlayerButton type="play_circle" id="player_play" ref="play" />
          <PlayerButton type="pause_circle" id="player_pause" ref="pause" />
          <PlayerButton type="forward_10" id="player_next_10" ref="next10" />
          <PlayerButton type="skip_next" id="player_next" ref="next" />
          <PlayerButton type="repeat" id="player_repeat" ref="repeat" />
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
import PlayerProgress from './PlayerProgress.vue'
import { API_IP, API_URL } from '../config.js'

export default {
  components: {
    PlayerButton,
    PlayerProgress,
  },
  data() {
    return {
      playerWS: WebSocket,
      playing: false,
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

    initEventListeners() {
      this.$refs.play.$el.addEventListener('click', this.play);
      this.$refs.pause.$el.addEventListener('click', this.pause);
      this.$refs.previous.$el.addEventListener('click', this.previous);
      this.$refs.next.$el.addEventListener('click', this.next);
      this.$refs.shuffle.$el.addEventListener('click', this.shuffle);
      this.$refs.repeat.$el.addEventListener('click', this.repeat);
      this.$refs.previous10.$el.addEventListener('click', this.previous10);
      this.$refs.next10.$el.addEventListener('click', this.next10);
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
        }
        const msg = JSON.parse(event.data);
        this.$el.querySelector("#title").textContent = msg.title
        this.$el.querySelector("#artist").textContent = msg.artist
        this.$el.querySelector("#time").textContent = this.msecToTime(new Date(msg.progress))
        this.$el.querySelector("#duration").textContent = this.msecToTime(new Date(msg.duration))
        if (lastLoadedMediaUri != msg.uri) {
          console.log("Loading image: "+API_URL + "/artwork?randomizer=" + Date.now());
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
      }
    }
  }
}
</script>

<style lang='scss'>
@import '../scss/app.scss';

#player {
  position: fixed;
  display: flex;
  grid-template-columns: repeat(3, 1fr);
  grid-gap: 10px;
  bottom: 0;
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

#player_artwork {
  width: 48px;
  height: 48px;
}
</style>