<template>
  <nav class="navbar navbar-light navbar-expand-md shadow-sm sticky-top align-items-center container-fluid main-navbar">
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
      <RouterLink :to="{ name: 'AudioArtists' }">
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
      <button class="btn btn-lg nav-button blink" v-show="!playerStore.socketOpened" v-on:click.stop="disconnectedClicked"
        data-bs-toggle="tooltip" data-bs-placement="bottom" title="Device disconnected">
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
    <ul class="nav justify-content-center navtabs border-top" v-show="playerStore.currentTab != ''">
      <li class="nav-item">
        <RouterLink class="nav-link" v-bind:class="(playerStore.currentTab == 'artists') ? 'active' : ''" :to="{ name: 'AudioArtists' }">Artists</RouterLink>
      </li>
      <li class="nav-item">
        <RouterLink class="nav-link" v-bind:class="(playerStore.currentTab == 'albums') ? 'active' : ''" :to="{ name: 'AudioAlbums' }">Albums</RouterLink>
      </li>
      <li class="nav-item">
        <RouterLink class="nav-link" v-bind:class="(playerStore.currentTab == 'tracks') ? 'active' : ''" :to="{ name: 'AudioTracks' }">Tracks</RouterLink>
      </li>
      <li class="nav-item">
        <RouterLink class="nav-link" v-bind:class="(playerStore.currentTab == 'genres') ? 'active' : ''" :to="{ name: 'AudioGenres' }">Genres</RouterLink>
      </li>
    </ul>
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
      return API_URL + 'icon?id=' + name
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
  border-radius: 0;
  padding: 0;
}

#logo {
  margin: 4px;
}

.blink {
  animation: blinker 1.5s linear infinite;
}

.router-link-active .nav-button img,
.nav-button:hover img {
  color: $primary-color;
  filter: invert(61%) sepia(64%) saturate(4340%) hue-rotate(358deg) brightness(99%) contrast(109%);
}

.nav-button p {
  font-size: 0.7em;
  font-weight: 500;
}

.router-link-active .nav-button p {
  color: $primary-color;
}

.navtabs {
  flex-basis: 100%;
  background-color: #fafafa;
}

.navtabs .nav-link {
  color: #212529;
}

.navtabs .nav-link.active {
  border-bottom: 2px solid $primary-color;
}

#app .main-navbar {
  flex-wrap: wrap;
}

@keyframes blinker {
  50% {
    opacity: 0;
  }
}
</style>