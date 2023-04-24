<template>
    <div v-if="loaded && this.artists.length !== 0" class="container">
        <div v-if="this.appStore.displayType[this.$route.name]" class="row gx-3 gy-3 media-content">
            <table class="table table-hover media-list">
                <tbody>
                    <tr v-for="artist in artists" :key="artist.id">
                        <MediaListItem :media="artist" :mediaType="'artist'"/>
                    </tr>
                </tbody>
            </table>


        </div>
        <div v-else class="row gx-3 gy-3 media-content">
            <div class="col-md-3 col-lg-2 col-sm-4 col-xs-6" v-for="artist in artists" :key="artist.id">
                <MediaCardItem :media="artist" :mediaType="'artist'"/>
            </div>
        </div>
    </div>
    <div v-else-if="loaded">
        <EmptyView :message="$t('NO_MEDIA')" />
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
        ...mapStores(useAppStore),
    },
    components: {
        MediaCardItem,
        MediaListItem,
        EmptyView,
    },
    data() {
        return {
            artists: [],
            loaded: false,
        }
    },
    methods: {
        fetchArtists() {
            let component = this
            component.appStore.loading = true
            axios.get(API_URL + "artist-list").then((response) => {
                this.loaded = true;
                this.artists = response.data
                component.appStore.loading = false
            });
        },
    },
    created: function () {
        this.fetchArtists();
    }
}
</script>

<style lang='scss'>
@import '../scss/colors.scss';
</style>
