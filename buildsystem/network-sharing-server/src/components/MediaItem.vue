<template>
    <div v-if="(isCard)" class="card">
        <div v-on:click="manageClick" class="ratio clickable" v-bind:class="(mainImgClasses())">
            <img v-if="this.mediaType == 'file'" v-lazy="$getImageUrl(media, `${media.fileType}_big`)" class="media-img-top">
            <img v-else v-lazy="$getImageUrl(media, `${this.mediaType}_big`)" class="media-img-top">
            <div class="media-overlay" v-show="!isBrowse()">
                <img class="overlay-play" :src="(isOpenable() ? `./icons/open.svg` : `./icons/play_circle_white.svg`)"
                    width="48" />
            </div>
            <span v-if="(mediaType == 'video' && media.resolution != '')" class="resolution">{{ media.resolution }}</span>
            <img class="played" :src="(`./icons/played.svg`)" v-show="(media.played)" />

            <div class="card-progress-container" v-show="(media.progress > 0)">
                <div class="card-progress full"></div>
                <div class="card-progress" v-bind:style="(getProgressStyle())"></div>
            </div>
            <div class="item-play" v-show="isPlayable()" v-on:click.stop="$play(media, this.mediaType, false, false)">
                <img class="image-button-image" :src="(`./icons/play_item.svg`)"  :title="$t('PLAY')"/>
            </div>
        </div>
        <div class="d-flex align-items-end">

            <div class="card-body media-text flex1">
                <h6 class="text-truncate" data-bs-toggle="tooltip" data-bs-placement="left"
                    :title="(media.title)">{{ media.title }}</h6>
                <p class="card-text text-truncate subtitle" v-bind:class="((!this.getDescription()) ? 'empty-desc' : '')">
                    {{ getDescription() }}
                </p>

            </div>
            <div class="dropdown dropstart overlay-more-container" v-show="!this.hideOverflow">
                <ImageButton type="more_vert" id="dropdownMenuButton1" data-bs-toggle="dropdown" aria-expanded="false" />
                <ul class="dropdown-menu media-more" aria-labelledby="dropdownMenuButton1">
                    <li> <span v-on:click="$play(media, this.mediaType, false, false)" class="dropdown-item"
                            v-t="'PLAY'"></span> </li>
                    <li> <span v-on:click="$play(media, this.mediaType, true, false)" class="dropdown-item"
                            v-t="'APPEND'"></span> </li>
                    <li> <span v-if="(mediaType == 'video')" v-on:click="$play(media, this.mediaType, false, true)"
                            class="dropdown-item" v-t="'PLAY_AS_AUDIO'"></span> </li>
                    <li v-if="(mediaType != 'file' && mediaType != 'folder')"> <span
                            v-on:click="$download(media, this.mediaType, this.downloadable)" class="dropdown-item"
                            v-t="'DOWNLOAD'"></span>
                    </li>

                </ul>
            </div>
        </div>
    </div>
    <div v-else class="d-flex clickable media-img-list-tr">
        <div v-on:click="manageClick" class="ratio media-img-container" v-bind:class="(mainImgClasses())">
            <img v-if="this.mediaType == 'file'" v-lazy="$getImageUrl(media, media.fileType)" class="media-img-list">
            <img v-else v-lazy="$getImageUrl(media, this.mediaType)" class="media-img-list">
            <div class="media-overlay" v-show="!isBrowse()">
                <img class="overlay-play" :src="(isOpenable() ? `./icons/open.svg` : `./icons/play_circle_white.svg`)"
                    width="24" />
            </div>
            <span v-if="(mediaType == 'video' && media.resolution != '')" class="resolution">{{ media.resolution }}</span>
        <img class="played" :src="(`./icons/played.svg`)" v-show="(media.played && mediaType == 'video')"/>

            <div class="card-progress-container" v-show="(media.progress > 0)">
                <div class="card-progress full"></div>
                <div class="card-progress" v-bind:style="(getProgressStyle())"></div>
            </div>
        </div>
        <div v-on:click="manageClick" class="card-body media-text flex1">
            <h6 class="card-title text-truncate">{{ media.title }}</h6>
            <p class="card-text text-truncate subtitle"
                v-bind:class="((!this.getDescription()) ? 'empty-desc empty-desc-list' : '')">{{ getDescription() }}</p>

        </div>
        <div class="dropdown dropstart overlay-more-container" v-show="!this.hideOverflow">
            <ImageButton type="more_vert" id="dropdownMenuButton1" data-bs-toggle="dropdown" aria-expanded="false" />
            <ul class="dropdown-menu media-more" aria-labelledby="dropdownMenuButton1">
                <li> <span v-on:click="$play(media, this.mediaType, false, false)" class="dropdown-item"
                        v-t="'PLAY'"></span> </li>
                <li> <span v-on:click="$play(media, this.mediaType, true, false)" class="dropdown-item"
                        v-t="'APPEND'"></span> </li>
                <li> <span v-if="(mediaType == 'video')" v-on:click="$play(media, this.mediaType, false, true)"
                        class="dropdown-item" v-t="'PLAY_AS_AUDIO'"></span> </li>
                <li v-if="(mediaType != 'file' && mediaType != 'folder')"> <span
                        v-on:click="$download(media, this.mediaType, this.downloadable)" class="dropdown-item"
                        v-t="'DOWNLOAD'"></span>
                </li>
            </ul>
        </div>
    </div>
