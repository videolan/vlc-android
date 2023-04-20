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
    warning: Object,
    loading: false,
    displayType: reactive(useLocalStorage('displayType', {})),
    uploadingFiles: []
  }),
  getters: {
  },
  actions: {
    toggleDisplayType(route) {
      this.displayType[route] = !this.displayType[route]
    },
    togglePlayQueueEdit() {
      this.playQueueEdit = !this.playQueueEdit
    },
    changeFileStatus(file, status) {
      const newFile = {file: file, status: status}
      var index = this.uploadingFiles.findIndex(element => element.file.name == file.name)
      if (index == -1) {
        this.uploadingFiles.push(newFile)
      } else {
        this.uploadingFiles[index] = newFile
      }
    },
    clearUploads() {
      this.uploadingFiles = []
    },
    changeProgress(file, progress) {
      this.uploadingFiles.find(element => element.file.name == file.name).progress = progress
    },
    uploadRemaining() {
      return this.uploadingFiles.filter(element => element.status == "uploading").length
    },
    removeUpload(file) {
      var index = this.uploadingFiles.findIndex(element => element.file.name == file.name)
      this.uploadingFiles.splice(index, 1)
    }
  },
})