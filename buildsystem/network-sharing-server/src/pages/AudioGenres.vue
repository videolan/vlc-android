<template>
    <div class="container">
        <p>Genres</p>
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
        this.playerStore.currentTab = "genres";
    },
    unmounted: function () {
        this.playerStore.currentTab = "";
    }
}
</script>

<style></style>
