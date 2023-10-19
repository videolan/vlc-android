<template>
    <div v-if="loaded && this.videos.length !== 0" class="container">
        <div v-if="this.appStore.displayType[this.$route.name]" class="row gx-3 gy-3 media-list">
            <template v-for="video in videos" :key="video.id">
                <MediaListItem :media="video" :downloadable="true" :mediaType="'video'" />
            </template>
        </div>
        <div v-else class="row gx-3 gy-3 media-content">
            <div class="col-md-3 col-sm-4 col-xs-6" v-for="video in videos" :key="video.id">
                <MediaCardItem :media="video" :downloadable="true" :mediaType="'video'" />
            </div>
        </div>
    </div>
    <div v-else-if="loaded" class="empty-view-container">
        <EmptyView :message="getEmptyText()" />
    </div>
</template>

<script>

import http from '../plugins/auth'
import { vlcApi } from '../plugins/api.js'
import { useAppStore } from '../stores/AppStore'
import { mapStores } from 'pinia'
import EmptyView from '../components/EmptyView.vue'
import MediaCardItem from '../components/MediaCardItem.vue'
import MediaListItem from '../components/MediaListItem.vue'

export default {
    components: {
        EmptyView,
        MediaCardItem,
        MediaListItem,
    },
    computed: {
        ...mapStores(useAppStore)
    },
    data() {
        return {
            videos: [],
            loaded: false,
            forbidden: false,
        }
    },
    methods: {
        fetchVideos() {
            let component = this
            component.appStore.loading = true
            http.get(vlcApi.videoList)
                .catch(function (error) {
                    if (error.response.status == 403) {
                        component.forbidden = true;
                    }
                })
                .then((response) => {
                    this.loaded = true;
                    if (response) {
                        component.forbidden = false;
                        this.videos = response.data
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
