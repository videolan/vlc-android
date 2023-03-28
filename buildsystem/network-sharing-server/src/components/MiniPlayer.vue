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
import { API_IP, API_URL } from '../config.js'

export default {
  components: {
    PlayerButton,
  },
  data() {
    return {
      playerWS: WebSocket,
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
    initEventListeners() {
      const play = document.getElementById("player_play");
      const pause = document.getElementById("player_pause");
      const previous = document.getElementById("player_previous");
      const next = document.getElementById("player_next");
      const shuffle = document.getElementById("player_shuffle");
      const repeat = document.getElementById("player_repeat");
      const previous10 = document.getElementById("player_previous_10");
      const next10 = document.getElementById("player_next_10");
      play.addEventListener('click', () => {
        this.playerWS.send("play");
      });
      pause.addEventListener('click', () => {
        this.playerWS.send("pause");
      });
      previous.addEventListener('click', () => {
        this.playerWS.send("previous");
      });
      next.addEventListener('click', () => {
        this.playerWS.send("next");
      });
      shuffle.addEventListener('click', () => {
        this.playerWS.send("shuffle");
      });
      repeat.addEventListener('click', () => {
        this.playerWS.send("repeat");
      });
      previous10.addEventListener('click', () => {
        this.playerWS.send("previous10");
      });
      next10.addEventListener('click', () => {
        this.playerWS.send("next10");
      });
    },
    removeEventListeners() {
      const play = document.getElementById("player_play");
      const pause = document.getElementById("player_pause");
      const previous = document.getElementById("player_previous");
      const next = document.getElementById("player_next");
      const shuffle = document.getElementById("player_shuffle");
      const repeat = document.getElementById("player_repeat");
      const previous10 = document.getElementById("player_previous_10");
      const next10 = document.getElementById("player_next_10");
      play.removeEventListeners()
      pause.removeEventListeners()
      previous.removeEventListeners()
      next.removeEventListeners()
      shuffle.removeEventListeners()
      repeat.removeEventListeners()
      previous10.removeEventListeners()
      next10.removeEventListeners()
    },
  },
  mounted: function () {
    const play = document.getElementById("player_play");
    const pause = document.getElementById("player_pause");
    this.playerWS = new WebSocket("ws://" + API_IP + "/echo", "player");
    this.initEventListeners();
    var lastLoadedMediaUri = ""
    this.playerWS.onmessage = (event) => {
      const msg = JSON.parse(event.data);

      const title = document.getElementById("title");
      const artist = document.getElementById("artist");
      const time = document.getElementById("time");
      const duration = document.getElementById("duration");
      const artwork = document.getElementById("player_artwork");
      title.textContent = msg.title
      artist.textContent = msg.artist
      time.textContent = this.msecToTime(new Date(msg.progress))
      duration.textContent = this.msecToTime(new Date(msg.duration))
      if (lastLoadedMediaUri != msg.uri) {
        artwork.src = API_URL + "/artwork?randomizer=" + Date.now()
        lastLoadedMediaUri = msg.uri
      }

      if (msg.playing) {
        play.style.display = "none";
        pause.style.display = "inline-block";
      } else {
        play.style.display = "inline-block";
        pause.style.display = "none";
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