<template>
    <div v-if="this.logs.length !== 0">
        <LogList :logs="logs" />
    </div>
    <div v-else>
        <EmptyView />
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
        }
    },
    methods: {
        fetchLogs() {
            axios.get(API_URL + "list-logfiles").then((response) => {
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
