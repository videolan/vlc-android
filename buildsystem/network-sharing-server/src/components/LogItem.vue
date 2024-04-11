<template>
  <td class="align-middle text-center" :class="{ 'current-log': logfile.path == '' }">
    <img class="image-button-image image-button" :src="(this.getImageByType(logfile.type))" v-tooltip
      data-bs-placement="bottom" :title="$t(this.getTitleByType(logfile.type))" />
  </td>
  <td class="align-middle w-auto" :class="{ 'current-log': logfile.path == '' }">
    <span class="new" v-if="logfile.new">NEW</span>
    {{ logfile.date }}
  </td>
  <td class="text-center col-1" :class="{ 'current-log': logfile.path == '' }">
    <a :href="href" class="" v-if="logfile.path != ''">
      <ImageButton type="file_download" v-tooltip data-bs-placement="bottom" :title="$t('DOWNLOAD')" />
    </a>
    <ImageButton v-else-if="!sending" type="file_upload" v-on:click.stop="downloadLocalLog" v-tooltip
      data-bs-placement="bottom" :title="$t('SEND_LOGS')" />
    <div v-else class="spinner-border text-primary send-spinner" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>
  </td>
</template>

<script>
import { vlcApi } from '@/plugins/api';
import ImageButton from './ImageButton.vue'
import { Tooltip } from 'bootstrap';
import { sendLogs } from '../plugins/logger'

export default {
  components: {
    ImageButton,
  },
  emits: ['refresh-logs'],

  props: {
    logfile: Object
  },
  data() {
    return {
      sending: false,
    }
  },
  computed: {
    href: function () {
      return `${vlcApi.downloadLog}` + this.logfile.path
    },
    iclass: function () {
      return `fa fa-` + this.type
    },
  },
  methods: {
    getImageByType(type) {
      switch (type) {
        case 'web':
          return `./icons/web.svg`
        case 'crash':
          return `./icons/crash.svg`
        default:
          return `./icons/mobile.svg`
      }
    },
    getTitleByType(type) {
      switch (type) {
        case 'web':
          return "LOG_TYPE_WEB"
        case 'crash':
          return "LOG_TYPE_CRASH"
        default:
          return `LOG_TYPE_MOBILE`
      }
    },
    downloadLocalLog(e) {
      Tooltip.getInstance(e.target).hide()
      this.sending = true
      sendLogs().then(() => {
        this.sending = false
        this.$emit('refresh-logs')
      })
    }
  },
}
</script>

<style lang="scss">
@import '../scss/colors.scss';

.new {
  background-color: $primary-color;
  padding: 4px;
  font-size: 0.8em;
  font-weight: bold;
  border-radius: 4px;
  margin-right: 8px;
}

.table .current-log {
  background-color: var(--hover-gray);
}

.log-download {
  color: $primary-color;
}

.send-spinner {
  margin: 5px;
}
</style>