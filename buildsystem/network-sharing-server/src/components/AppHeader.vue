<template>
  <nav class="navbar navbar-light navbar-expand-md shadow-sm sticky-top align-items-center container-fluid main-navbar">
    <RouterLink class="flex1" :to="{ name: 'VideoList' }">
      <img id="logo" v-bind:src="$getAppAsset('ic_icon', 48, true)" width="48">
    </RouterLink>
    <div class="d-flex justify-content-center">
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
    <div class="d-flex flex1 justify-content-end">
      <ImageButton type="cloud_off" class="blink" v-show="!appStore.socketOpened" v-on:click.stop="disconnectedClicked"
        v-tooltip data-bs-placement="bottom" :title="$t('DISCONNECTED')" />
      <RouterLink :to="{ name: 'SearchList' }">
        <ImageButton type="search" v-tooltip data-bs-placement="bottom" :title="$t('SEARCH')" />
      </RouterLink>
      <div class="dropdown dropstart">
        <ImageButton type="more_vert" data-bs-toggle="dropdown" aria-expanded="false" />
        <ul class="dropdown-menu">
          <li>
            <a v-on:click="this.$emit('send-files')" v-t="'SEND_FILES'" class="dropdown-item clickable"></a>
          </li>
          <li>
            <RouterLink class="dropdown-item" :to="{ name: 'Logs' }" v-t="'LOG_FILE'">
            </RouterLink>
          </li>
          <li><hr class="dropdown-divider"></li>
          <li>
            <a v-on:click="changeTheme" class="dropdown-item clickable">
              <img  class="image-button" :src="(`./icons/checked.svg`)"  v-show="(this.appStore.darkTheme)"/>
            <span  v-t="'DARK_THEME'"></span>
            </a>
          </li>
        </ul>
      </div>
    </div>
    <div class="navtabs-container border-bottom" v-show="this.$route.meta.showDisplayBar">
      <div class="flex1 d-flex align-items-center">
        <ImageButton :type="(this.appStore.displayType[this.$route.name]) ? 'grid_view' : 'view_list'" v-on:click.stop="this.appStore.toggleDisplayType(this.$route.name)"
        v-tooltip data-bs-placement="bottom"
          :title="$t((this.appStore.displayType[this.$route.name]) ? 'DISPLAY_GRID': 'DISPLAY_LIST')" />

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
      <div class="d-flex align-items-center" v-else-if="this.getTitle()">
        <p class="text-primary breadcrumb">{{ this.getTitle() }}</p>
      </div>

      <div class="flex1 d-flex justify-content-end align-items-center">
        <button class="btn btn-lg image-button" v-show="this.$route.meta.showFAB"
        v-on:click.stop="$playAll(this.$route)" v-tooltip data-bs-placement="bottom"
          :title="$t('PLAY_ALL')">
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
    changeTheme () {
      this.appStore.darkTheme = !this.appStore.darkTheme
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

.nav-button img {
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

@keyframes blinker {
  50% {
    opacity: 0;
  }
}

.cursor-pointer {
  cursor: pointer;
}</style>