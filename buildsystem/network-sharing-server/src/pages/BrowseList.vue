<template>
    <div class="container" @ml-refresh-needed="fetchStreams">
        <h5 class="media-content text-primary">Streams</h5>
        <div v-if="!favoritesLoaded" class="spinner-border text-primary mt-3" role="status">
            <span class="visually-hidden">Loading...</span>
        </div>
        <template v-else>
            <div v-if="this.appStore.displayType[this.$route.name]" class="row gx-3 gy-3 media-list">
                <MediaItem :isCard="false" :media="newStream" :mediaType="'new-stream'" :hideOverflow="true" />
                <template v-for="stream in streams" :key="stream.id">
                    <MediaItem :isCard="false" :media="stream" :mediaType="'stream'" :hideOverflow="true" />
                </template>
            </div>
            <div v-else class="row gx-3 gy-3 media-content">
                <div class="col-md-3 col-lg-2 col-sm-4 col-6">
                    <MediaItem :isCard="true" :media="newStream" :mediaType="'new-stream'" :hideOverflow="true" />
                </div>
                <div class="col-md-3 col-lg-2 col-sm-4 col-6" v-for="stream in streams" :key="stream.id">
                    <MediaItem :isCard="true" :media="stream" :mediaType="'stream'" :hideOverflow="true" />
                </div>
            </div>
        </template>

    </div>

    <div class="container">
        <h5 class="media-content text-primary">Favorites</h5>
        <div v-if="!favoritesLoaded" class="spinner-border text-primary mt-3" role="status">
            <span class="visually-hidden">Loading...</span>
        </div>
        <div v-else-if="this.favorites.length == 0">
            <EmptyView :message="getFileEmptyText()" />
        </div>
        <template v-else>
            <div v-if="this.appStore.displayType[this.$route.name]" class="row gx-3 gy-3 media-list">
                <template v-for="favorite in favorites" :key="favorite.id">
                    <MediaItem :isCard="false" :media="favorite" :mediaType="'folder'" :hideOverflow="true" />
                </template>
            </div>
            <div v-else class="row gx-3 gy-3 media-content">
                <div class="col-md-3 col-lg-2 col-sm-4 col-6" v-for="favorite in favorites" :key="favorite.id">
                    <MediaItem :isCard="true" :media="favorite" :mediaType="'folder'" :hideOverflow="true" />
                </div>
            </div>
        </template>

    </div>

    <div class="container">
        <h5 class="media-content text-primary">Storages</h5>
        <div v-if="!storagesLoaded" class="spinner-border text-primary mt-3" role="status">
            <span class="visually-hidden">Loading...</span>
        </div>
        <div v-else-if="this.storages.length == 0">
            <EmptyView :message="getFileEmptyText()" />
        </div>
        <template v-else>
            <div v-if="this.appStore.displayType[this.$route.name]" class="row gx-3 gy-3 media-list">
                <template v-for="storage in storages" :key="storage.id">
                    <MediaItem :isCard="false" :media="storage" :mediaType="'folder'" :hideOverflow="true" />
                </template>
            </div>
            <div v-else class="row gx-3 gy-3 media-content">
                <div class="col-md-3 col-lg-2 col-sm-4 col-6" v-for="storage in storages" :key="storage.id">
                    <MediaItem :isCard="true" :media="storage" :mediaType="'folder'" :hideOverflow="true" />
                </div>
            </div>
        </template>


    </div>

    <div class="container">
        <h5 class="media-content text-primary">Local network</h5>
        <div v-if="!networkLoaded" class="spinner-border text-primary mt-3" role="status">
            <span class="visually-hidden">Loading...</span>
        </div>
        <div v-else-if="this.networkEntries.length == 0">
            <EmptyView :message="getNetworkEmptyText()" />
        </div>
        <template v-else>
            <div v-if="this.appStore.displayType[this.$route.name]" class="row gx-3 gy-3 media-list">
                <template v-for="network in networkEntries" :key="network.id">
                    <MediaItem :isCard="false" :media="network" :mediaType="'network'" :hideOverflow="true" />
                </template>
            </div>
            <div v-else class="row gx-3 gy-3 media-content">
                <div class="col-md-3 col-lg-2 col-sm-4 col-6" v-for="network in networkEntries" :key="network.id">
                    <MediaItem :isCard="true" :media="network" :mediaType="'network'" :hideOverflow="true" />
                </div>
            </div>
        </template>


    </div>
