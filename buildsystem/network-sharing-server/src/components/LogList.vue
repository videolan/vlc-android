<template>
  <main class="mdc-top-app-bar--fixed-adjust">

    <div class="mdc-layout-grid">
      <div class="mdc-layout-grid__inner">
        <div class="mdc-layout-grid__cell"></div>
        <div class="mdc-layout-grid__cell">
          <div class="mdc-data-table">
            <div class="mdc-data-table__table-container">
              <table class="mdc-data-table__table" aria-label="Dessert calories">
                <thead>
                  <tr class="mdc-data-table__header-row">
                    <th class="mdc-data-table__header-cell" role="columnheader" scope="col">
                      Log Files
                    </th>
                    <th class="mdc-data-table__header-cell mdc-data-table__header-cell--numeric" role="columnheader"
                      scope="col">Download
                    </th>
                  </tr>
                </thead>
                <tbody class="mdc-data-table__content">
                  <div v-for="logfile in logs" :key="logfile.path">
                    <LogItem :logfile="logfile" />
                  </div>
                </tbody>
              </table>
            </div>
          </div>
        </div>
        <div class="mdc-layout-grid__cell"></div>
      </div>
    </div>
  </main>
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