<template>
  <div class="media-info" v-on:click="play(mediaIndex)">
    <div class="media-artwork-container">
      <div class="playing" v-show="media.playing">
        <span class="playing-bar playing-bar1"></span>
        <span class="playing-bar playing-bar2"></span>
        <span class="playing-bar playing-bar3"></span>
      </div>
      <img class="media-artwork" :src="getImageUrl(media)">
    </div>
    <div class="media-text">
      <p class="h6 queue-title text-truncate">{{ media.title }}</p>
      <p class="queue-subtitle text-truncate">{{ media.artist }} - {{ msecToTime(media.length) }}</p>
    </div>
    <PlayerButton type="playlist_remove" id="player_repeat" ref="repeat" v-on:click.stop="removeItem(mediaIndex)" />
  </div>
</template>

<script>
import { API_URL } from '../config.js'
import PlayerButton from './PlayerButton.vue'

export default {
  components: {
    PlayerButton
  },
  props: {
    media: Object,
    mediaIndex: Number
  },
  computed: {
  },
  mounted: function () {
  },
  methods: {
    msecToTime(ms) {
      const seconds = Math.floor((ms / 1000) % 60)
      const minutes = Math.floor((ms / (60 * 1000)) % 60)
      const hours = Math.floor((ms / (3600 * 1000)) % 3600)
      return `${hours < 10 ? '0' + hours : hours}:${minutes < 10 ? '0' + minutes : minutes}:${seconds < 10 ? '0' + seconds : seconds
        }`
    },
    getImageUrl(media) {
      return API_URL + "/artwork?artwork=" + media.artworkURL + "&id=" + media.id
    },
    removeItem(index) {
      this.$root.connection.send("deleteMedia:" + index);
    },
    play(index) {
      this.$root.connection.send("playMedia:" + index);
    }
  }
}
</script>

<style lang="scss">
@import '../scss/app.scss';

.media-info {
  overflow: auto;
  width: 100%;
  display: flex;
  overflow: hidden;
}

.media-text {
  flex: auto;
  min-width: 0;
}

.media-artwork-container {
  width: 42px;
  height: 42px;
  float: left;
}

.media-artwork {
  width: 42px;
  height: 42px;
  border-radius: 6px;
}

.h6.queue-title,
.queue-subtitle {
  padding-left: 16px;
  margin-bottom: 0px;
}

.queue-title {
  font-size: 0.9em !important;
  line-height: 21px;
}

.queue-subtitle {
  font-size: 0.8em !important;
  line-height: 21px;
}
</style>