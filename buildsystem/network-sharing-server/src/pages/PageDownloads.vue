<template>
    <div class="container">
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
import axios from 'axios'
import { API_URL } from '../config.js'
import LogList from '../components/LogList.vue'
import EmptyView from '../components/EmptyView.vue'


export default {
    components: {
        LogList,
        EmptyView,
    },
    data() {
        return {
            logs: [],
            loaded: false,
        }
    },
    methods: {
        fetchLogs() {
            axios.get(API_URL + "list-logfiles").then((response) => {
                this.loaded = true;
                this.logs = response.data
            });
        }
    },
    created: function () {
        this.fetchLogs();
    }
}
</script>

<style></style>
