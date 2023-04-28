<template>
    <div class="container mt-3">
        <div class="d-flex align-items-center justify-content-center">
            <input type="text" id="searchInput" class="form-control search-input" ref="searchText"
                :placeholder="$t('SEARCH_HINT')" aria-labelledby="passwordHelpBlock" @keydown.enter="onPressEnter">
            <ImageButton type="search" data-bs-toggle="tooltip" data-bs-placement="bottom" :title="$t('SEARCH')"
                v-on:click="loadResults()" />
        </div>
        <div v-if="!emptyResults()">
            <!-- results -->
            <div v-if="this.results.videos.length !== 0" class="container">
                <h5 class="media-content text-primary" v-t="'VIDEO'"></h5>
                <div class="row gx-3 gy-3 ">
                    <table class="table table-hover media-list">
                        <tbody>
                            <tr v-for="video in results.videos" :key="video.id" class="media-img-list-tr">
                                <MediaListItem :media="video" :mediaType="'video'" />
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
            <div v-if="this.results.tracks.length !== 0" class="container">
                <h5 class="media-content text-primary" v-t="'TRACKS'"></h5>
                <div class="row gx-3 gy-3 ">
                    <table class="table table-hover media-list">
                        <tbody>
                            <tr v-for="track in results.tracks" :key="track.id" class="media-img-list-tr">
                                <MediaListItem :media="track" :mediaType="'track'" />
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
            <div v-if="this.results.albums.length !== 0" class="container">
                <h5 class="media-content text-primary" v-t="'ALBUMS'"></h5>
                <div class="row gx-3 gy-3 ">
                    <table class="table table-hover media-list">
                        <tbody>
                            <tr v-for="album in results.albums" :key="album.id" class="media-img-list-tr">
                                <MediaListItem :media="album" :mediaType="'album'" />
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
            <div v-if="this.results.artists.length !== 0" class="container">
                <h5 class="media-content text-primary" v-t="'ARTISTS'"></h5>
                <div class="row gx-3 gy-3 ">
                    <table class="table table-hover media-list">
                        <tbody>
                            <tr v-for="artist in results.artists" :key="artist.id" class="media-img-list-tr">
                                <MediaListItem :media="artist" :mediaType="'artist'" />
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
            <div v-if="this.results.genres.length !== 0" class="container">
                <h5 class="media-content text-primary" v-t="'GENRES'"></h5>
                <div class="row gx-3 gy-3 ">
                    <table class="table table-hover media-list">
                        <tbody>
                            <tr v-for="genre in results.genres" :key="genre.id" class="media-img-list-tr">
                                <MediaListItem :media="genre" :mediaType="'genre'" />
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
            <div v-if="this.results.playlists.length !== 0" class="container">
                <h5 class="media-content text-primary" v-t="'PLAYLISTS'"></h5>
                <div class="row gx-3 gy-3 ">
                    <table class="table table-hover media-list">
                        <tbody>
                            <tr v-for="genre in results.playlists" :key="genre.id" class="media-img-list-tr">
                                <MediaListItem :media="genre" :mediaType="'playlist'" />
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>

        </div>
        <div v-else>
            <EmptyView :message="$t('SEARCH_NO_RESULT')" />
        </div>
    </div>
</template>

<script>

import { useAppStore } from '../stores/AppStore'
import { mapStores } from 'pinia'
import axios from 'axios'
import { API_URL } from '../config.js'
import MediaListItem from '../components/MediaListItem.vue'
import EmptyView from '../components/EmptyView.vue'
import ImageButton from '../components/ImageButton.vue'
import { reactive } from 'vue'

export default {
    computed: {
        ...mapStores(useAppStore)
    },
    components: {
        MediaListItem,
        EmptyView,
        ImageButton,
    },
    data() {
        return {
            results: reactive({
                albums: [],
                artists: [],
                genres: [],
                playlists: [],
                videos: [],
                tracks: [],
            }),
            loaded: false,
        }
    },
    methods: {
        onPressEnter() {
            this.loadResults()
        },
        emptyResults() {
            return (this.results.albums.length == 0 &&
                this.results.artists.length == 0 &&
                this.results.genres.length == 0 &&
                this.results.playlists.length == 0 &&
                this.results.videos.length == 0 &&
                this.results.tracks.length == 0)
        },
        loadResults() {
            let component = this
            component.appStore.loading = true
            axios.get(API_URL + "search",
                {
                    params: {
                        search: this.$refs.searchText.value
                    }
                }).then((response) => {
                    this.loaded = true;
                    this.results = response.data
                    component.appStore.loading = false
                });
        },
    },
}
</script>

<style lang='scss'>
@import '../scss/colors.scss';

.search-input {
    max-width: 550px;
}
</style>
