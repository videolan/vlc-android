<template>
    <div v-if="loaded && this.tracks.length !== 0" class="container">
        <div v-if="this.appStore.displayType[this.$route.name]" class="row gx-3 gy-3 media-list">
            <template v-for="track in tracks" :key="track.id">
                <MediaItem :isCard="false" :media="track" :mediaType="'track'" />
            </template>
        </div>
        <div v-else class="row gx-3 gy-3 media-content">
            <div class="col-md-3 col-lg-2 col-sm-4 col-6" v-for="track in tracks" :key="track.id">
                <MediaItem :isCard="true" :media="track" :mediaType="'track'" />
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
            let apiUrl = ''
            switch (this.$route.name) {
                case 'VideoGroupList':
                case 'VideoFolderList':
                    apiUrl = vlcApi.videoList(this.appStore.videoGrouping, this.$route.params.groupId, this.$route.params.folderId)
                    break;
                case 'ArtistDetails':
                    apiUrl = vlcApi.artistDetails(this.$route.params.artistId)
                    break;
                case 'AlbumDetails':
                    apiUrl = vlcApi.albumDetails(this.$route.params.albumId)
                    break;
                case 'GenreDetails':
                    apiUrl = vlcApi.genreDetails(this.$route.params.genreId)
                    break;
                case 'PlaylistDetails':
                    apiUrl = vlcApi.playlistDetails(this.$route.params.playlistId)
                    break;
            }

            console.log(`url is ${apiUrl}`)




            http.get(apiUrl)
                .catch(function (error) {
                    if (error.response !== undefined && error.response.status == 403) {
                        component.forbidden = true;
                    }
                })
                .then((response) => {
                    this.loaded = true;
                    if (response) {
                        component.forbidden = false;

                        switch (this.$route.name) {
                            case 'VideoGroupList':
                            case 'VideoFolderList':
                                this.tracks = response.data.content
                                break;
                            case 'ArtistDetails':
                                this.tracks = response.data.albums
                                break;
                            default:
                                this.tracks = response.data.tracks
                                break;
                        }
                    }
                    component.appStore.loading = false
                    component.appStore.title = response.data.name
                });

        },
        getEmptyText() {
            if (this.forbidden) return this.$t('FORBIDDEN')
            return this.$t('NO_MEDIA')
        },
    },
    created: function () {
        this.fetchTracks();
    },
    unmounted: function () {
        console.log("unmounted")
        this.appStore.title = ''
    }
}
</script>

<style lang='scss'>
@import '../scss/colors.scss';
</style>
