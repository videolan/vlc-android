<template>
    <div class="ratio media-img-container audio-img-container"
        v-bind:class="(mediaType == 'video') ? 'ratio-16x9' : 'ratio-1x1'">
        <img :src="getImageUrl(media)" class="media-img-top">
        <div v-on:click="play(media)" class="media-overlay">
            <button class="btn btn-lg overlay-play text-white" type="button">
                <span class="material-symbols-outlined">play_circle</span>
            </button>
        </div>
        <span v-if="(mediaType == 'video')" class="resolution">{{ media.resolution }}</span>
    </div>
    <div class="d-flex">

        <div class="card-body media-text flex1">
            <h6 class="card-title text-truncate">{{ media.title }}</h6>
            <p class="card-text text-truncate">{{ (mediaType == 'video') ? msecToTime(media.length) : media.artist }}</p>

        </div>
        <div class="dropdown dropstart overlay-more-container">
            <button class="btn btn-lg nav-button overlay-more " type="button" id="dropdownMenuButton1"
                data-bs-toggle="dropdown" aria-expanded="false">
                <span class="material-symbols-outlined">more_vert</span>
            </button>
            <ul class="dropdown-menu media-more" aria-labelledby="dropdownMenuButton1">
                <li> <span v-on:click="play(media, false, false)" class="dropdown-item">Play</span> </li>
                <li> <span v-on:click="play(media, true, false)" class="dropdown-item">Append</span> </li>
                <li> <span v-if="(mediaType == 'video')" v-on:click="play(media, false, true)" class="dropdown-item">Play as audio</span> </li>
                <li v-if="downloadable"> <span v-on:click="download(media)" class="dropdown-item">Download</span> </li>

            </ul>
        </div>
    </div>
</template>

<script>
import { API_URL } from '../config.js'
import axios from 'axios'
import { playerStore } from '../stores/PlayerStore.js'
import { mapStores } from 'pinia'
export default {
    computed: {
        ...mapStores(playerStore),
    },
    props: {
        media: Object,
        downloadable: Boolean,
        mediaType: String
    },
    methods: {
        msecToTime(ms) {
            const seconds = Math.floor((ms / 1000) % 60)
            const minutes = Math.floor((ms / (60 * 1000)) % 60)
            const hours = Math.floor((ms / (3600 * 1000)) % 3600)
            return `${hours < 10 ? '0' + hours : hours}:${minutes < 10 ? '0' + minutes : minutes}:${seconds < 10 ? '0' + seconds : seconds
                }`
        },
        getImageUrl(media) {
            return API_URL + "/artwork?artwork=" + media.artworkURL + "&id=" + media.id + "&type=" + this.mediaType
        },
        download(media) {
            window.open(API_URL + "download?id=" + media.id, '_blank', 'noreferrer');
        },
        play(media, append, asAudio) {
            let component = this
            axios.get(API_URL + "play?id=" + media.id + "&append=" + append + "&audio=" + asAudio + "&type=" + this.mediaType)
                .catch(function (error) {
                    console.log(error.toJSON());
                    if (error.response.status != 200) {
                        component.playerStore.warning = { type: "warning", message: error.response.data }
                    }
                })
        }
    }
}
</script>