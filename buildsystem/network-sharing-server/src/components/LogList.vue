<template>
  <div class="row  media-content">
    <table class="table">
      <thead>
        <tr class="">
          <th class="" role="columnheader" scope="col" v-t="'LOG_FILE'">
          </th>
          <th class="text-center" role="columnheader" scope="col" v-t="'DOWNLOAD'">
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="logfile in logs" :key="logfile.path">
          <LogItem :logfile="logfile" />
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script>
import LogItem from './LogItem.vue'

export default {
  components: {
    LogItem,
  },
  props: {
    logs: []
  }

}
</script>