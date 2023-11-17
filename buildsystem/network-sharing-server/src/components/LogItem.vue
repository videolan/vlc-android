<template>
  <th scope="row" class="align-middle text-center">
    <img class="image-button-image" :src="(this.getImageByType(logfile.type))" width="24" data-bs-toggle="tooltip"
      data-bs-placement="bottom" :title="$t(this.getTitleByType(logfile.type))" />
  </th>
  <td class="align-middle">{{ logfile.date }}</td>
  <td class="text-center">
    <a :href="href" class="">
      <ImageButton type="file_download" />
    </a>
  </td>
</template>

<script>
import { vlcApi } from '@/plugins/api';
import ImageButton from './ImageButton.vue'
import { Tooltip } from 'bootstrap';

export default {
  components: {
    ImageButton,
  },
  props: {
    logfile: Object
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
  },
  mounted: function () {
    var tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'))
    tooltipTriggerList.map(function (tooltipTriggerEl) {
      return new Tooltip(tooltipTriggerEl, {
        trigger: 'hover'
      })
    })
  }
}
</script>

<style lang="scss">
@import '../scss/colors.scss';

.log-download {
  color: $primary-color;
}
</style>