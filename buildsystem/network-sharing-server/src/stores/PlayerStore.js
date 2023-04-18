import { defineStore } from 'pinia'
import { reactive } from 'vue'
import { useLocalStorage } from '@vueuse/core'

export const playerStore = defineStore('player', {

  state: () => ({
    playing: false,
    nowPlaying: Object,
    playqueueData: Object,
    playqueueShowing: false,
    playQueueEdit: false,
    responsivePlayerShowing: false,
    socketOpened: true,
    warning:Object,
    loading: false,
    displayType:reactive(useLocalStorage('displayType', {}))
  }),
  getters: {
  },
  actions: {
    toggleDisplayType(route) {
      this.displayType[route] = !this.displayType[route]
    },
    togglePlayQueueEdit() {
      this.playQueueEdit = !this.playQueueEdit
    }
  },
})