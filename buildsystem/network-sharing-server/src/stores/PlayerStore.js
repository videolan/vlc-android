import { defineStore } from 'pinia'

export const playerStore = defineStore('player', {

  state: () => ({
    playing: false,
    nowPlaying: Object,
    playqueueData: Object,
    playqueueShowing: false,
    responsivePlayerShowing: false,
    socketOpened: true,
    warning:Object,
    loading: false
  }),
  getters: {
  },
  actions: {
  },
})