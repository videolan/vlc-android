<template>
  <div class="row">
    <table class="table">
      <thead>
        <tr class="">
          <th class="" role="columnheader" scope="col">
            Log Files
          </th>
          <th class="text-center" role="columnheader" scope="col">
            Download
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