<template>
    <div v-if="loaded" class="container">
        <div v-if="this.videos.length !== 0" class="row gx-3 gy-3 media-content">
            <div class="col-md-3 col-sm-4 col-xs-6" v-for="video in videos" :key="video.id">
                <div class="ratio ratio-16x9 media-img-container">
                    <img :src="getImageUrl(video)" class="media-img-top">
                    <div v-on:click="play(video)" class="media-overlay">
                        <button class="btn btn-lg overlay-play text-white" type="button">
                            <span class="material-symbols-outlined">play_circle</span>
                        </button>
                    </div>
                    <span class="resolution">{{ video.resolution }}</span>
                </div>
                <div class="d-flex">

                    <div class="card-body media-text flex1">
                        <h6 class="card-title text-truncate">{{ video.title }}</h6>
                        <p class="card-text text-truncate">{{ msecToTime(video.length) }}</p>

                    </div>
                    <div class="dropdown dropstart overlay-more-container">
                        <button class="btn btn-lg nav-button overlay-more " type="button" id="dropdownMenuButton1"
                            data-bs-toggle="dropdown" aria-expanded="false">
                            <span class="material-symbols-outlined">more_vert</span>
                        </button>
                        <ul class="dropdown-menu media-more" aria-labelledby="dropdownMenuButton1">
                            <li> <span v-on:click="play(video, false, false)" class="dropdown-item">Play</span> </li>
                            <li> <span v-on:click="play(video, true, false)" class="dropdown-item">Append</span> </li>
                            <li> <span v-on:click="play(video, false, true)" class="dropdown-item">Play as audio</span>
                            </li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>
        <div v-else>
            <EmptyView />
        </div>
    </div>
</template>

<script>

import axios from 'axios'
import { API_URL } from '../config.js'
import { playerStore } from '../stores/PlayerStore'
import { mapStores } from 'pinia'
import EmptyView from '../components/EmptyView.vue'

export default {
    components: {
        EmptyView,
    },
    computed: {
        ...mapStores(playerStore)
    },
    data() {
        return {
            videos: [],
            loaded: false,
        }
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
            return API_URL + "/artwork?artwork=" + media.artworkURL + "&id=" + media.id + "&type=video"
        },
        fetchVideos() {
            let component = this
            component.playerStore.loading = true
            axios.get(API_URL + "video-list").then((response) => {
                this.loaded = true;
                this.videos = response.data
                component.playerStore.loading = false
            });
        },
        play(media, append, asAudio) {
            let component = this
            axios.get(API_URL + "play?id=" + media.id + "&append=" + append + "&audio=" + asAudio)
                .catch(function (error) {
                    console.log(error.toJSON());
                    if (error.response.status != 200) {
                        component.playerStore.warning = { type: "warning", message: error.response.data }
                    }
                })
        }
    },
    created: function () {
        this.fetchVideos();
    }
}
</script>

<style>

.ratio>.resolution {
    position: absolute;
    top: 8px;
    left: 8px;
    width: auto;
    height: auto;
    color: #fff;
    background: rgba(0, 0, 0, 0.6);
    padding: 4px 6px;
    border-radius: 2px;
    font-size: 0.6rem;
}


</style>
