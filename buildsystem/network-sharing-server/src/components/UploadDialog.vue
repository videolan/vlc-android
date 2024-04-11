<template>
  <div class="drop-files" ref="dropFiles" v-bind:class="(this.dragging) ? 'active' : ''"
    @dragenter.prevent="setDragging(true)" @dragover.prevent="setDragging(true)" @dragleave.prevent="setDragging(false)"
    @drop.prevent="onDrop">
    <div class="drop-files-content">
      <img class="image-button-image image-button" :src="(`./icons/file_upload.svg`)" width="82" />
      <p v-t="'DROP_FILES_TIP'"></p>
    </div>
  </div>
  <input type="file" id="file_upload" multiple="true" style="display: none;" v-on:change="filesSelected"
    ref="inputFile" />
  <div v-show="this.uploadStore.uploadingFiles.length > 0" class="uploads">
    <div class="uploads-header d-flex align-items-center">
      <h6 v-if="(this.uploadStore.uploadRemaining() == 0)" v-t="'SEND_FILES'" class="flex1 uploads-title" />
      <h6 v-else class="flex1 uploads-title">{{ $t('UPLOAD_REMAINING', { msg: this.uploadStore.uploadRemaining() }) }}
      </h6>
      <ImageButton type="close" v-on:click="this.uploadStore.clearUploads()" />
    </div>
    <div class="uploads-table table-responsive">

      <table class="table">
        <thead>
          <tr class="">
            <th class="" role="columnheader" scope="col" v-t="'FILE'">
            </th>
            <th class="status-header" role="columnheader" scope="col">
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(fileUpload, index) in uploadStore.uploadingFiles" :key="index">
            <td class="align-middle">
              <p class="text-truncate" data-bs-toggle="tooltip" data-bs-placement="left" :title="(fileUpload.file.name)">
                {{ fileUpload.file.name }}</p>
              <div v-if="(fileUpload.status == 'uploading')" class="progress" role="progressbar" aria-valuenow="0"
                aria-valuemin="0" aria-valuemax="100">
                <div class="progress-bar bg-primary" v-bind:style="{ width: getProgress(fileUpload) }"></div>
              </div>
            </td>
            <td class="align-middle text-end status-cell">
              <div v-if="(fileUpload.status == 'waiting')">
                <ImageButton type="file_upload" class="small" v-on:click="this.$upload(fileUpload.file)" />
                <ImageButton type="delete" class="small" v-on:click="this.deleteUpload(fileUpload.file)" />
              </div>
              <div v-else-if="(fileUpload.status == 'uploading')">
                <div class="spinner-border text-primary align-middle" role="status">
                  <span class="visually-hidden">Loading...</span>
                </div>
                <ImageButton type="cancel" class="small" v-on:click="this.$cancelUploadFile(fileUpload.file)" />
              </div>
              <img v-else-if="(fileUpload.status == 'error')" class="image-button-image" :src="(`./icons/error.svg`)" />
              <div v-else>
                <img class="image-button-image" :src="(`./icons/success.svg`)" />
                <ImageButton type="close" class="small" v-on:click="this.deleteUpload(fileUpload.file)" />
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <button type="button" v-on:click="uploadAll" class="btn btn-outline-primary upload-all" v-t="'UPLOAD_ALL'"></button>
  </div>
</template>
  
<script>
import { useUploadStore } from '../stores/UploadStore'
import { mapStores } from 'pinia'
import ImageButton from './ImageButton.vue'
import { Tooltip } from 'bootstrap';

export default {
  components: {
    ImageButton,
  },
  computed: {
    ...mapStores(useUploadStore)
  },
  data() {
    return {
      dragging: false,
      inActiveTimeout: null
    }
  },
  methods: {
    openFiles() {
      this.$refs.inputFile.click()
    },
    filesSelected() {
      const fileInput = document.getElementById("file_upload")
      for (const file of fileInput.files) {
        this.uploadStore.changeFileStatus(file, "waiting")
      }
      this.refreshTooltips()

    },
    uploadAll() {
      this.uploadStore.uploadingFiles.filter(upload => upload.status == 'waiting').forEach(element => {
        this.$upload(element.file)
      });
    },
    deleteUpload(file) {
      this.uploadStore.removeUpload(file)
    },
    getProgress(fileUpload) {
      return `${fileUpload.progress}%`
    },
    onDrop(e) {
      this.dragging = false
      for (const file of e.dataTransfer.files) {
        this.uploadStore.changeFileStatus(file, "waiting")
      }
      this.refreshTooltips()
    },
    setDragging(dragging) {
      if (!dragging) {
        this.inActiveTimeout = setTimeout(() => {
          this.dragging = false
        }, 50)
      } else {
        this.dragging = true
        clearTimeout(this.inActiveTimeout)
      }
    },
    refreshTooltips() {
      this.$nextTick(() => {
        var tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'))
        tooltipTriggerList.map(function (tooltipTriggerEl) {
          return new Tooltip(tooltipTriggerEl, {
            trigger: 'hover'
          })
        })
      });
    }
  },
  mounted() {
    document.body.addEventListener("dragenter", function (e) {
      this.$log.log("Drag and drop event: " + e.type)
      this.setDragging(true)
      e.preventDefault();
      e.stopPropagation();
    }.bind(this), false);
  }
}
</script>
  
<style lang="scss">
@import '../scss/colors.scss';

.drop-files {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 2000;
  display: flex;
  visibility: hidden;
  opacity: 0;
  transition: visibility 0s, opacity 0.6s ease;
  flex-direction: column;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
}

.drop-files.active {
  opacity: 1;
  visibility: visible;
  background-color: var(--progress-background);
}

.drop-files-content {
  border: 3px dashed $primary-color;
  padding: 32px;
  border-radius: 16px;
  background-color: var(--light-gray);
  opacity: 1;
  display: flex;
  flex-direction: column;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 16px;
}

.uploads {
  position: fixed;
  border: 1px solid rgba(0, 0, 0, 0.075);
  bottom: 0;
  right: 16px;
  width: 450px;
  z-index: 1023;
  background-color: var(--light-gray);
  border-top-left-radius: 8px;
  border-top-right-radius: 8px;
  box-shadow: 0 0 0.25rem 0.125rem rgba(0, 0, 0, 0.075) !important
}

.uploads-header {
  height: 64px;
  background-color: var(--bs-card-bg);
  border-top-left-radius: 8px;
  border-top-right-radius: 8px;
}

.uploads-title {
  padding: 16px;
}

.uploads-table {
  padding: 16px;
  max-height: 55vh;
  overflow: auto;
}

.uploads-table .table {
  table-layout: fixed;
}

.status-cell {
  height: 64px;
  text-align: center;
}

.status-header {
  width: 128px;
}

.upload-all {
  float: right;
  margin: 16px;
}
</style>
  