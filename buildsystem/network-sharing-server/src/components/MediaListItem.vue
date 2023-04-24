<template>
    <td class="media-img-list-td">
        <div class="ratio media-img-container" v-bind:class="(mainImgClasses())">
            <img v-lazy="$getImageUrl(media, this.mediaType)" class="media-img-list">
            <div v-on:click="$play(media, this.mediaType)" class="media-overlay" v-show="!isBrowse()">
                <ImageButton type="play_circle_white" class="overlay-play" />
            </div>
        </div>
    </td>
    <td>
        <div class="card-body media-text flex1">
            <h6 class="card-title text-truncate">{{ media.title }}</h6>
            <p class="card-text text-truncate">{{ (mediaType == 'video') ? $readableDuration(media.length) + " · " +
                media.resolution : media.artist }}</p>

        </div>
    </td>
    <td class="media-action-list-td">
        <div class="dropdown dropstart overlay-more-container" v-show="!isBrowse()">
            <ImageButton type="more_vert" id="dropdownMenuButton1" data-bs-toggle="dropdown" aria-expanded="false" />
            <ul class="dropdown-menu media-more" aria-labelledby="dropdownMenuButton1">
                <li> <span v-on:click="$play(media, this.mediaType, false, false)" class="dropdown-item"
                        v-t="'PLAY'"></span> </li>
                <li> <span v-on:click="$play(media, this.mediaType, true, false)" class="dropdown-item"
                        v-t="'APPEND'"></span> </li>
                <li> <span v-if="(mediaType == 'video')" v-on:click="$play(media, this.mediaType, false, true)"
                        class="dropdown-item" v-t="'PLAY_AS_AUDIO'"></span> </li>
                <li v-if="downloadable"> <span v-on:click="$download(media)" class="dropdown-item" v-t="'DOWNLOAD'"></span>
                </li>
            </ul>
        </div>
    </td>
</template>

<script>
import ImageButton from './ImageButton.vue'
export default {
    components: {
        ImageButton,
    },
    methods: {
        mainImgClasses() {
            if (this.mediaType == 'video') return 'ratio-16x9 video audio-img-container'
            if (this.isBrowse()) return 'ratio-1x1'
            return 'ratio-1x1 audio-img-container'
        },
        isBrowse() {
            return (this.mediaType == 'folder' || this.mediaType == 'network')
        }
    },
    props: {
        media: Object,
        downloadable: Boolean,
        mediaType: String
    },
}
</script>