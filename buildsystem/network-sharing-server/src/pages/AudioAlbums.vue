<template>
    <div class="container">
        <p>Albums</p>
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
        this.playerStore.currentTab = "albums";
    },
    unmounted: function () {
        this.playerStore.currentTab = "";
    }
}
</script>

<style></style>
