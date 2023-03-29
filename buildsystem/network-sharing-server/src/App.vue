<template>
    <AppHeader />
    <router-view></router-view>
    <MiniPlayer />
</template>

<script>
import AppHeader from './components/AppHeader.vue'
import MiniPlayer from './components/MiniPlayer.vue'

export default {
    name: 'App',
    components: {
        AppHeader,
        MiniPlayer,
    }
}
</script>

<style></style>
