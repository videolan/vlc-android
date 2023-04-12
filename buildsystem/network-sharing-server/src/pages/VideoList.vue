<template>
    <div class="">
        <div v-if="loaded" class="container">
            <p>Videos</p>
            <div v-if="this.videos.length !== 0" class="row gx-3 gy-3">
                <div class="col-md-3 col-sm-4 col-xs-6" v-for="video in videos" :key="video.id">
                    <div class="ratio ratio-16x9 video-img-container">
                        <img :src="getImageUrl(video)" class="video-img-top">
                        <div v-on:click="play(video)" class="overlay">
                            <button class="btn btn-lg overlay-play text-white" type="button">
                                <span class="material-symbols-outlined">play_circle</span>
                            </button>
                        </div>
                        <span class="resolution">{{ video.resolution }}</span>
                    </div>
                    <div class="d-flex">

                        <div class="card-body video-text flex1">
                            <h6 class="card-title text-truncate">{{ video.title }}</h6>
                            <p class="card-text text-truncate">{{ msecToTime(video.length) }}</p>
                            
                        </div>
                        <div class="dropdown dropstart overlay-more-container">
                                <button class="btn btn-lg nav-button overlay-more " type="button"
                                    id="dropdownMenuButton1" data-bs-toggle="dropdown" aria-expanded="false">
                                    <span class="material-symbols-outlined">more_vert</span>
                                </button>
                                <ul class="dropdown-menu video-more" aria-labelledby="dropdownMenuButton1">
                                    <li> <span v-on:click="play(video, false, false)" class="dropdown-item">Play</span> </li>
                                    <li> <span v-on:click="play(video, true, false)" class="dropdown-item">Append</span> </li>
                                    <li> <span v-on:click="play(video, false, true)" class="dropdown-item">Play as audio</span> </li>
                                </ul>
                            </div>
                    </div>
                </div>
            </div>
            <div v-else>
                <EmptyView />
            </div>
        </div>
        <div v-else class="d-flex vertical center-page spinner" style="margin: 0 auto">
        </div>
    </div>
</template>

<script>

import axios from 'axios'
import { API_URL } from '../config.js'
import { playerStore } from '../stores/PlayerStore'
import { mapStores } from 'pinia'

export default {
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
            axios.get(API_URL + "video-list").then((response) => {
                this.loaded = true;
                this.videos = response.data
            });
        },
        play(media, append, asAudio) {
            let component = this
            axios.get(API_URL + "play?id=" + media.id + "&append="+append + "&audio="+asAudio)
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
.video-img-top {
    width: 100%;
    height: 100%;
    object-fit: cover;
    border-radius: 4px;
}

.video-img-container:hover .overlay {
    visibility: visible;
    opacity: 1;
}

.overlay {
    visibility: hidden;
    opacity: 0;
    position: absolute;
    top: 0;
    bottom: 0;
    left: 0;
    right: 0;
    background: rgba(0, 0, 0, 0.3);
    transition: opacity 0.2s ease, visibility 0.2s;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
}

.overlay-play>span.material-symbols-outlined {
    font-size: 42px;
    vertical-align: middle;
}

.video-text {
    margin-top: 0.5em;
    min-width: 0;
}

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

.overlay-more {
    position: absolute;
    bottom: 0px;
    right: 0px;
}
.overlay-more-container {
    flex: 0 0 48px;
}

.video-more li {
    cursor: pointer;
}

</style>
