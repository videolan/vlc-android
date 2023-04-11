<template>
  <nav class="navbar navbar-light navbar-expand-md  align-items-center container-fluid">
    <RouterLink class="flex1" :to="{ name: 'VideoList' }">
      <img id="logo" src="../assets/resources/icon.png" width="48">
    </RouterLink>
    <div class="d-flex justify-content-center">
      <RouterLink :to="{ name: 'VideoList' }">
        <button class="btn btn-lg nav-button medium" data-bs-toggle="tooltip" data-bs-placement="bottom" title="Video">
          <span class="material-symbols-outlined">movie</span>
        </button>
      </RouterLink>
      <RouterLink :to="{ name: 'AudioList' }">
        <button class="btn btn-lg nav-button medium" data-bs-toggle="tooltip" data-bs-placement="bottom" title="Audio">
          <span class="material-symbols-outlined">audiotrack</span>
        </button>
      </RouterLink>
      <button class="btn btn-lg nav-button medium" data-bs-toggle="tooltip" data-bs-placement="bottom" title="Browse">
        <span class="material-symbols-outlined">folder</span>
      </button>
      <button class="btn btn-lg nav-button medium" data-bs-toggle="tooltip" data-bs-placement="bottom" title="Playlists">
        <span class="material-symbols-outlined">playlist_play</span>
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

export default {
  components: {
  },
  methods: {
    disconnectedClicked() {
      this.$root.startWebSocket();
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
@import '../scss/app.scss';

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

.router-link-exact-active .nav-button {
  color: $primary-color;
}


@keyframes blinker {
  50% {
    opacity: 0;
  }
}
</style>