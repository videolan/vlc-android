<template>
  <AppHeader />
  <router-view></router-view>
  <MiniPlayer ref="miniPlayer" />
</template>

<script>
import AppHeader from './components/AppHeader.vue'
import MiniPlayer from './components/MiniPlayer.vue'
import { API_IP } from './config.js'
import { playerStore } from './stores/PlayerStore'
import { mapStores } from 'pinia'

export default {
  name: 'App',
  components: {
    AppHeader,
    MiniPlayer,
  },
  computed: {
    ...mapStores(playerStore)
  },
  created: function () {
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

    this.connection.onopen = function (event) {
      console.log(event)
      console.log("Successfully connected to the echo websocket server...")
    }
  }
}
</script>

<style></style>
