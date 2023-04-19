<template>
    <td class="media-img-list-td">
        <div class="ratio media-img-container audio-img-container"
            v-bind:class="(mediaType == 'video') ? 'ratio-16x9 video' : 'ratio-1x1'">
            <img :src="$getImageUrl(media, this.mediaType)" class="media-img-list">
            <div v-on:click="$play(media, this.mediaType)" class="media-overlay">
                <ImageButton type="play_circle_white" class="overlay-play" />
            </div>
        </div>
    </td>
    <td>
        <div class="card-body media-text flex1">
            <h6 class="card-title text-truncate">{{ media.title }}</h6>
            <p class="card-text text-truncate">{{ (mediaType == 'video') ? $readableDuration(media.length) + " · " + media.resolution : media.artist }}</p>

        </div>
    </td>
    <td class="media-action-list-td">
        <div class="dropdown dropstart overlay-more-container">
            <ImageButton type="more_vert"  id="dropdownMenuButton1"
                data-bs-toggle="dropdown" aria-expanded="false"/>
            <ul class="dropdown-menu media-more" aria-labelledby="dropdownMenuButton1">
                <li> <span v-on:click="$play(media, this.mediaType, false, false)" class="dropdown-item" v-t="'PLAY'"></span> </li>
                <li> <span v-on:click="$play(media, this.mediaType, true, false)" class="dropdown-item" v-t="'APPEND'"></span> </li>
                <li> <span v-if="(mediaType == 'video')" v-on:click="$play(media, this.mediaType, false, true)" class="dropdown-item" v-t="'PLAY_AS_AUDIO'"></span> </li>
                <li v-if="downloadable"> <span v-on:click="$download(media)" class="dropdown-item" v-t="'DOWNLOAD'"></span> </li>
            </ul>
        </div>
    </td>
</template>

<script>
import ImageButton from './ImageButton.vue'
import { playerStore } from '../stores/PlayerStore'
import { mapStores } from 'pinia'
export default {
    components: {
        ImageButton,
    },
    computed: {
        ...mapStores(playerStore),
    },
    props: {
        media: Object,
        downloadable: Boolean,
        mediaType: String
    },
}
</script>