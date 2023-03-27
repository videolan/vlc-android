<template>
  <AppHeader />
  <PageDownloads />
  <MiniPlayer />
  <AppFooter />
</template>

<script>
import AppHeader from './components/AppHeader.vue'
import AppFooter from './components/AppFooter.vue'
import MiniPlayer from './components/MiniPlayer.vue'
import PageDownloads from './pages/PageDownloads.vue'

export default {
  name: 'App',
  components: {
    PageDownloads,
    AppHeader,
    AppFooter,
    MiniPlayer,
  }
}
</script>

<style></style>
