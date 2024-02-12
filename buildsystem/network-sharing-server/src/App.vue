<template>
  <AppHeader @send-files="sendFiles" />
  <main v-bind:class="(this.playerStore.playing) ? 'footer-bottom-margin' : ''" class="flex1">
    <router-view></router-view>
  </main>
  <PlayQueue />
  <MiniPlayer ref="miniPlayer" />
  <Alert />
  <AddStream />
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
import AddStream from './components/AddStream.vue'
import UploadDialog from './components/UploadDialog.vue'
import { vlcApi } from './plugins/api.js'
import { usePlayerStore } from './stores/PlayerStore'
import { useBrowserStore } from './stores/BrowserStore'
import { useAppStore } from './stores/AppStore'
import { mapStores } from 'pinia'
import http from './plugins/auth'

export default {
  name: 'App',
  components: {
    AppHeader,
    MiniPlayer,
    PlayQueue,
    Alert,
    AddStream,
    UploadDialog,
  },
  computed: {
    ...mapStores(usePlayerStore, useAppStore, useBrowserStore)
  },
  data() {
    return {
      retryDelay: 500,
      retrying: false,
    }
  },
  methods: {
    sendMessage(message, id) {
      this.$log.log(`Sending message : ${message} - ${id}`)
      this.connection.send(
        JSON.stringify({
          message: message,
          id: id,
          authTicket: this.appStore.wsTicket
        })
      )
    },
    askWSTicket() {
      let component = this
      http.get(vlcApi.websocketAuthTicket)
        .catch(function () { })
        .then((response) => {
          if (response !== undefined) component.appStore.wsTicket = response.data
        });
    },
    sendFiles() {
      this.$refs.uploadComponent.openFiles()
    },
    /**
     * Make sure a WS ticket is available before starting the WS connection
     */
    startWS() {
      let component = this
      http.get(vlcApi.websocketAuthTicket)
        .catch(function () { })
        .then((response) => {
          if (response !== undefined) {
            component.appStore.wsTicket = response.data
            this.setupWS()
          }
        });
    },
    setupWS() {
      this.$log.log("Starting connection to WebSocket Server")
      this.connection = new WebSocket(vlcApi.websocket, "player")
      this.connection.onmessage = (event) => {

        const msg = JSON.parse(event.data);
        if (!['now-playing', 'play-queue'].includes(msg.type)) this.$log.info(`WS received with message ${JSON.stringify(msg)}`)
        if (this.playerStore.playing == false && msg.shouldShow) {
          this.$log.info("Starting player ...")
          this.playerStore.playing = true;
        }

        switch (msg.type) {
          case 'player-status':
            this.playerStore.playing = msg.playing;
            if (!msg.playing) {
              this.playerStore.playqueueShowing = false
              this.playerStore.responsivePlayerShowing = false
            }
            break
          case 'volume':
            this.playerStore.volume = msg.volume
            break;
          case 'login-needed':
            if (msg.dialogOpened)
              this.appStore.warning = { type: "warning", message: this.$t('INVALID_LOGIN') }
            else
              this.appStore.warning = undefined
            break;
          case 'now-playing':
            this.playerStore.nowPlaying = msg
            this.playerStore.volume = msg.volume
            break;
          case 'play-queue':
            this.playerStore.playqueueData = msg
            break;
          case 'auth':
            //websockets not authorized. Asking for a ticket
            this.askWSTicket(JSON.parse(msg.initialMessage))
            break;
          case 'playback-control-forbidden':
            this.appStore.warning = { type: "warning", message: this.$t('PLAYBACK_CONTROL_FORBIDDEN') }
            break;
          case 'ml-refresh-needed':
            this.$log.info("ML refresh needed")
            this.appStore.needRefresh = true
            break;
          case 'browser-description':
            this.browserStore.descriptions.push(msg)
            break;
        }
      }

      this.connection.onopen = (event) => {
        clearTimeout(this.retryId)
        this.retryDelay = 500
        this.retrying = false
        this.$log.log(`Successfully connected to the echo websocket server. Event: `, event)
        this.appStore.socketOpened = true;
        this.sendMessage("hello")
      }

      this.connection.onclose = () => {
        this.$log.log("Socket closed")
        this.appStore.socketOpened = false;
        if (!this.retrying) this.retry()
      }

      this.connection.onerror = () => {
        this.$log.log("Socket on error")
        this.appStore.socketOpened = false;
      }
    },
    retry() {
      this.retrying = true
      clearTimeout(this.retryId)
      this.retryDelay = this.retryDelay + 500
      if (this.retryDelay > 10000) this.retryDelay = 10000
      this.setupWS()
      this.retryId = setTimeout(this.retry, this.retryDelay);
      this.$log.log(`Will retry in ${this.retryDelay}ms`)
    }
  },
  created: function () {
    console.log("App.vue onCreated")
    window.addEventListener('resize', () => {
      // We execute the same script as before
      let vh = window.innerHeight * 0.01;
      document.documentElement.style.setProperty('--vh', `${vh}px`);
    });
    if (location.protocol !== 'https:' && process.env.NODE_ENV !== 'development') {
      this.$router.push({ name: 'SslPage' })
    } else {
      this.startWS()
    }

    if (this.appStore.darkTheme) {
      document.documentElement.setAttribute('data-bs-theme', 'dark')
    } else {
      document.documentElement.removeAttribute('data-bs-theme')
    }

  },
  mounted: function () {
    this.appStore.$subscribe((mutation, state) => {
      if (state.darkTheme) {
        document.documentElement.setAttribute('data-bs-theme', 'dark')
      } else {
        document.documentElement.removeAttribute('data-bs-theme')
      }
    })

  },
}
</script>

<style>

main {
  padding-bottom: 48px;
}

::-webkit-scrollbar {
  height: 8px;
  width: 8px;
}

/* Handle */
::-webkit-scrollbar-thumb {
  background: var(--scrollbar-thumb);
}

::-webkit-scrollbar-track {
  background: var(--scrollbar-track);
}

::-webkit-scrollbar-thumb:window-inactive {
  background: var(--scrollbar-thumb);
}

.footer-bottom-margin {
  overflow: auto;
}

.main-loading {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);

}
</style>
