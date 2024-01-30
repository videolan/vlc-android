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
    warning: Object,
    socketOpened: true,
    showAddStream: false,
    needRefresh: false
  }),
  getters: {
  },
  actions: {
    toggleDisplayType(route) {
      this.displayType[route] = !this.displayType[route]
    },
  },
})