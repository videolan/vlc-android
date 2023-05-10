import { API_URL, API_CONFIG } from '../config.js'
import axios from 'axios'
import { useAppStore } from '../stores/AppStore'
import { useUploadStore } from '../stores/UploadStore'
import geti18n from "../i18n";

export default {
    install: (app) => {
        const appStore = useAppStore()
        const uploadStore = useUploadStore()
        app.config.globalProperties.$readableDuration = (ms) => {
            const seconds = Math.floor((ms / 1000) % 60)
            const minutes = Math.floor((ms / (60 * 1000)) % 60)
            const hours = Math.floor((ms / (3600 * 1000)) % 3600)
            return `${hours == 0 ? '' : hours + ":"}${hours == 0 && minutes < 10 ? minutes : minutes < 10 ? '0' + minutes : minutes}:${seconds < 10 ? '0' + seconds : seconds}`
        }
        app.config.globalProperties.$getAppAsset = (name, width) => {
            if (width < 0 || width === undefined) width = 32
            return `${API_URL}icon?id=${name}&width=${width}`
        }

        app.config.globalProperties.$getImageUrl = (media, mediaType) => {
            if (mediaType == 'network' && media.artworkURL != "") return media.artworkURL
            return `${API_URL}artwork?artwork=${media.artworkURL}&id=${media.id}&type=${mediaType}`
        }

        app.config.globalProperties.$download = (media, mediaType, directDownload) => {
            if (directDownload) {
                window.location.href =`${API_URL}prepare-download?id=${media.id}&type=${mediaType}`
            } else {
                appStore.warning = { type: "message", message: geti18n().global.t("PREPARING_DOWNLOAD") }
                axios.get(`${API_URL}prepare-download?id=${media.id}&type=${mediaType}`).then((response) => {
                    appStore.warning = undefined
                    window.location.href = `${API_URL}download?file=${response.data}`
                });
            }
        }

        app.config.globalProperties.$play = (media, mediaType, append, asAudio) => {
            axios.get(`${API_URL}play`, {
                params: {
                    id: media.id,
                    append: append,
                    audio: asAudio,
                    type: mediaType,
                    path: media.path
                }
            })
                .catch(function (error) {
                    if (error.response.status != 200) {
                        appStore.warning = { type: "warning", message: error.response.data }
                    }
                })
        }
        app.config.globalProperties.$resumePlayback = (isAudio) => {
            axios.get(`${API_URL}resume-playback`, {
                params: {
                    audio: isAudio
                }
            })
        }
        app.config.globalProperties.$upload = (file) => {
            const onUploadProgress = (progressEvent) => {
                const { loaded, total } = progressEvent;
                let percent = Math.floor((loaded * 100) / total);
                uploadStore.changeProgress(file, percent)
            };
            var formData = new FormData();
            formData.append("media", file);
            formData.append("filename", file.name)
            uploadStore.changeFileStatus(file, 'uploading')
            axios.post(`${API_CONFIG.UPLOAD_MEDIA}`, formData, {
                headers: {
                    'Content-Type': 'multipart/form-data'
                },
                onUploadProgress
            }).catch(function () {
                uploadStore.changeFileStatus(file, 'error')

            }).then(() => {
                uploadStore.changeFileStatus(file, 'uploaded')
            })
        }
    }
}