</template>

<script>
import ImageButton from './ImageButton.vue'
import { Tooltip } from 'bootstrap';
export default {
    components: {
        ImageButton,
    },
    methods: {
        mainImgClasses() {
            if (this.mediaType.startsWith('video')) return 'ratio-16x9 video audio-img-container'
            if (this.isBrowse() || this.mediaType == "file") return 'ratio-1x1'
            return 'ratio-1x1 audio-img-container'
        },
        getProgressStyle() {
            if (this.mediaType.startsWith('video') && this.media.progress > 0 && this.media.length > 0) {
                return `width: ${this.media.progress * 100 / this.media.length}%`
            }
            return ''
        },
        isBrowse() {
            return (this.mediaType == 'folder' || this.mediaType == 'network' || this.mediaType == 'stream' || this.mediaType == 'new-stream')
        },
        isPlayable() {
            return (this.mediaType == 'album' || this.mediaType == 'artist')
        },
        isOpenable() {
            return ['video-group', 'video-folder', 'artist', 'album', 'playlist', 'genre'].includes(this.mediaType)
        },
        getDescription() {
            if (this.mediaType == 'video') {
                return `${this.$readableDuration(this.media.length)}`
            } else {
                if (this.isCard && this.media.artist == " ") return '\xa0'
                return this.media.artist
            }
        },
        manageClick() {
            if (this.mediaType == 'genre') {
                this.$router.push({ name: 'GenreDetails', params: { genreId: this.media.id } })
            } else if (this.mediaType == 'playlist') {
                this.$router.push({ name: 'PlaylistDetails', params: { playlistId: this.media.id } })
            } else if (this.mediaType == 'album') {
                this.$router.push({ name: 'AlbumDetails', params: { albumId: this.media.id } })
            } else if (this.mediaType == 'artist') {
                this.$router.push({ name: 'ArtistDetails', params: { artistId: this.media.id } })
            } else if (this.mediaType == 'video-group') {
                this.$router.push({ name: 'VideoGroupList', params: { groupId: this.media.id } })
            } else if (this.mediaType == 'video-folder') {
                this.$router.push({ name: 'VideoFolderList', params: { folderId: this.media.id } })
            } else if (['folder', 'storage', 'network'].includes(this.mediaType)) {
                this.$router.push({ name: 'BrowseChild', params: { browseId: this.media.path } })
            } else {
                this.$play(this.media, this.mediaType)
            }
        }
    },
    props: {
        media: Object,
        downloadable: Boolean,
        mediaType: String,
        isCard: Boolean,
        hideOverflow: {
            type: Boolean,
            default: false
        }
    },
    mounted() {
        var tooltipTriggerList = [].slice.call(this.$el.querySelectorAll('[data-bs-toggle="tooltip"]'))
        tooltipTriggerList.map(function (tooltipTriggerEl) {
            return new Tooltip(tooltipTriggerEl, {
                trigger: 'hover'
            })
        })
    }
}
</script>


<style lang='scss'>
@import '../scss/colors.scss';

.empty-desc {
    background: var(--hover-gray);
    width: 100%;
    height: 16px;
    margin-top: 4px;
    margin-bottom: 4px !important;
    border-radius: 4px;
}

.empty-desc-list {
    max-width: 250px;
}

.empty-desc:after {
    content: "";
    width: 100%;
    height: 100%;
    top: 0;
    left: 0;
    display: block;
    animation: slide 1.5s infinite;
    background: linear-gradient(90deg, rgba(0, 0, 0, 0) 0%, var(--placeholder-animation) 50%, rgba(0, 0, 0, 0) 100%);
    float: left;
}

.item-play {
    position: absolute;
    bottom: 0px;
    right: 0px;
    top: auto;
    left: auto;
    margin-bottom: 8px;
    margin-right: 8px;
    width: 32px;
    height: 32px;
    background-color: white;
    border-radius: 50%;
    padding: 4px;
    display: flex;
}

.item-play:hover {
    background-color: $primary-color;
}

/* animation */

@keyframes slide {
    0% {
        transform: translateX(-100%);
    }

    100% {
        transform: translateX(100%);
    }
}
</style>