<template>
  <nav class="navbar navbar-light navbar-expand-md shadow-sm sticky-top align-items-center container-fluid main-navbar">
    <div class="flex1-collapsible">
      <RouterLink :to="{ name: 'VideoList' }">
        <img id="logo" v-bind:src="$getAppAsset('ic_icon', 48, true)" width="48" v-on:click="iconClick()"
          v-bind:class="this.clicked > 2 && this.clicked % 2 == 1 ? 'animate' : ''">
      </RouterLink>
    </div>
    <div class="d-flex justify-content-center nav-collapsed collapsed">
      <p class="text-primary nav-main-title">{{ getRouteName() }}</p>
    </div>
    <div class="dropdown dropstart nav-collapsed">
      <ImageButton type="menu" data-bs-toggle="dropdown" aria-expanded="false" />

      <ul class="dropdown-menu dropdown-menu-start">
        <li>
          <RouterLink :to="{ name: 'VideoList' }">
            <div class="nav-button d-flex btn  btn-lg medium">
              <img class="nav-button" v-bind:src="$getAppAsset('ic_video')">
              <p class="collapsed-menu-text" v-t="'VIDEO'"></p>
            </div>
          </RouterLink>
          <RouterLink :to="{ name: 'AudioArtists' }">
            <div class="nav-button d-flex btn  btn-lg medium">
              <img v-bind:src="$getAppAsset('ic_menu_audio')">
              <p class="collapsed-menu-text" v-t="'AUDIO'"></p>
            </div>
          </RouterLink>
          <RouterLink :to="{ name: 'BrowseList' }">
            <div class="nav-button d-flex btn  btn-lg medium">
              <img v-bind:src="$getAppAsset('ic_folder')">
              <p class="collapsed-menu-text" v-t="'BROWSE'"></p>
            </div>
          </RouterLink>
          <RouterLink :to="{ name: 'PlaylistList' }">
            <div class="nav-button d-flex btn  btn-lg medium">
              <img v-bind:src="$getAppAsset('ic_playlist')">
              <p class="collapsed-menu-text" v-t="'PLAYLISTS'"></p>
            </div>
          </RouterLink>
        </li>
        <li>
          <hr class="dropdown-divider">
        </li>
        <li>
          <RouterLink :to="{ name: 'SearchList' }">
            <div class="nav-button d-flex btn  btn-lg medium">
              <img :src="(`./icons/search.svg`)">
              <p class="collapsed-menu-text" v-t="'SEARCH'"></p>
            </div>
          </RouterLink>
        </li>

        <li>
          <RouterLink :to="{ name: 'Logs' }">
            <div class="nav-button d-flex btn  btn-lg medium">
              <img :src="(`./icons/crash.svg`)">
              <p class="collapsed-menu-text" v-t="'LOG_FILE'"></p>
            </div>
          </RouterLink>
        </li>
        <li>
          <hr class="dropdown-divider">
        </li>
        <li>
          <div class="nav-button d-flex btn  btn-lg medium" v-on:click="this.$emit('send-files')">
              <img :src="(`./icons/file_upload.svg`)">
              <p class="collapsed-menu-text" v-t="'SEND_FILES'"></p>
            </div>
        </li>
        <li>
          <div class="nav-button d-flex btn  btn-lg medium" v-on:click="changeTheme">
              <img :src="(`./icons/dark_mode.svg`)" v-show="(!this.appStore.darkTheme)">
              <p class="collapsed-menu-text" v-t="'DARK_THEME'" v-show="(!this.appStore.darkTheme)"></p>
              <img :src="(`./icons/light_mode.svg`)" v-show="(this.appStore.darkTheme)">
              <p class="collapsed-menu-text" v-t="'LIGHT_THEME'" v-show="(this.appStore.darkTheme)"></p>
            </div>
        </li>
      </ul>
    </div>
    <div class="d-flex justify-content-center nav-collapsible">
      <RouterLink :to="{ name: 'VideoList' }">
        <button class="btn btn-lg nav-button medium">
          <img v-bind:src="$getAppAsset('ic_video')">
          <p v-t="'VIDEO'"></p>
        </button>
      </RouterLink>
      <RouterLink :to="{ name: 'AudioArtists' }">
        <button class="btn btn-lg nav-button medium">
          <img v-bind:src="$getAppAsset('ic_menu_audio')">
          <p v-t="'AUDIO'"></p>
        </button>
      </RouterLink>
      <RouterLink :to="{ name: 'BrowseList' }">
        <button class="btn btn-lg nav-button medium">
          <img v-bind:src="$getAppAsset('ic_folder')">
          <p v-t="'BROWSE'"></p>
        </button>
      </RouterLink>
      <RouterLink :to="{ name: 'PlaylistList' }">
        <button class="btn btn-lg nav-button medium">
          <img v-bind:src="$getAppAsset('ic_playlist')">
          <p v-t="'PLAYLISTS'"></p>
        </button>
      </RouterLink>
    </div>
    <div class="d-flex flex1 justify-content-end nav-collapsible">
      <ImageButton type="cloud_off" class="blink" v-show="!appStore.socketOpened" v-on:click.stop="disconnectedClicked"
        v-tooltip data-bs-placement="bottom" :title="$t('DISCONNECTED')" />
      <RouterLink :to="{ name: 'SearchList' }">
        <ImageButton type="search" v-tooltip data-bs-placement="bottom" :title="$t('SEARCH')" />
      </RouterLink>
      <div class="dropdown dropstart">
        <ImageButton type="more_vert" data-bs-toggle="dropdown" aria-expanded="false" />
        <ul class="dropdown-menu nav-dropdown">
          <li>
          <RouterLink :to="{ name: 'Logs' }">
            <div class="nav-button d-flex btn  btn-lg medium">
              <img :src="(`./icons/crash.svg`)">
              <p class="collapsed-menu-text" v-t="'LOG_FILE'"></p>
            </div>
          </RouterLink>
        </li>
        <li>
          <hr class="dropdown-divider">
        </li>
        <li>
          <div class="nav-button d-flex btn  btn-lg medium" v-on:click="this.$emit('send-files')">
              <img :src="(`./icons/file_upload.svg`)">
              <p class="collapsed-menu-text" v-t="'SEND_FILES'"></p>
            </div>
        </li>
        <li>
          <div class="nav-button d-flex btn  btn-lg medium" v-on:click="changeTheme">
              <img :src="(`./icons/dark_mode.svg`)" v-show="(!this.appStore.darkTheme)">
              <p class="collapsed-menu-text" v-t="'DARK_THEME'" v-show="(!this.appStore.darkTheme)"></p>
              <img :src="(`./icons/light_mode.svg`)" v-show="(this.appStore.darkTheme)">
              <p class="collapsed-menu-text" v-t="'LIGHT_THEME'" v-show="(this.appStore.darkTheme)"></p>
            </div>
        </li>
        </ul>
      </div>
    </div>
    <div class="navtabs-container border-bottom" v-show="this.$route.meta.showDisplayBar">
      <div class="flex1 d-flex align-items-center">
        <ImageButton :type="(this.appStore.displayType[this.$route.name]) ? 'grid_view' : 'view_list'"
          v-on:click.stop="this.appStore.toggleDisplayType(this.$route.name)" v-tooltip data-bs-placement="bottom"
          :title="$t((this.appStore.displayType[this.$route.name]) ? 'DISPLAY_GRID' : 'DISPLAY_LIST')" />

        <div class="dropdown" v-show="this.$route.meta.showGrouping">
          <button class="btn btn-lg image-button hidden-arrow" type="button" data-bs-toggle="dropdown"
            aria-expanded="false">
            <img class="image-button-image" v-bind:src="$getAppAsset('ic_group', 24)">
          </button>
          <ul class="dropdown-menu dropdown-menu-start">
            <li><a class="dropdown-item cursor-pointer" v-bind:class="isActive(0) ? 'active' : ''"
                v-t="'VIDEO_GROUP_NONE'" v-on:click="this.appStore.changeGrouping(0)"></a></li>
            <li><a class="dropdown-item cursor-pointer" v-bind:class="isActive(1) ? 'active' : ''"
                v-t="'VIDEO_GROUP_BY_FOLDER'" v-on:click="this.appStore.changeGrouping(1)"></a></li>
            <li><a class="dropdown-item cursor-pointer" v-bind:class="isActive(2) ? 'active' : ''"
                v-t="'VIDEO_GROUP_BY_NAME'" v-on:click="this.appStore.changeGrouping(2)"></a></li>
          </ul>
        </div>
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
            v-bind:class="(this.$route.name == 'AudioAlbums') ? 'active text-primary' : ''"
            :to="{ name: 'AudioAlbums' }" v-t="'ALBUMS'">
          </RouterLink>
        </li>
        <li class="nav-item">
          <RouterLink class="nav-link" v-show="this.$route.meta.isAudio"
            v-bind:class="(this.$route.name == 'AudioTracks') ? 'active text-primary' : ''"
            :to="{ name: 'AudioTracks' }" v-t="'TRACKS'">
          </RouterLink>
        </li>
        <li class="nav-item">
          <RouterLink class="nav-link" v-show="this.$route.meta.isAudio"
            v-bind:class="(this.$route.name == 'AudioGenres') ? 'active text-primary' : ''"
            :to="{ name: 'AudioGenres' }" v-t="'GENRES'"> </RouterLink>
        </li>
      </ul>

      <!-- Breadcrumb -->
      <div class="d-flex align-items-center" v-if="this.hasBreadcrumb()">
        <nav aria-label="breadcrumb">
          <ol class="breadcrumb">
            <li class="breadcrumb-item"
              v-bind:class="(item.path == this.$route.params.browseId) ? '' : 'text-primary clickable'"
              v-for="item in this.browserStore.breadcrumb" :key="item.path" v-on:click="manageClick(item)">
              {{ item.title }}
            </li>
          </ol>
        </nav>

      </div>
      <div class="d-flex align-items-center" v-else-if="this.getTitle() && $route.meta.icon">
        <div class="text-primary breadcrumb"><img v-bind:src="$getAppAsset($route.meta.icon)"> <span class="breadcrumb-content-item">{{ this.getTitle() }}</span></div>
      </div>

      <div class="flex1 d-flex justify-content-end align-items-center">
        <button class="btn btn-lg image-button" v-show="this.$route.meta.showFAB"
          v-on:click.stop="$playAll(this.$route)" v-tooltip data-bs-placement="bottom" :title="$t('PLAY_ALL')">
          <img class="image-button-image" v-bind:src="$getAppAsset('ic_ctx_play_all', 24)">
        </button>
        <button class="btn btn-lg image-button" v-show="this.$route.meta.showResume"
          v-on:click.stop="$resumePlayback(this.$route.meta.isAudio)" v-tooltip data-bs-placement="bottom"
          :title="$t('RESUME_PLAYBACK')">
          <img class="image-button-image" v-bind:src="$getAppAsset('ic_resume_playback', 24)">
        </button>
      </div>
    </div>
  </nav>