</template>

<script>

import { useAppStore } from '../stores/AppStore'
import { useBrowserStore } from '../stores/BrowserStore'
import { mapStores } from 'pinia'
import http from '../plugins/auth'
import { vlcApi } from '../plugins/api.js'
import MediaItem from '../components/MediaItem.vue'
import EmptyView from '../components/EmptyView.vue'

export default {
    computed: {
        ...mapStores(useAppStore, ['needRefresh'], useBrowserStore)
    },
    components: {
        MediaItem,
        EmptyView,
    },
    data() {
        return {
            streams: [],
            favorites: [],
            storages: [],
            networkEntries: [],
            streamsLoaded: false,
            favoritesLoaded: false,
            storagesLoaded: false,
            networkLoaded: false,
            forbidden: false,
            networkForbidden: false,
            newStream: {
                "artist": " ",
                "artworkURL": "",
                "id": -1,
                "isFolder": true,
                "length": 0,
                "path": "",
                "playing": false,
                "resolution": "",
                "title": this.$t('NEW_STREAM')
            }
        }
    },
    watch: {
        needRefresh() {
            this.fetchStreams()
        },
    },
    methods: {
        fetchStreams() {
            let component = this
            http.get(vlcApi.streamList)
                .catch(function (error) {
                    if (error.response !== undefined && error.response.status == 403) {
                        component.forbidden = true;
                    }
                })
                .then((response) => {
                    this.streamsLoaded = true;
                    if (response) {
                        component.forbidden = false;
                        this.streams = response.data
                    }
                });
        },
        fetchFavorites() {
            let component = this
            http.get(vlcApi.favoriteList)
                .catch(function (error) {
                    if (error.response !== undefined && error.response.status == 403) {
                        component.forbidden = true;
                    }
                })
                .then((response) => {
                    this.favoritesLoaded = true;
                    if (response) {
                        component.forbidden = false;
                        this.favorites = response.data
                    }
                });
        },
        fetchStorages() {
            let component = this
            http.get(vlcApi.storageList)
                .catch(function (error) {
                    if (error.response !== undefined && error.response.status == 403) {
                        component.forbidden = true;
                    }
                })
                .then((response) => {
                    this.storagesLoaded = true;
                    if (response) {
                        component.forbidden = false;
                        this.storages = response.data
                    }
                });
        },
        fetchNetwork() {
            let component = this
            http.get(vlcApi.networkList)
                .catch(function (error) {
                    if (error.response !== undefined && error.response.status == 403) {
                        component.networkForbidden = true;
                    }
                })
                .then((response) => {
                    this.networkLoaded = true;
                    if (response) {
                        component.networkForbidden = false;
                        this.networkEntries = response.data
                    }
                });
        },
        getFileEmptyText() {
            if (this.forbidden) return this.$t('FORBIDDEN')
            return this.$t('DIRECTORY_EMPTY')
        },
        getNetworkEmptyText() {
            if (this.networkForbidden) return this.$t('FORBIDDEN')
            return this.$t('DIRECTORY_EMPTY')
        }
    },
    created: function () {
        this.fetchStreams();
        this.fetchFavorites();
        this.fetchStorages();
        this.fetchNetwork();
    },
    mounted: function () {
        this.appStore.$subscribe((mutation, state) => {
            if (state.needRefresh) {
                this.$log.log(`A stream has changed!! Reloading with: ${JSON.stringify(state)}`)
                this.fetchStreams();
                this.appStore.needRefresh = false
            }
        })
        this.browserStore.$subscribe((mutation, state) => {
            state.descriptions.forEach(description => {
                this.storages.forEach(item => {
                    if (item.path == description.path) {
                        item.artist = description.description
                    }
                })
            })
        })
    },
}
</script>

<style lang='scss'>
@import '../scss/colors.scss';
</style>
