<template>
  <div class="d-flex flex-column min-vh-100 justify-content-center align-items-center" id="drop_space">
    <input type="file" id="file_upload" />
    <i class="fa fa-cloud drag-icon"></i>
    Please drag & drop any file you want to upload
    <button v-on:click="uploadFile" class="btn btn-primary shadow-none border-0" id="btn_upload">Upload</button>
  </div>
</template>

<script>
import axios from 'axios'
import { API_CONFIG } from '../config'

export default {
  methods: {
    uploadFile() {
      const files = document.getElementById("file_upload").files
      console.log(files)
      if (files.length == 1) {
        console.log(files)
        var formData = new FormData();
        formData.append("media", files[0]);
        formData.append("filename", files[0].name)
        axios.post(`${API_CONFIG.UPLOAD_MEDIA}`, formData, {
          headers: {
            'Content-Type': 'multipart/form-data'
          }
        })
      } else {
        alert("You can only upload one file at a time");
      }

    },
  },
  mounted: function () {
    const drop_space = document.getElementById('drop_space');
    console.log("mounting")
    drop_space.addEventListener('dragend', () => {
      console.log("dragend: ")
    });
    drop_space.addEventListener('drop', () => {
      console.log("Dropping file: ")
    });
    drop_space.addEventListener('dragleave', () => {
      console.log("dragleave: ")
    });
    drop_space.addEventListener('dragenter', () => {
      console.log("dragenter: ")
    });
    drop_space.addEventListener('change', () => {
      console.log("change: ")
    });

    const dropZone = document.querySelector('body');
    dropZone.addEventListener('drop', (e) => {
      console.log("drop zone")
      const fileInput = document.getElementById('file_upload');
      fileInput.files = e.dataTransfer.files;
    });
  }
}
</script>

<style lang="scss">
@import '../scss/app.scss';

.drag-icon {
  padding: 16px;
}

#btn_upload {
  background: $primary-color;
  margin: 16px;
  outline: none;
}
</style>