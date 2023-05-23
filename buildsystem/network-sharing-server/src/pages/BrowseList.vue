<template>
    <div class="container">
        <h5 class="media-content text-primary">Favorites</h5>
        <div v-if="!favoritesLoaded" class="spinner-border text-primary mt-3" role="status">
            <span class="visually-hidden">Loading...</span>
        </div>
        <div v-else-if="this.favorites.length == 0">
            <EmptyView :message="getFileEmptyText()" />
        </div>
        <template v-else>
            <div v-if="this.appStore.displayType[this.$route.name]" class="row gx-3 gy-3 ">
                <table class="table table-hover media-list">
                    <tbody>
                        <tr v-for="favorite in favorites" :key="favorite.id" class="media-img-list-tr">
                            <MediaListItem :media="favorite" :mediaType="'folder'" :hideOverflow="true" />
                        </tr>
                    </tbody>
                </table>
            </div>
            <div v-else class="row gx-3 gy-3 media-content">
                <div class="col-md-3 col-lg-2 col-sm-4 col-xs-6" v-for="favorite in favorites" :key="favorite.id">
                    <MediaCardItem :media="favorite" :mediaType="'folder'" :hideOverflow="true" />
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
            <div v-if="this.appStore.displayType[this.$route.name]" class="row gx-3 gy-3 media-content">
                <table class="table table-hover media-list">
                    <tbody>
                        <tr v-for="storage in storages" :key="storage.id">
                            <MediaListItem :media="storage" :mediaType="'folder'" :hideOverflow="true" />
                        </tr>
                    </tbody>
                </table>
            </div>
            <div v-else class="row gx-3 gy-3 media-content">
                <div class="col-md-3 col-lg-2 col-sm-4 col-xs-6" v-for="storage in storages" :key="storage.id">
                    <MediaCardItem :media="storage" :mediaType="'folder'" :hideOverflow="true" />
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
            <div v-if="this.appStore.displayType[this.$route.name]" class="row gx-3 gy-3 media-content">
                <table class="table table-hover media-list">
                    <tbody>
                        <tr v-for="network in networkEntries" :key="network.id">
                            <MediaListItem :media="network" :mediaType="'network'" :hideOverflow="true" />
                        </tr>
                    </tbody>
                </table>
            </div>
            <div v-else class="row gx-3 gy-3 media-content">
                <div class="col-md-3 col-lg-2 col-sm-4 col-xs-6" v-for="network in networkEntries" :key="network.id">
                    <MediaCardItem :media="network" :mediaType="'network'" :hideOverflow="true" />
                </div>
            </div>
        </template>


    </div>
</template>

<script>

import { useAppStore } from '../stores/AppStore'
import { mapStores } from 'pinia'
import axios from 'axios'
import { API_URL } from '../config.js'
import MediaCardItem from '../components/MediaCardItem.vue'
import MediaListItem from '../components/MediaListItem.vue'
import EmptyView from '../components/EmptyView.vue'

export default {
    computed: {
        ...mapStores(useAppStore)
    },
    components: {
        MediaCardItem,
        MediaListItem,
        EmptyView,
    },
    data() {
        return {
            favorites: [],
            storages: [],
            networkEntries: [],
            favoritesLoaded: false,
            storagesLoaded: false,
            networkLoaded: false,
            forbidden: false,
            networkForbidden: false,
        }
    },
    methods: {
        fetchFavorites() {
            let component = this
            axios.get(API_URL + "favorite-list")
                .catch(function (error) {
                    if (error.response.status == 403) {
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
            axios.get(API_URL + "storage-list")
                .catch(function (error) {
                    if (error.response.status == 403) {
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
            axios.get(API_URL + "network-list")
                .catch(function (error) {
                    if (error.response.status == 403) {
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
        this.fetchFavorites();
        this.fetchStorages();
        this.fetchNetwork();
    }
}
</script>

<style lang='scss'>
@import '../scss/colors.scss';
</style>
