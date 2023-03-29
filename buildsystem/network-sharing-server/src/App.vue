<template>
    <AppHeader />
    <PageDownloads />
    <MiniPlayer />
</template>

<script>
import AppHeader from './components/AppHeader.vue'
import MiniPlayer from './components/MiniPlayer.vue'
import PageDownloads from './pages/PageDownloads.vue'

export default {
    name: 'App',
    components: {
        PageDownloads,
        AppHeader,
        MiniPlayer,
    }
}
</script>

<style></style>