</template>

<script>
import { useAppStore } from '../stores/AppStore'
import { useBrowserStore } from '../stores/BrowserStore'
import { mapStores } from 'pinia'
import ImageButton from './ImageButton.vue'

export default {
  components: {
    ImageButton,
  },
  data() {
    return {
      clicked: 0
    }
  },
  methods: {
    disconnectedClicked() {
      this.$root.startWebSocket();
    },
    manageClick(browsePoint) {
      if (browsePoint.path == "" || browsePoint.path == "root") {
        this.$router.push({ name: 'BrowseList' })
      } else {
        this.$router.push({ name: 'BrowseChild', params: { browseId: browsePoint.path } })
      }
    },
    hasBreadcrumb() {
      return this.browserStore.breadcrumb.length != 0
    },
    getTitle() {
      return this.appStore.title
    },
    isActive(mode) {
      return this.appStore.videoGrouping == mode
    },
    changeTheme() {
      this.appStore.darkTheme = !this.appStore.darkTheme
    },
    iconClick() {
      this.clicked++
    },
    getRouteName() {
      if (this.$route.matched[0]) {
        switch (this.$route.matched[0].name) {
          case 'Video':
            return this.$t('VIDEO')
          case 'AudioArtists':
            return this.$t('AUDIO')
          case 'Browse':
            return this.$t('BROWSE')
          case 'Playlist':
            return this.$t('PLAYLISTS')
          case 'SearchList':
            return this.$t('SEARCH')
          case 'Logs':
            return this.$t('LOG_FILE')
          default:
            return ""
        }
      } else {
        return ""
      }
    }
  },
  computed: {
    ...mapStores(useAppStore),
    ...mapStores(useBrowserStore),
  },
}
</script>

