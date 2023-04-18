<template>
  <nav class="navbar navbar-light navbar-expand-md shadow-sm sticky-top align-items-center container-fluid main-navbar">
    <RouterLink class="flex1" :to="{ name: 'VideoList' }">
      <img id="logo" src="../assets/resources/icon.png" width="48">
    </RouterLink>
    <div class="d-flex justify-content-center">
      <RouterLink :to="{ name: 'VideoList' }">
        <button class="btn btn-lg nav-button medium">
          <img v-bind:src="getAppAsset('ic_menu_video')">
          <p v-t="'VIDEO'"></p>
        </button>
      </RouterLink>
      <RouterLink :to="{ name: 'AudioArtists' }">
        <button class="btn btn-lg nav-button medium">
          <img v-bind:src="getAppAsset('ic_menu_audio')">
          <p v-t="'AUDIO'"></p>
        </button>
      </RouterLink>
      <button class="btn btn-lg nav-button medium">
        <img v-bind:src="getAppAsset('ic_menu_folder')">
        <p v-t="'BROWSE'"></p>
      </button>
      <RouterLink :to="{ name: 'PlaylistList' }">
        <button class="btn btn-lg nav-button medium">
          <img v-bind:src="getAppAsset('ic_menu_playlist')">
          <p v-t="'PLAYLISTS'"></p>
        </button>
      </RouterLink>
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
            <RouterLink class="dropdown-item" :to="{ name: 'MediaUpload' }" v-t="'DROP_FILES'">
            </RouterLink>
          </li>
          <li>
            <RouterLink class="dropdown-item" :to="{ name: 'Logs' }" v-t="'LOG_FILE'">
            </RouterLink>
          </li>
        </ul>
      </div>
    </div>
    <div class=" navtabs-container border-top" v-show="this.$route.meta.showDisplayBar">
      <div class="flex1">
        <button class="btn btn-lg nav-button" type="button" aria-expanded="false"
          v-on:click.stop="this.playerStore.toggleDisplayType(this.$route.name)"
          v-show="this.playerStore.displayType[this.$route.name]">
          <span class="material-symbols-outlined">grid_view</span>
        </button>
        <button class="btn btn-lg nav-button" type="button" v-show="!this.playerStore.displayType[this.$route.name]"
          aria-expanded="false" v-on:click.stop="this.playerStore.toggleDisplayType(this.$route.name)">
          <span class="material-symbols-outlined">view_list</span>
        </button>
      </div>
      <ul class="nav justify-content-center navtabs">
        <li class="nav-item">
          <RouterLink class="nav-link" v-show="this.$route.meta.isAudio"
            v-bind:class="(this.$route.name == 'AudioArtists') ? 'active text-primary' : ''"
            :to="{ name: 'AudioArtists' }" v-t="'ARTISTS'">
          </RouterLink>
        </li>
        <li class="nav-item">
          <RouterLink class="nav-link" v-show="this.$route.meta.isAudio"
            v-bind:class="(this.$route.name == 'AudioAlbums') ? 'active text-primary' : ''" :to="{ name: 'AudioAlbums' }"
            v-t="'ALBUMS'">
          </RouterLink>
        </li>
        <li class="nav-item">
          <RouterLink class="nav-link" v-show="this.$route.meta.isAudio"
            v-bind:class="(this.$route.name == 'AudioTracks') ? 'active text-primary' : ''" :to="{ name: 'AudioTracks' }"
            v-t="'TRACKS'">
          </RouterLink>
        </li>
        <li class="nav-item">
          <RouterLink class="nav-link" v-show="this.$route.meta.isAudio"
            v-bind:class="(this.$route.name == 'AudioGenres') ? 'active text-primary' : ''" :to="{ name: 'AudioGenres' }"
            v-t="'GENRES'"> </RouterLink>
        </li>
      </ul>
      <div class="flex1">&nbsp;</div>
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

.navtabs-container {
  flex-basis: 100%;
  background-color: #fafafa;
  display: flex;
}

.navtabs {
  align-items: end;
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
}</style>