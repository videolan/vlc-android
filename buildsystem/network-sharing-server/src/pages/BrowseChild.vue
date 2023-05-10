<template>
    <div v-if="loaded && this.browseResult.content.length !== 0" class="container">
        <!-- <h5 class="media-content text-primary">{{ this.$route.params.browseId }}</h5> -->
        <div class="media-content breadcrumb">
            <nav aria-label="breadcrumb">
                <ol class="breadcrumb">
                    <li class="breadcrumb-item"
                        v-bind:class="(item.path == this.$route.params.browseId) ? '' : 'text-primary clickable'"
                        v-for="item in browseResult.breadcrumb" :key="item.path" v-on:click="manageClick(item)">
                        {{ item.title }}
                    </li>
                </ol>
            </nav>

        </div>
        <div v-if="this.appStore.displayType[this.$route.name]" class="row gx-3 gy-3">
            <table class="table table-hover media-list">
                <tbody>
                    <tr v-for="item in browseResult.content" :key="item.id">
                        <MediaListItem :media="item" :mediaType="(item.isFolder) ? 'folder' : 'file'" />
                    </tr>
                </tbody>
            </table>
        </div>
        <div v-else class="row gx-3 gy-3 media-content">
            <div class="col-md-3 col-lg-2 col-sm-4 col-xs-6" v-for="item in browseResult.content" :key="item.id">
                <MediaCardItem :media="item" :mediaType="(item.isFolder) ? 'folder' : 'file'" />
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
        ...mapStores(useAppStore)
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
            let component = this
            component.appStore.loading = true
            axios.get(API_URL + "browse-list", {
                params: {
                    path: this.$route.params.browseId
                }
            }).then((response) => {
                this.loaded = true;
                this.browseResult = response.data
                component.appStore.loading = false
            });
        },
        manageClick(browsePoint) {
            if (browsePoint.path == "" || browsePoint.path == "root") {
                this.$router.push({ name: 'BrowseList' })
            } else {
                this.$router.push({ name: 'BrowseChild', params: { browseId: browsePoint.path } })
            }
        }
    },
    watch: {
        $route(to) {
            if (to.params.browseId !== undefined) {
                this.browseResult = {}
                this.loaded = false
                this.fetchContent()
            }
        }
    },
    created: function () {
        this.fetchContent()
    }
}
</script>

<style lang='scss'>
@import '../scss/colors.scss';
</style>
