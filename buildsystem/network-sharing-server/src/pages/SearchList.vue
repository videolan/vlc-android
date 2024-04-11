<template>
    <div class="container mt-3">
        <div class="d-flex align-items-center justify-content-center">
            <input type="text" id="searchInput" class="search-input" ref="searchText" :placeholder="$t('SEARCH_HINT')"
                aria-labelledby="passwordHelpBlock" @keydown.enter="onPressEnter">
            <ImageButton type="search" data-bs-toggle="tooltip" data-bs-placement="bottom" :title="$t('SEARCH')"
                v-on:click="loadResults()" />
        </div>
        <div v-if="!this.forbidden && !emptyResults()">
            <!-- results -->
            <div v-if="this.results.videos.length !== 0" class="container">
                <h5 class="media-content text-primary" v-t="'VIDEO'"></h5>
                <div class="row gx-3 gy-3 media-list">
                    <template v-for="video in results.videos" :key="video.id">
                        <MediaItem :isCard="false" :media="video" :mediaType="'video'" />
                    </template>
                </div>
            </div>
            <div v-if="this.results.tracks.length !== 0" class="container">
                <h5 class="media-content text-primary" v-t="'TRACKS'"></h5>
                <div class="row gx-3 gy-3 media-list">
                    <template v-for="video in results.videos" :key="video.id">
                        <MediaItem :isCard="false" :media="video" :mediaType="'video'" />
                    </template>
                </div>
            </div>
            <div v-if="this.results.albums.length !== 0" class="container">
                <h5 class="media-content text-primary" v-t="'ALBUMS'"></h5>
                <div class="row gx-3 gy-3 media-list">
                    <template v-for="album in results.albums" :key="album.id">
                        <MediaItem :isCard="false" :media="album" :mediaType="'album'" />
                    </template>
                </div>
            </div>
            <div v-if="this.results.artists.length !== 0" class="container">
                <h5 class="media-content text-primary" v-t="'ARTISTS'"></h5>
                <div class="row gx-3 gy-3 media-list">
                    <template v-for="artist in results.artists" :key="artist.id">
                        <MediaItem :isCard="false" :media="artist" :mediaType="'artist'" />
                    </template>
                </div>
            </div>
            <div v-if="this.results.genres.length !== 0" class="container">
                <h5 class="media-content text-primary" v-t="'GENRES'"></h5>
                <div class="row gx-3 gy-3 media-list">
                    <template v-for="genre in results.genres" :key="genre.id">
                        <MediaItem :isCard="false" :media="genre" :mediaType="'genre'" />
                    </template>
                </div>
            </div>
            <div v-if="this.results.playlists.length !== 0" class="container">
                <h5 class="media-content text-primary" v-t="'PLAYLISTS'"></h5>
                <div class="row gx-3 gy-3 media-list">
                    <template v-for="genre in results.playlists" :key="genre.id">
                        <MediaItem :isCard="false" :media="genre" :mediaType="'playlist'" />
                    </template>
                </div>
            </div>

        </div>
        <div v-else-if="loaded">
            <EmptyView :message="getEmptyText()" />
        </div>
    </div>
</template>

<script>

import { useAppStore } from '../stores/AppStore'
import { mapStores } from 'pinia'
import http from '../plugins/auth'
import { vlcApi } from '../plugins/api.js'
import MediaItem from '../components/MediaItem.vue'
import EmptyView from '../components/EmptyView.vue'
import ImageButton from '../components/ImageButton.vue'
import { reactive } from 'vue'

export default {
    computed: {
        ...mapStores(useAppStore)
    },
    components: {
        MediaItem,
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
            forbidden: false,
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
            http.get(vlcApi.search(this.$refs.searchText.value))
                .catch(function (error) {
                    if (error.response !== undefined && error.response.status == 403) {
                        component.forbidden = true;
                    }
                })
                .then((response) => {
                    this.loaded = true;
                    if (response) {
                        component.forbidden = false;
                        this.results = response.data
                    }
                    component.appStore.loading = false
                });
        },
        getEmptyText() {
            if (this.forbidden) return this.$t('FORBIDDEN')
            return this.$t('SEARCH_NO_RESULT')
        }
    },
    mounted: function () {
        this.$nextTick(() => {
            this.$refs.searchText.focus()
        });
    },
}
</script>

<style lang='scss'>
@import '../scss/colors.scss';

.search-input {
    max-width: 550px;
    box-sizing: border-box;
    border: 2px solid $dark-background;
    border-radius: 8px;
    -webkit-transition: 0.5s;
    transition: 0.5s;
    outline: none;
    display: block;
    width: 100%;
    padding: 0.375rem 0.75rem;
    font-size: 1rem;
    font-weight: 400;
    line-height: 1.5;
}

.search-input:focus {
    border: 2px solid $primary-color;
}
</style>
