<template>
    <div class="container">
        <div class="modal fade" id="exampleModal" tabindex="-1" data-bs-backdrop="static"
            aria-labelledby="exampleModalLabel" aria-hidden="true">
            <div class="modal-dialog">
                <div class="modal-content">
                    <div class="modal-header">
                        <h1 class="modal-title fs-5" id="exampleModalLabel" v-t="'SSL_EXPLANATION_TITLE'"></h1>
                    </div>
                    <div class="modal-body" v-t="'SSL_EXPLANATION'">
                    </div>
                    <div class="modal-footer">
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
}
</script>

<style lang='scss'>
@import '../scss/colors.scss';

.digits {
    margin-top: 16px;
    margin-bottom: 16px;
}

.digit {
    margin-left: 16px;
    margin-bottom: 16px;
    height: 48px;
    width: 48px;
    box-sizing: border-box;
    border: 2px solid $dark-background;
    border-radius: 8px;
    -webkit-transition: 0.5s;
    transition: 0.5s;
    outline: none;
}

.digit:focus {
    border: 2px solid $primary-color;
}

.action-btn {
    margin-bottom: 16px;
}

.explanation {
    white-space: pre-line;
    padding-top: 16px;
    padding-bottom: 16px;
}
</style>
