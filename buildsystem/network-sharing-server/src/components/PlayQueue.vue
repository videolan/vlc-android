<template>
    <div id="play_queue" v-show="show">

        <div v-for="media in medias" :key="media.id" class="play-queue_item">
            <PlayQueueItem :media="media" />
        </div>

    </div>
</template>
  
<script>
import PlayQueueItem from './PlayQueueItem.vue'

export default {
    components: {
        PlayQueueItem,
    },
    data() {
        return {
            // show: false,
        }
    },
    props: {
        show: {
            type: Boolean,
            default: true
        },
        medias: Array
    }
}
</script>
  
<style lang="scss">
@import '../scss/app.scss';


#play_queue {
    position: absolute;
    right: 0;
    top: 0;
    height: calc(100% - var(--playerHeight) + 22px);
    width: 300px;
    background-color: $light-grey;
    overflow-y: scroll;
    padding-bottom: 48px;
    
}
.play-queue_item {
  padding-left: 8px;
  padding-right: 8px;
  padding-top: 8px;
  padding-bottom: 8px;
}
</style>