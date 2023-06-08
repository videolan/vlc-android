<template>
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
  mounted: function () {
  }
}
</script>

<style lang="scss">
@import '../scss/colors.scss';

.log-download {
  color: $primary-color;
}
</style>