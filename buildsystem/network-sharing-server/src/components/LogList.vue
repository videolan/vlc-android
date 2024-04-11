<template>
  <div class="row  media-content">
    <div class="table-responsive card table-container">

      <table class="table table-hover table-no-margin">
        <thead>
          <tr class="table-striped-columns">
            <th class="col-1 text-center align-middle" role="columnheader" scope="col" v-t="'LOG_TYPE'">
            </th>
            <th class="align-middle" role="columnheader" scope="col" v-t="'LOG_FILE'">
            </th>
            <th class="text-center align-middle" role="columnheader" scope="col" v-t="'DOWNLOAD'">
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="logfile in logs" :key="logfile.path">
            <LogItem :logfile="logfile" @refresh-logs="$emit('refresh-logs')" />
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script>
import LogItem from './LogItem.vue'

export default {
  components: {
    LogItem,
  },
  props: {
    logs: Array
  }

}
</script>
<style lang="scss">
@import '../scss/colors.scss';

.table-container {
  padding: 0;
}
.table-no-margin {
  margin-bottom: 0;
}
</style>