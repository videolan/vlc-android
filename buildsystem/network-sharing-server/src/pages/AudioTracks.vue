<template>
    <div v-if="loaded" class="container">
        <div v-if="this.tracks.length !== 0" class="row gx-3 gy-3 media-content">
            <div class="col-md-3 col-lg-2 col-sm-4 col-xs-6" v-for="track in tracks" :key="track.id">
                <div class="ratio ratio-1x1 media-img-container audio-img-container">
                    <img :src="getImageUrl(track)" class="media-img-top">
                    <div v-on:click="play(track)" class="media-overlay">
                        <button class="btn btn-lg overlay-play text-white" type="button">
                            <span class="material-symbols-outlined">play_circle</span>
                        </button>
                    </div>
                </div>
                <div class="d-flex">

                    <div class="card-body media-text flex1">
                        <h6 class="card-title text-truncate">{{ track.title }}</h6>
                        <p class="card-text text-truncate">{{ track.artist }}</p>

                    </div>
                    <div class="dropdown dropstart overlay-more-container">
                        <button class="btn btn-lg nav-button overlay-more " type="button" id="dropdownMenuButton1"
                            data-bs-toggle="dropdown" aria-expanded="false">
                            <span class="material-symbols-outlined">more_vert</span>
                        </button>
                        <ul class="dropdown-menu media-more" aria-labelledby="dropdownMenuButton1">
                            <li> <span v-on:click="play(track, false, false)" class="dropdown-item">Play</span> </li>
                            <li> <span v-on:click="play(track, true, false)" class="dropdown-item">Append</span> </li>
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

import { playerStore } from '../stores/PlayerStore'
import { mapStores } from 'pinia'
import axios from 'axios'
import { API_URL } from '../config.js'

export default {
    computed: {
        ...mapStores(playerStore)
    },
    mounted: function () {
        this.playerStore.currentTab = "tracks";
    },
    unmounted: function () {
        this.playerStore.currentTab = "";
    },
    data() {
        return {
            tracks: [],
            loaded: false,
        }
    },
    methods: {
        getImageUrl(media) {
            return API_URL + "/artwork?artwork=" + media.artworkURL + "&id=" + media.id + "&type=track"
        },
        fetchTracks() {
            let component = this
            component.playerStore.loading = true
            axios.get(API_URL + "track-list").then((response) => {
                this.loaded = true;
                this.tracks = response.data
                component.playerStore.loading = false
            });
        },
        play(media, append, asAudio) {
            let component = this
            axios.get(API_URL + "play?id=" + media.id + "&append=" + append + "&audio=" + asAudio + "&type=track")
                .catch(function (error) {
                    console.log(error.toJSON());
                    if (error.response.status != 200) {
                        component.playerStore.warning = { type: "warning", message: error.response.data }
                    }
                })
        }
    },
    created: function () {
        this.fetchTracks();
    }
}
</script>

<style lang='scss'>
@import '../scss/colors.scss';

</style>
