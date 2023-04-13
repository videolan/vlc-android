<template>
    <div id="play_queue" v-show="show">
        <div class="play-queue-header">
            <div class="flex1">&nbsp;</div>
            <PlayerButton type="close" class="small" v-on:click.stop="hide()" />
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
import { playerStore } from '../stores/PlayerStore'
import { mapStores } from 'pinia'
import PlayerButton from './PlayerButton.vue'

export default {
    components: {
        PlayQueueItem,
        PlayerButton,
    },
    computed: {
        ...mapStores(playerStore),
    },
    props: {
        show: {
            type: Boolean,
            default: true
        }
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
    position: absolute;
    right: 0;
    top: 0;
    height: calc(100% - var(--playerHeight) + 24px);
    width: 100%;
    overflow: hidden;
    background-color: $grey-overlay;
    padding-bottom: 48px;
    cursor: pointer;

}

@media screen and (min-width: 768px) {
    #play_queue {
        width: 300px;
        background-color: $light-grey;
    }

    .play-queue-header {
        display: none !important;
    }

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

.play-queue-header {
    display: flex;
}

.play-queue_item:hover {
    background-color: rgba($primary-color, 0.2);
}
</style>