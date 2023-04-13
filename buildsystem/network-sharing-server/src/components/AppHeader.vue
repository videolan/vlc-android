<template>
  <nav class="navbar navbar-light navbar-expand-md sticky-top align-items-center container-fluid">
    <RouterLink class="flex1" :to="{ name: 'VideoList' }">
      <img id="logo" src="../assets/resources/icon.png" width="48">
    </RouterLink>
    <div class="d-flex justify-content-center">
      <RouterLink :to="{ name: 'VideoList' }">
        <button class="btn btn-lg nav-button medium">
          <img v-bind:src="getAppAsset('ic_menu_video')">
          <p>Video</p>
        </button>
      </RouterLink>
      <RouterLink :to="{ name: 'AudioList' }">
        <button class="btn btn-lg nav-button medium">
          <img v-bind:src="getAppAsset('ic_menu_audio')">
          <p>Audio</p>
        </button>
      </RouterLink>
      <button class="btn btn-lg nav-button medium">
        <img v-bind:src="getAppAsset('ic_menu_folder')">
          <p>Browse</p>
      </button>
      <button class="btn btn-lg nav-button medium">
        <img v-bind:src="getAppAsset('ic_menu_playlist')">
          <p>Playlists</p>
      </button>
    </div>
    <div class="d-flex flex1 justify-content-end">
      <button class="btn btn-lg nav-button blink" v-show="!playerStore.socketOpened" v-on:click.stop="disconnectedClicked" data-bs-toggle="tooltip"
        data-bs-placement="bottom" title="Device disconnected">
        <span class="material-symbols-outlined">cloud_off</span>
      </button>
      <button class="btn btn-lg nav-button" data-bs-toggle="tooltip" data-bs-placement="bottom" title="Search">
        <span class="material-symbols-outlined">search</span>
      </button>
      <div class="dropdown dropstart">
        <button class="btn btn-lg nav-button" type="button" id="dropdownMenuButton1" data-bs-toggle="dropdown"
          aria-expanded="false">
          <span class="material-symbols-outlined">more_vert</span>
        </button>
        <ul class="dropdown-menu" aria-labelledby="dropdownMenuButton1">
          <li>
            <RouterLink class="dropdown-item" :to="{ name: 'MediaUpload' }">
              Send files
            </RouterLink>
          </li>
          <li>
            <RouterLink class="dropdown-item" :to="{ name: 'Logs' }">
              Log files
            </RouterLink>
          </li>
        </ul>
      </div>
    </div>
  </nav>
</template>

<script>
import { playerStore } from '../stores/PlayerStore'
import { mapStores } from 'pinia'
import { Tooltip } from 'bootstrap';
 import { API_URL } from '../config.js'

export default {
  components: {
  },
  methods: {
    disconnectedClicked() {
      this.$root.startWebSocket();
    },
    getAppAsset(name) {
      return API_URL+'icon?id='+name
    }
  },
  computed: {
    ...mapStores(playerStore),
  },
  mounted() {
    var tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'))
    tooltipTriggerList.map(function (tooltipTriggerEl) {
      return new Tooltip(tooltipTriggerEl, {
        trigger: 'hover'
      })
    })
  }
}
</script>

<style lang='scss'>
@import '../scss/colors.scss';

.navbar.navbar-light {
  background-color: $light-grey;
  box-shadow: 0 0 4px rgba(0, 0, 0, .14), 0 4px 8px rgba(0, 0, 0, .28);
  border-radius: 0;
  padding: 0;
}

#logo {
  margin: 4px;
}

.blink {
  animation: blinker 1.5s linear infinite;
}

.router-link-exact-active .nav-button img, .nav-button:hover img {
  color: $primary-color;
  filter: invert(61%) sepia(64%) saturate(4340%) hue-rotate(358deg) brightness(99%) contrast(109%);
}

.nav-button p {
  font-size: 0.7em;
}

.router-link-exact-active .nav-button p {
  color: $primary-color;
}


@keyframes blinker {
  50% {
    opacity: 0;
  }
}
</style>