<style lang='scss'>
@import '../scss/colors.scss';

.nav-main-title {
  font-weight: bold;
}

.nav-dropdown a, .nav-collapsed a {
  text-decoration: none;
}

.collapsed-menu-text {
  margin-left: 8px;
}

.d-flex.nav-collapsed,
.nav-collapsed {
  display: none !important;
}

.flex1-collapsible {
  flex: 1;
}

@media screen and (max-width: 580px) {

  .d-flex.nav-collapsible,
  .nav-collapsible {
    display: none !important;
  }

  .d-flex.nav-collapsed,
  .nav-collapsed {
    display: inherit !important;
  }

  .flex1-collapsible {
    flex: none;
  }

}

.navbar.navbar-light {
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

.nav-button img, .breadcrumb img {
  color: $primary-color;
  filter: var(--img-tint);
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
  display: flex;
}

.navtabs {
  align-items: end;
}

.navtabs .nav-link {
  color: var(--bs-btn-color);
}

.navtabs .nav-link.active {
  border-bottom: 2px solid $primary-color;
}

#app .main-navbar {
  flex-wrap: wrap;
}

.breadcrumb-content-item {
  margin-left: var(--bs-breadcrumb-item-padding-x);
}

.breadcrumb-content-item::before {
  content: '>';
  margin-right: var(--bs-breadcrumb-item-padding-x);
}

