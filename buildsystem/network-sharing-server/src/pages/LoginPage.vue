<template>
    <div class="container">
        <div class="modal fade" id="loginModal" data-bs-backdrop="static" data-bs-keyboard="false" tabindex="-1"
            aria-labelledby="loginModalLabel" aria-hidden="true">
            <div class="modal-dialog modal-fullscreen">
                <div class="modal-content">
                    <div class="modal-header">
                        <h1 class="modal-title fs-5" id="loginModalLabel">Modal title</h1>
                        <!-- <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button> -->
                    </div>
                    <div class="modal-body">
                        <p v-t="'CODE_REQUESTED'"></p>
                        <div class="col-sm-5 text-center">
                            <input name="firstdigit" class="digit text-center" type="password" required ref="digit1"
                                size="1" maxlength="1" tabindex="0">
                            <input name="secondtdigit" class="digit text-center" type="password" required ref="digit2"
                                size="1" maxlength="1" tabindex="1">
                            <input name="thirddigit" class="digit text-center" type="password" required ref="digit3"
                                size="1" maxlength="1" tabindex="2">
                            <input name="fourthdigit" class="digit text-center" type="password" required ref="digit4"
                                size="1" maxlength="1" tabindex="3">
                        </div>

                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-primary" v-t="'SEND'" v-on:click="manageClick"></button>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
import axios from 'axios'
import { vlcApi } from '../plugins/api.js'
import { useAppStore } from '../stores/AppStore'
import { mapStores } from 'pinia'
import { Modal } from 'bootstrap'
import { sha256 } from 'js-sha256';


export default {
    components: {
    },
    computed: {
        ...mapStores(useAppStore)
    },
    data() {
        return {
            modal: null
        }
    },
    methods: {
        manageClick() {
            let code = ""
            code += this.$refs.digit1.value
            code += this.$refs.digit2.value
            code += this.$refs.digit3.value
            code += this.$refs.digit4.value
            let component = this
            console.log(code)
            axios.get(vlcApi.verifyCode(sha256(code)))
                .catch(function (error) {
                    if (error.response.status == 403) {
                        component.forbidden = true;
                    }
                })
                .then((response) => {
                    this.loaded = true;
                    if (response) {
                        component.forbidden = false;
                        this.genres = response.data
                    }
                    component.appStore.loading = false
                });
        }
    },
    mounted: function () {
        this.appStore.loading = false
        this.modal = new Modal(document.getElementById('loginModal'), {})
        this.modal.show()
        let component = this
        axios.get(vlcApi.code)
            .catch(function (error) {
                if (error.response.status == 403) {
                    component.forbidden = true;
                }
            })
            .then((response) => {
                this.loaded = true;
                if (response) {
                    component.forbidden = false;
                    this.genres = response.data
                }
                component.appStore.loading = false
            });

    },
}
</script>

<style></style>
