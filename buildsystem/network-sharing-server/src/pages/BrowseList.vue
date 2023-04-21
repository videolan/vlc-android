<template>
    <div v-if="loaded && this.favorites.length !== 0" class="container">
        <h5 class="media-content text-primary">Favorites</h5>
        <div v-if="this.playerStore.displayType[this.$route.name]" class="row gx-3 gy-3 ">
            <table class="table table-hover media-list">
                <tbody>
                    <tr v-for="favorite in favorites" :key="favorite.id">
                        <MediaListItem :media="favorite" :mediaType="'folder'"/>
                    </tr>
                </tbody>
            </table>
        </div>
        <div v-else class="row gx-3 gy-3 media-content">
            <div class="col-md-3 col-lg-1 col-sm-4 col-xs-6" v-for="favorite in favorites" :key="favorite.id">
                <MediaCardItem :media="favorite" :mediaType="'folder'"/>
            </div>
        </div>
    </div>
    <div v-else-if="loaded">
        <EmptyView :message="$t('NO_MEDIA')" />
    </div>
    
    <div v-if="loaded && this.storages.length !== 0" class="container">
        <h5 class="media-content text-primary">Storages</h5>
        <div v-if="this.playerStore.displayType[this.$route.name]" class="row gx-3 gy-3 media-content">
            <table class="table table-hover media-list">
                <tbody>
                    <tr v-for="storage in storages" :key="storage.id">
                        <MediaListItem :media="storage" :mediaType="'folder'"/>
                    </tr>
                </tbody>
            </table>
        </div>
        <div v-else class="row gx-3 gy-3 media-content">
            <div class="col-md-3 col-lg-1 col-sm-4 col-xs-6" v-for="storage in storages" :key="storage.id">
                <MediaCardItem :media="storage" :mediaType="'folder'"/>
            </div>
        </div>
    </div>
    <div v-else-if="loaded">
        <EmptyView :message="$t('NO_MEDIA')" />
    </div>
    
    <div v-if="loaded && this.networkEntries.length !== 0" class="container">
        <h5 class="media-content text-primary">Local network</h5>
        <div v-if="this.playerStore.displayType[this.$route.name]" class="row gx-3 gy-3 media-content">
            <table class="table table-hover media-list">
                <tbody>
                    <tr v-for="network in networkEntries" :key="network.id">
                        <MediaListItem :media="network" :mediaType="'network'"/>
                    </tr>
                </tbody>
            </table>
        </div>
        <div v-else class="row gx-3 gy-3 media-content">
            <div class="col-md-3 col-lg-1 col-sm-4 col-xs-6" v-for="network in networkEntries" :key="network.id">
                <MediaCardItem :media="network" :mediaType="'network'"/>
            </div>
        </div>
    </div>
    <div v-else-if="loaded">
        <EmptyView :message="$t('NO_MEDIA')" />
    </div>
</template>

<script>

import { playerStore } from '../stores/PlayerStore'
import { mapStores } from 'pinia'
import axios from 'axios'
import { API_URL } from '../config.js'
import MediaCardItem from '../components/MediaCardItem.vue'
import MediaListItem from '../components/MediaListItem.vue'
import EmptyView from '../components/EmptyView.vue'

export default {
    computed: {
        ...mapStores(playerStore)
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
            loaded: false,
        }
    },
    methods: {
        fetchFavorites() {
            let component = this
            component.playerStore.loading = true
            axios.get(API_URL + "favorite-list").then((response) => {
                this.loaded = true;
                this.favorites = response.data
                component.playerStore.loading = false
            });
        },
        fetchStorages() {
            let component = this
            component.playerStore.loading = true
            axios.get(API_URL + "storage-list").then((response) => {
                this.loaded = true;
                this.storages = response.data
                component.playerStore.loading = false
            });
        },
        fetchNetwork() {
            let component = this
            component.playerStore.loading = true
            axios.get(API_URL + "network-list").then((response) => {
                this.loaded = true;
                this.networkEntries = response.data
                component.playerStore.loading = false
            });
        },
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
