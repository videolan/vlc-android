<template>
  <nav class="navbar navbar-light navbar-expand-md ">
    <div class="navbar-nav d-flex align-items-center">
      <RouterLink class="navbar-brand " :to="{ name: 'Home' }">
        <span><img id="logo" src="../assets/resources/icon.png"></span>
        VLC-Android's WebServer
      </RouterLink>
      <RouterLink class="nav-item nav-link" :to="{ name: 'MediaUpload' }">
        Uploads
      </RouterLink>
    </div>
  </nav>
  <div class="warnings">
    <PlayerButton type="cloud_off"  v-show="!playerStore.socketOpened" v-on:click.stop="disconnectedClicked" />
  </div>
</template>

<script>
import PlayerButton from './PlayerButton.vue'
import { playerStore } from '../stores/PlayerStore'
import { mapStores } from 'pinia'

export default {
  components: {
    PlayerButton
  },
  methods: {
    disconnectedClicked() {
      this.$root.startWebSocket();
    }
  },
  computed: {
    ...mapStores(playerStore),
  },
}
</script>

<style lang='scss'>
@import '../scss/app.scss';
.navbar.navbar-light {
  background-color: #FF7700;
  border-color: #e7e7e7;
  box-shadow: 0 0 4px rgba(0, 0, 0, .14), 0 4px 8px rgba(0, 0, 0, .28);
  border-radius: 0;
  padding: 0;
}

.navbar {
  align-items: start;
  justify-content: flex-start;
}

.navbar-brand,
.navbar-brand:hover,
.navbar-brand:focus {
  color: white;
}

.nav-link,
.nav-link:hover,
.nav-link:focus {
  color: white;
}

.warnings {
  position: absolute;
  right: 8px;
  top: 8px;
  border-radius: 24px;
  background-color: $light-grey;
  animation: blinker 1.5s linear infinite;
}


@keyframes blinker {
  50% {
    opacity: 0;
  }
}
</style>