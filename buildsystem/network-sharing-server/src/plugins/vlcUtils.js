import { vlcApi } from '../plugins/api.js'
import axios from 'axios'
import { useAppStore } from '../stores/AppStore'
import { useUploadStore } from '../stores/UploadStore'
import geti18n from "../i18n";

export default {
    install: (app) => {
        const appStore = useAppStore()
        const uploadStore = useUploadStore()
        const controllersForFile = []
        app.config.globalProperties.$readableDuration = (ms) => {
            const seconds = Math.floor((ms / 1000) % 60)
            const minutes = Math.floor((ms / (60 * 1000)) % 60)
            const hours = Math.floor((ms / (3600 * 1000)) % 3600)
            return `${hours == 0 ? '' : hours + ":"}${hours == 0 && minutes < 10 ? minutes : minutes < 10 ? '0' + minutes : minutes}:${seconds < 10 ? '0' + seconds : seconds}`
        }
        app.config.globalProperties.$getAppAsset = (name, width, preventTint) => {
            if (width < 0 || width === undefined) width = 24
            return vlcApi.appAsset(name, width, preventTint)
        }

        app.config.globalProperties.$getImageUrl = (media, mediaType) => {
            return vlcApi.artwork(media.artworkURL, media.id, mediaType)
        }

        app.config.globalProperties.$download = (media, mediaType, directDownload) => {
            if (directDownload) {
                window.location.href = vlcApi.prepareDownload(media.id, mediaType)
            } else {
                appStore.warning = { type: "message", message: geti18n().global.t("PREPARING_DOWNLOAD") }
                axios.get(vlcApi.prepareDownload(media.id, mediaType)).then((response) => {
                    appStore.warning = undefined
                    window.location.href = vlcApi.download(response.data)
                });
            }
        }

        app.config.globalProperties.$play = (media, mediaType, append, asAudio) => {
            if (mediaType == "new-stream" && media.id == -1) {
                appStore.showAddStream = true
            } else
            axios.get(vlcApi.play(media, mediaType, append, asAudio))
                .catch(function (error) {
                    if (error.response.status != 200) {
                        appStore.warning = { type: "warning", message: error.response.data }
                    }
                })
        }
        app.config.globalProperties.$playAll = (route) => {
            let type= route.meta.playAllType

            let id
            switch (type) {
                case "video-group":
                    id = route.params.groupId
                    break
                case "video-folder": id = route.params.folderId
                    break
                case "artist": id = route.params.artistId
                    break
                case "album": id = route.params.albumId
                    break
                case "genre": id = route.params.genreId
                    break
                case "playlist": id = route.params.playlistId
                    break
                default: id = 0
            }
            let path = (type == "browser") ? route.params.browseId : ""
             axios.get(vlcApi.playAll(type, id, path))
                 .catch(function (error) {
                     if (error.response.status != 200) {
                         appStore.warning = { type: "warning", message: error.response.data }
                     }
                 })
        }
        app.config.globalProperties.$resumePlayback = (isAudio) => {
            axios.get(vlcApi.resumePlayback(isAudio)).then((response) => {
                if (response.status == 204) {
                    appStore.warning = { type: "warning", message: geti18n().global.t("NOTHING_RESUME") }
                }
            });
        }
        app.config.globalProperties.$upload = (file) => {
            const controller = new AbortController();
            const onUploadProgress = (progressEvent) => {
                const { loaded, total } = progressEvent;
                let percent = Math.floor((loaded * 100) / total);
                uploadStore.changeProgress(file, percent)
            };
            var formData = new FormData();
            formData.append("media", file);
            formData.append("filename", file.name)
            uploadStore.changeFileStatus(file, 'uploading')
            axios.post(`${vlcApi.uploadMedia}`, formData, {
                headers: {
                    'Content-Type': 'multipart/form-data'
                },
                signal: controller.signal,
                onUploadProgress
            }).catch(function () {
                uploadStore.changeFileStatus(file, 'error')

            }).then((response) => {
                if (response === undefined) uploadStore.changeFileStatus(file, 'waiting')
                else
                    uploadStore.changeFileStatus(file, 'uploaded')
            })
            controllersForFile.push({
                file: file,
                controller: controller
            })
        }
        app.config.globalProperties.$cancelUploadFile = (file) => {
            let controller = controllersForFile.findLast((element) => element.file == file)
            const index = controllersForFile.indexOf(controller)
            controller.controller.abort()
            controllersForFile.splice(index, 1)
        }
        //prevent unloading when an upload is running
        window.onbeforeunload = function () {
            if (uploadStore.uploadingFiles.find(element => element.status == "uploading") != null)
                return "";
        }
    }
}