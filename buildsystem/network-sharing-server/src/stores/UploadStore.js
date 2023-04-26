import { defineStore } from 'pinia'


/**
 * Manages the upload state
 */
export const useUploadStore = defineStore('upload', {

  state: () => ({
    uploadingFiles: []
  }),
  getters: {
  },
  actions: {
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