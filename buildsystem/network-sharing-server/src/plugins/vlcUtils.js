import { API_URL, API_CONFIG } from '../config.js'
import axios from 'axios'
import { playerStore } from '../stores/PlayerStore'

export default {
    install: (app) => {
        const store = playerStore()
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

        app.config.globalProperties.$download = (media) => {
            window.open(`${API_URL}download?id=${media.id}`, '_blank', 'noreferrer');
        }

        app.config.globalProperties.$play = (media, mediaType, append, asAudio) => {
            axios.get(`${API_URL}play`, {
                params: {
                    id: media.id,
                    append: append,
                    audio: asAudio,
                    type: mediaType
                }
            })
                .catch(function (error) {
                    if (error.response.status != 200) {
                        store.warning = { type: "warning", message: error.response.data }
                    }
                })
        }
        app.config.globalProperties.$upload = (file) => {
            const onUploadProgress = (progressEvent) => {
                const { loaded, total } = progressEvent;
                let percent = Math.floor((loaded * 100) / total);
                store.changeProgress(file, percent)
              };
            var formData = new FormData();
            formData.append("media", file);
            formData.append("filename", file.name)
            store.changeFileStatus(file, 'uploading')
            axios.post(`${API_CONFIG.UPLOAD_MEDIA}`, formData, {
              headers: {
                'Content-Type': 'multipart/form-data'
              },
              onUploadProgress
            }).catch(function () {
                store.changeFileStatus(file, 'error')

            }).then(() => {
                store.changeFileStatus(file, 'uploaded')
            })
        }
    }
}