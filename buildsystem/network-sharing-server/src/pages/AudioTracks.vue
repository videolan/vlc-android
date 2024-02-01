<template>
    <div v-if="loaded && this.tracks.length !== 0" class="container">
        <div v-if="this.appStore.displayType[this.$route.name]" class="row gx-3 gy-3 media-list">
            <template v-for="track in tracks" :key="track.id">
                <MediaItem :isCard="false" :media="track" :downloadable="true" :mediaType="'track'" />
            </template>
        </div>
        <div v-else class="row gx-3 gy-3 media-content">
            <div class="col-md-3 col-lg-2 col-sm-4 col-6" v-for="track in tracks" :key="track.id">
                <MediaItem :isCard="true" :media="track" :downloadable="true" :mediaType="'track'" />
            </div>
        </div>
    </div>
    <div v-else-if="loaded" class="empty-view-container">
        <EmptyView :message="getEmptyText()" />
    </div>
</template>

<script>

import { useAppStore } from '../stores/AppStore'
import { mapStores } from 'pinia'
import http from '../plugins/auth'
import { vlcApi } from '../plugins/api.js'
import MediaItem from '../components/MediaItem.vue'
import EmptyView from '../components/EmptyView.vue'

export default {
    computed: {
        ...mapStores(useAppStore)
    },
    components: {
        MediaItem,
        EmptyView,
    },
    data() {
        return {
            tracks: [],
            loaded: false,
            forbidden: false,
        }
    },
    methods: {
        fetchTracks() {
            let component = this
            component.appStore.loading = true
            http.get(vlcApi.trackList)
                .catch(function (error) {
                    if (error.response !== undefined && error.response.status == 403) {
                        component.forbidden = true;
                    }
                })
                .then((response) => {
                    this.loaded = true;
                    if (response) {
                        component.forbidden = false;
                        this.tracks = response.data
                    }
                    component.appStore.loading = false
                });
        },
        getEmptyText() {
            if (this.forbidden) return this.$t('FORBIDDEN')
            return this.$t('NO_MEDIA')
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
