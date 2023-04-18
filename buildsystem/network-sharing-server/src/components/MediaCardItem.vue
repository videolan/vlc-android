<template>
    <div class="ratio media-img-container audio-img-container"
        v-bind:class="(mediaType == 'video') ? 'ratio-16x9' : 'ratio-1x1'">
        <img :src="$getImageUrl(media, this.mediaType)" class="media-img-top">
        <div v-on:click="$play(media, this.mediaType)" class="media-overlay">
            <button class="btn btn-lg overlay-play text-white" type="button">
                <span class="material-symbols-outlined">play_circle</span>
            </button>
        </div>
        <span v-if="(mediaType == 'video')" class="resolution">{{ media.resolution }}</span>
    </div>
    <div class="d-flex">

        <div class="card-body media-text flex1">
            <h6 class="card-title text-truncate">{{ media.title }}</h6>
            <p class="card-text text-truncate">{{ (mediaType == 'video') ? $readableDuration(media.length) : media.artist }}</p>

        </div>
        <div class="dropdown dropstart overlay-more-container">
            <button class="btn btn-lg nav-button overlay-more " type="button" id="dropdownMenuButton1"
                data-bs-toggle="dropdown" aria-expanded="false">
                <span class="material-symbols-outlined">more_vert</span>
            </button>
            <ul class="dropdown-menu media-more" aria-labelledby="dropdownMenuButton1">
                <li> <span v-on:click="$play(media, this.mediaType, false, false)" class="dropdown-item" v-t="'PLAY'"></span> </li>
                <li> <span v-on:click="$play(media, this.mediaType, true, false)" class="dropdown-item" v-t="'APPEND'"></span> </li>
                <li> <span v-if="(mediaType == 'video')" v-on:click="$play(media, this.mediaType, false, true)" class="dropdown-item" v-t="'PLAY_AS_AUDIO'"></span> </li>
                <li v-if="downloadable"> <span v-on:click="$download(media)" class="dropdown-item" v-t="'DOWNLOAD'"></span> </li>

            </ul>
        </div>
    </div>
</template>

<script>
import { playerStore } from '../stores/PlayerStore.js'
import { mapStores } from 'pinia'
export default {
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