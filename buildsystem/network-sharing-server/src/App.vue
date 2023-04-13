<template>
  <AppHeader />
  <main v-bind:class="(this.playerStore.playing) ? 'footer-bottom-margin' : ''">
    <router-view></router-view>
  </main>
  <PlayQueue :show="this.playerStore.playqueueShowing" />
  <MiniPlayer ref="miniPlayer" />
  <Alert />
  <div class="main-loading" v-show="this.playerStore.loading">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
  </div>
</template>

<script>
import AppHeader from './components/AppHeader.vue'
import MiniPlayer from './components/MiniPlayer.vue'
import PlayQueue from './components/PlayQueue.vue'
import Alert from './components/Alert.vue'
import { API_IP } from './config.js'
import { playerStore } from './stores/PlayerStore'
import { mapStores } from 'pinia'

export default {
  name: 'App',
  components: {
    AppHeader,
    MiniPlayer,
    PlayQueue,
    Alert,
  },
  computed: {
    ...mapStores(playerStore)
  },
  methods: {
    startWebSocket() {
      console.log("Starting connection to WebSocket Server")
      this.connection = new WebSocket("ws://" + API_IP + "/echo", "player")
      this.connection.onmessage = (event) => {

        if (event.data === "Stopped") {
          console.log("Stopping Player ...")
          this.playerStore.playing = false;
        } else {
          const msg = JSON.parse(event.data);
          if (this.playerStore.playing == false && msg.shouldShow) {
            console.log("Starting player ...")
            this.playerStore.playing = true;
          }

          switch (msg.type) {
            case 'volume':
              this.playerStore.volume = msg.volume
              break;
            case 'now-playing':
              this.playerStore.nowPlaying = msg
              this.playerStore.volume = msg.volume
              break;
            case 'play-queue':
              this.playerStore.playqueueData = msg
              break;
          }
        }
      }

      this.connection.onopen = (event) => {
        console.log(event)
        console.log("Successfully connected to the echo websocket server...")
        this.playerStore.socketOpened = true;
      }

      this.connection.onclose = () => {
        console.log("Socket closed")
        this.playerStore.socketOpened = false;
      }

      this.connection.onerror = () => {
        console.log("Socket on error")
        this.playerStore.socketOpened = false;
      }
    }
  },
  created: function () {
    this.startWebSocket()
  }
}
</script>

<style>
::-webkit-scrollbar {
  height: 5px;
  width: 5px;
}

/* Handle */
::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.4);
}

::-webkit-scrollbar-track {
  background: rgba(0, 0, 0, 0.05);
}

::-webkit-scrollbar-thumb:window-inactive {
  background: rgba(0, 0, 0, 0.4);
}

.footer-bottom-margin {
  margin-bottom: var(--playerHeight);
}

.main-loading {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);

}
</style>
