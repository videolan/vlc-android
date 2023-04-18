import { API_URL } from '../config.js'
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
    }
}