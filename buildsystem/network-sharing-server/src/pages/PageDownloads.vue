<template>
    <div class="container" v-if="loaded && this.logs.length !== 0">
        <div class="row">
            <div v-if="this.logs.length !== 0">
                <LogList :logs="logs" @refresh-logs="fetchLogs" />
            </div>
            <div v-else class="d-flex vertical center-page spinner" style="margin: 0 auto">
            </div>
        </div>

    </div>
    <div  v-else-if="loaded" class="empty-view-container">
        <EmptyView :message="getEmptyText()" />
    </div>
</template>

<script>
import http from '../plugins/auth'
import { vlcApi } from '../plugins/api.js'
import LogList from '../components/LogList.vue'
import EmptyView from '../components/EmptyView.vue'
import { useAppStore } from '../stores/AppStore'
import { mapStores } from 'pinia'


export default {
    components: {
        LogList,
        EmptyView,
    },
    computed: {
        ...mapStores(useAppStore)
    },
    data() {
        return {
            logs: [],
            loaded: false,
        }
    },
    methods: {
        fetchLogs() {
            let component = this
            component.appStore.loading = true
            http.get(vlcApi.logfileList)
                .catch(function (error) {
                    if (error.response !== undefined && error.response.status == 403) {
                        component.forbidden = true;
                    }
                })
                .then((response) => {
                    this.loaded = true;
                    if (response) {
                        component.forbidden = false;
                        let data = response.data
                        data.unshift({
                            "path": "",
                            "date": this.$t('LOG_TYPE_CURRENT'),
                            "type": "web"
                        })
                        let oldLogs = this.logs
                        if (oldLogs.length != 0) {
                            data.forEach((element) => {
                                if (!oldLogs.some(e => e.path == element.path)) {
                                    element.new = true
                                }
                            });
                        }
                        this.logs = data
                    }
                    component.appStore.loading = false
                });
        },
        getEmptyText() {
            if (this.forbidden) return this.$t('FORBIDDEN')
            return this.$t('DIRECTORY_EMPTY')
        }
    },
    created: function () {
        this.fetchLogs();
    }
}
</script>

<style></style>
