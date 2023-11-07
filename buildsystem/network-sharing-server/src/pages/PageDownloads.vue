<template>
    <div class="container">
        <div class="flex-container media-content">
            <span class="flex1" v-t="'SEND_LOGS'"></span>
            <button type="button" class="btn btn-primary" v-t="'SEND'" v-on:click="sendLocalLogs()"></button>
        </div>
        <div v-if="loaded" class="row">
            <div v-if="this.logs.length !== 0">
                <LogList :logs="logs" />
            </div>
            <div v-else>
                <EmptyView />
            </div>
        </div>
        <div v-else class="d-flex vertical center-page spinner" style="margin: 0 auto">
        </div>
    </div>
</template>

<script>
import http from '../plugins/auth'
import { vlcApi } from '../plugins/api.js'
import LogList from '../components/LogList.vue'
import EmptyView from '../components/EmptyView.vue'
import { useAppStore } from '../stores/AppStore'
import { mapStores } from 'pinia'
import { sendLogs } from '../plugins/logger'


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
                .then((response) => {
                    this.loaded = true;
                    this.logs = response.data
                    component.appStore.loading = false
                });
        },
        sendLocalLogs() {
            sendLogs()
            this.fetchLogs()
        }
    },
    created: function () {
        this.fetchLogs();
    }
}
</script>

<style></style>
