<template>
    <div v-if="loaded && this.videos.length !== 0" class="container">
        <div v-if="this.appStore.displayType[this.$route.name]" class="row gx-3 gy-3 media-content">
            <table class="table table-hover media-list">
                <tbody>
                    <tr v-for="video in videos" :key="video.id">
                        <MediaListItem :media="video" :downloadable="true" :mediaType="'video'"/>
                    </tr>
                </tbody>
            </table>
        </div>
        <div v-else class="row gx-3 gy-3 media-content">
            <div class="col-md-3 col-sm-4 col-xs-6" v-for="video in videos" :key="video.id">
                <MediaCardItem :media="video" :downloadable="true" :mediaType="'video'"/>
            </div>
        </div>
    </div>
    <div v-else-if="loaded">
        <EmptyView :message="$t('NO_MEDIA')" />
    </div>
</template>

<script>

import axios from 'axios'
import { API_URL } from '../config.js'
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
        }
    },
    methods: {
        fetchVideos() {
            let component = this
            component.appStore.loading = true
            axios.get(API_URL + "video-list").then((response) => {
                this.loaded = true;
                this.videos = response.data
                component.appStore.loading = false
            });
        },
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
