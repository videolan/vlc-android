<template>
  <tr class="mdc-data-table__row">
    <th class="mdc-data-table__cell" scope="row">{{ logfile.date }}</th>
    <td class="mdc-data-table__cell">
      <a :href="href">
        <button class="material-icons mdc-icon-button" aria-label="download">
          download
        </button>
      </a>
    </td>
  </tr>
</template>

<script>
import { API_CONFIG } from '@/config';
export default {
  props: {
    logfile: Object
  },
  computed: {
    href: function () {
      return `${API_CONFIG.DOWNLOAD_LOGFILE}` + this.logfile.path
    }
  },
  mounted: function () {
    console.log(this.logfile.date + " -> " + this.logfile.path)
    console.log(this.href)
  }
}
</script>