<template>
  <AppHeader @send-files="sendFiles" />
  <main v-bind:class="(this.playerStore.playing) ? 'footer-bottom-margin' : ''">
    <router-view></router-view>
  </main>
  <PlayQueue :show="this.playerStore.playqueueShowing" />
  <MiniPlayer ref="miniPlayer" />
  <Alert />
  <UploadDialog ref="uploadComponent" />
  <div class="main-loading" v-show="this.appStore.loading">
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
import UploadDialog from './components/UploadDialog.vue'
import { API_IP } from './config.js'
import { usePlayerStore } from './stores/PlayerStore'
import { useAppStore } from './stores/AppStore'
import { mapStores } from 'pinia'

export default {
  name: 'App',
  components: {
    AppHeader,
    MiniPlayer,
    PlayQueue,
    Alert,
    UploadDialog,
  },
  computed: {
    ...mapStores(usePlayerStore, useAppStore)
  },
  data() {
    return {
      retryDelay: 500,
      retrying: false,
    }
  },
  methods: {
    sendMessage(message, id) {
      this.connection.send(
        JSON.stringify({
          message: message,
          id: id
        })
      )
    },
    sendFiles() {
      this.$refs.uploadComponent.openFiles()
    },
    startWebSocket() {
      console.log("Starting connection to WebSocket Server")
      this.connection = new WebSocket("ws://" + API_IP + "/echo", "player")
      this.connection.onmessage = (event) => {

        const msg = JSON.parse(event.data);
        if (this.playerStore.playing == false && msg.shouldShow) {
          console.log("Starting player ...")
          this.playerStore.playing = true;
        }

        switch (msg.type) {
          case 'player-status':
            this.playerStore.playing = msg.playing;
            break
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

      this.connection.onopen = (event) => {
        clearTimeout(this.retryId)
        this.retryDelay = 500
        this.retrying = false
        console.log(event)
        console.log("Successfully connected to the echo websocket server...")
        this.appStore.socketOpened = true;
      }

      this.connection.onclose = () => {
        console.log("Socket closed")
        this.appStore.socketOpened = false;
        if (!this.retrying) this.retry()
      }

      this.connection.onerror = () => {
        console.log("Socket on error")
        this.appStore.socketOpened = false;
      }
    },
    retry() {
      this.retrying = true
      clearTimeout(this.retryId)
      this.retryDelay = this.retryDelay + 500
      if (this.retryDelay > 10000) this.retryDelay = 10000
      this.startWebSocket()
      this.retryId = setTimeout(this.retry, this.retryDelay);
      console.log(`Will retry in ${this.retryDelay}ms`)
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
