<template>
    <div v-if="loaded && this.browseResult.length !== 0" class="container">
        <div v-if="this.appStore.displayType[this.$route.name]" class="row gx-3 gy-3  media-list">
            <template v-for="item in browseResult" :key="item.id">
                <MediaItem :isCard="false" :media="item" :mediaType="(item.isFolder) ? 'folder' : 'file'"/>
            </template>
        </div>
        <div v-else class="row gx-3 gy-3 media-content">
            <div class="col-md-3 col-lg-2 col-sm-4 col-6" v-for="item in browseResult" :key="item.id">
                <MediaItem :isCard="true" :media="item" :mediaType="(item.isFolder) ? 'folder' : 'file'" />
            </div>
        </div>
    </div>
    <div v-else-if="loaded" class="empty-view-container">
        <EmptyView :message="$t('DIRECTORY_EMPTY')" />
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
        ...mapStores(useAppStore),
        ...mapStores(useBrowserStore)
    },
    components: {
        MediaItem,
        EmptyView,
    },
    data() {
        return {
            browseResult: [],
            loaded: false,
        }
    },
    methods: {
        fetchContent() {
            this.browserStore.breadcrumb = []
            let component = this
            component.appStore.loading = true
            http.get(vlcApi.browseList(this.$route.params.browseId))
                .then((response) => {
                    this.loaded = true;
                    this.browseResult = response.data.content
                    component.appStore.loading = false
                    component.browserStore.breadcrumb = response.data.breadcrumb
                });
        },
    },
    watch: {
        $route(to) {
            if (to.params.browseId !== undefined) {
                this.browseResult = []
                this.loaded = false
                this.fetchContent()
            } else {
                this.browserStore.breadcrumb = []
            }
        }
    },
    created: function () {
        this.fetchContent()
    },
    unmounted: function () {
        this.browserStore.breadcrumb = []
        this.appStore.loading = false
        this.browserStore.descriptions = []
    },
    mounted: function () {
        this.browserStore.$subscribe((mutation, state) => {
            state.descriptions.forEach(description => {
                this.browseResult.forEach(item => {
                    if (item.path == description.path) {
                        item.artist = description.description
                    }
                })
            })
        })
    }
}
</script>

<style lang='scss'>
@import '../scss/colors.scss';

.empty-container {
    margin-top: 24px;
}
</style>