@keyframes blinker {
  50% {
    opacity: 0;
  }
}

.cursor-pointer {
  cursor: pointer;
}

.animate {
  animation-name: rotating, bounce, jello;
  animation-duration: 1000ms, 1000ms, 1000ms;
  animation-delay: 0ms, 1000ms, 1000ms;

}

@keyframes rotating {
  0% {
    transform: rotate(0deg);
  }

  100% {
    transform: rotate(360deg) translateY(-23px);

  }
}

@keyframes bounce {
  0% {
    animation-timing-function: ease-in;
    opacity: 1;
    transform: translateY(-23px);
  }

  24% {
    opacity: 1;
  }

  40% {
    animation-timing-function: ease-in;
    transform: translateY(-12px);
  }

  65% {
    animation-timing-function: ease-in;
    transform: translateY(-6px);
  }

  82% {
    animation-timing-function: ease-in;
    transform: translateY(-3px);
  }

  93% {
    animation-timing-function: ease-in;
    transform: translateY(-2px);
  }

  25%,
  55%,
  75%,
  87% {
    animation-timing-function: ease-out;
    transform: translateY(0px);
  }

  100% {
    animation-timing-function: ease-out;
    opacity: 1;
    transform: translateY(0px);
  }
}

@keyframes jello {
  0% {
    transform: scale3d(1, 1, 1);
  }

  30% {
    transform: scale3d(1.1, 0.9, 1);
  }

  40% {
    transform: scale3d(0.9, 1.1, 1);
  }

  50% {
    transform: scale3d(1.05, 0.95, 1);
  }

  65% {
    transform: scale3d(0.98, 1.02, 1);
  }

  75% {
    transform: scale3d(1.02, 0.98, 1);
  }

  100% {
    transform: scale3d(1, 1, 1);
  }
}
</style>nav-dropdown