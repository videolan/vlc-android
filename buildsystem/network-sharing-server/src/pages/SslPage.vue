<template>
    <div class="container">
        <div class="modal fade" id="exampleModal" tabindex="-1" data-bs-backdrop="static"
            aria-labelledby="exampleModalLabel" aria-hidden="true">
            <div class="modal-dialog">
                <div class="modal-content">
                    <div class="modal-header">
                        <img class="image-button-image" :src="(`./icons/security_warning.svg`)" />
                        <h1 class="modal-title fs-5 ssl-title" id="exampleModalLabel" v-t="'SSL_EXPLANATION_TITLE'"></h1>
                    </div>
                    <div class="modal-body">


                        <p v-t="'SSL_EXPLANATION'" class="ssl-explanation"></p>
                        <p v-t="'SSL_EXPLANATION_BROWSER'" class="fw-bold ssl-explanation"></p>
                        <p v-t="'SSL_EXPLANATION_ACCEPT'" class="ssl-explanation"></p>
                    </div>
                    <div class="modal-footer">
                        <a href="https://docs.videolan.me/vlc-user/android/3.5/en/more/remoteaccess/remote_access_ssl.html" target="_blank" class="link-primary" v-t="'LEARN_MORE'"></a>
                        <button type="button" class="btn btn-primary" v-t="'SSL_BUTTON'"
                            v-on:click="secureConnection()"></button>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
import { Modal } from 'bootstrap'
import http from '../plugins/auth'
import { vlcApi } from '../plugins/api.js'


export default {
    components: {
    },
    computed: {
    },
    data() {
        return {
            modal: null,
            challenge: ""
        }
    },
    methods: {
        secureConnection() {
            let component = this
            http.get(vlcApi.secureUrl)
                .catch(function (error) {
                    if (error.response !== undefined && error.response.status == 403) {
                        component.forbidden = true;
                    }
                })
                .then((response) => {
                    if (response) {
                        location.href = response.data
                    }
                });
        },
    },
    mounted: function () {
        this.modal = new Modal(document.getElementById('exampleModal'), {})
        this.modal.show()
    },
    unmounted: function () {
        this.modal.hide()
    }
}
</script>

<style lang='scss'>
@import '../scss/colors.scss';


.ssl-title {
    flex: 1;
    margin-left: 16px;
}

.ssl-explanation {
    margin-bottom: 16px;
}
</style>
