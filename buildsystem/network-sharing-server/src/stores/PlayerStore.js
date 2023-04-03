import { defineStore } from 'pinia'

export const playerStore = defineStore('player', {
  
    state: () => ({ 
        playing: false, 
        nowPlaying: Object,
        playqueueData:Object,
        socketOpened: true
     }),
    getters: {
    },
    actions: {
    },
})