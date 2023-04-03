<template>
    <div id="play_queue" v-show="show">

        <div v-for="(media, index) in playerStore.playqueueData.medias" :key="media.id" class="play-queue_item">
            <PlayQueueItem :mediaIndex="index" :media="media" />
        </div>

    </div>
</template>
  
<script>
import PlayQueueItem from './PlayQueueItem.vue'
import { playerStore } from '../stores/PlayerStore'
import { mapStores } from 'pinia'

export default {
    components: {
        PlayQueueItem,
    },
    computed: {
        ...mapStores(playerStore),
    },
    props: {
        show: {
            type: Boolean,
            default: true
        }
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
    cursor: pointer;

}

.play-queue_item {
    padding-left: 8px;
    padding-right: 8px;
    padding-top: 8px;
    padding-bottom: 8px;
}

.play-queue_item:hover {
    background-color: rgba($primary-color, 0.2);
}
</style>