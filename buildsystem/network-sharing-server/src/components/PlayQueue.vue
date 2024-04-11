<template>
    <div id="play_queue" v-show="this.playerStore.playqueueShowing">
        <div class="d-flex border-bottom play-queue-toolbar">
            <div class="flex1">&nbsp;</div>
            <ImageButton type="edit" class="small" v-on:click.stop="this.playerStore.togglePlayQueueEdit()"
                v-bind:class="(this.playerStore.playQueueEdit) ? 'active' : ''" />
            <ImageButton type="close" class="small" v-on:click.stop="hide()" />
        </div>

        <div class="play-queue-items">
            <div v-for="(media, index) in playerStore.playqueueData.medias" :key="media.id" class="play-queue-item">
                <PlayQueueItem :mediaIndex="index" :media="media" />
            </div>
        </div>

    </div>
</template>
  
<script>
import PlayQueueItem from './PlayQueueItem.vue'
import { usePlayerStore } from '../stores/PlayerStore'
import { mapStores } from 'pinia'
import ImageButton from './ImageButton.vue'

export default {
    components: {
        PlayQueueItem,
        ImageButton,
    },
    computed: {
        ...mapStores(usePlayerStore),
    },
    methods: {
        hide() {
            this.playerStore.playqueueShowing = false
            this.playerStore.responsivePlayerShowing = false
        }
    }
}
</script>
  
<style lang="scss">
@import '../scss/colors.scss';


#play_queue {
    position: fixed;
    right: 0;
    top: 0;
    bottom: calc(var(--playerHeight) - 24px);
    z-index: 1021;
    width: 100vw;
    overflow: hidden;
    background-color: var(--bs-body-bg);
    padding-bottom: 48px;
    cursor: pointer;
    box-shadow: 0 4px 12px 0 rgba(0, 0, 0, 0.15);
}

@media screen and (min-width: 768px) {
    #play_queue {
        width: 400px;
        background-color: var(--bs-body-bg);
    }

    .play-queue-header {
        display: none !important;
    }

}

.play-queue-toolbar {
    background-color: var(--bs-card-bg);
    padding-bottom: 8px;
}

.play-queue-items {
    height: 100%;
    overflow-y: scroll;
}

.play-queue-item {
    padding-left: 8px;
    padding-right: 8px;
    padding-top: 8px;
    padding-bottom: 8px;
}


.play-queue-item:hover {
    background-color: var(--hover-gray);
}

.play-queue-header {
    display: flex;
}

.play-queue_item:hover {
    background-color: rgba($primary-color, 0.2);
}
</style>