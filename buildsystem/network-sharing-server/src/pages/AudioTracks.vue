<template>
    <div class="container">
        <p>Tracks</p>
    </div>
</template>

<script>

import { playerStore } from '../stores/PlayerStore'
import { mapStores } from 'pinia'
export default {
    computed: {
        ...mapStores(playerStore)
    },
    mounted: function () {
        this.playerStore.currentTab = "tracks";
    },
    unmounted: function () {
        this.playerStore.currentTab = "";
    }
}
</script>

<style></style>
