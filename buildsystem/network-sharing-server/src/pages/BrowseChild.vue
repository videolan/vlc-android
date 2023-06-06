<template>
    <div v-if="loaded && this.browseResult.length !== 0" class="container media-list">
        <div v-if="this.appStore.displayType[this.$route.name]" class="row gx-3 gy-3">
            <template v-for="item in browseResult" :key="item.id">
                <MediaListItem :media="item" :mediaType="(item.isFolder) ? 'folder' : 'file'" />
            </template>
        </div>
        <div v-else class="row gx-3 gy-3 media-content">
            <div class="col-md-3 col-lg-2 col-sm-4 col-xs-6" v-for="item in browseResult" :key="item.id">
                <MediaCardItem :media="item" :mediaType="(item.isFolder) ? 'folder' : 'file'" />
            </div>
        </div>
    </div>
    <div v-else-if="loaded">
        <EmptyView :message="$t('DIRECTORY_EMPTY')" />
    </div>
</template>

<script>

import { useAppStore } from '../stores/AppStore'
import { useBrowserStore } from '../stores/BrowserStore'
import { mapStores } from 'pinia'
import axios from 'axios'
import { API_URL } from '../config.js'
import MediaCardItem from '../components/MediaCardItem.vue'
import MediaListItem from '../components/MediaListItem.vue'
import EmptyView from '../components/EmptyView.vue'

export default {
    computed: {
        ...mapStores(useAppStore),
        ...mapStores(useBrowserStore)
    },
    components: {
        MediaCardItem,
        MediaListItem,
        EmptyView,
    },
    data() {
        return {
            browseResult: Object,
            loaded: false,
        }
    },
    methods: {
        fetchContent() {
            this.browserStore.breadcrumb = []
            let component = this
            component.appStore.loading = true
            axios.get(API_URL + "browse-list", {
                params: {
                    path: this.$route.params.browseId
                }
            }).then((response) => {
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
                this.browseResult = {}
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
    }
}
</script>

<style lang='scss'>
@import '../scss/colors.scss';
</style>
