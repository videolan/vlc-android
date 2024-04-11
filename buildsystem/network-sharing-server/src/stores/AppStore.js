import { defineStore } from 'pinia'
import { reactive } from 'vue'
import { useLocalStorage } from '@vueuse/core'

/**
 * App's main store. Manages global ui states
 */
export const useAppStore = defineStore('app', {

  state: () => ({
    loading: false,
    wsTicket: "",
    displayType: reactive(useLocalStorage('displayType', {})),
    videoGrouping: reactive(useLocalStorage('videoGrouping',0)),
    warning: Object,
    socketOpened: true,
    showAddStream: false,
    needRefresh: false,
    darkTheme: reactive(useLocalStorage('darkTheme',false)),
    title: ""
  }),
  getters: {
  },
  actions: {
    toggleDisplayType(route) {
      this.displayType[route] = !this.displayType[route]
    },
    changeGrouping(mode) {
      this.videoGrouping = mode
    },
  },
})