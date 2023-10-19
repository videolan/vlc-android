<template>
    <div class="container">
        <div class="modal fade" id="loginModal" data-bs-backdrop="static" data-bs-keyboard="false" tabindex="-1"
            aria-labelledby="loginModalLabel" aria-hidden="true">
            <div class="modal-dialog modal-fullscreen">
                <div class="modal-content">
                    <div class="modal-body">
                        <div class="container">
                            <h5 class="text-center explanation" v-t="'CODE_REQUEST_EXPLANATION'"></h5>
                            <!-- <p v-t="'CODE_REQUESTED'"></p> -->
                            <div class="text-center digits">
                                <input name="firstdigit" class="digit text-center" type="password" required ref="digit1"
                                    id="digit1" size="1" maxlength="1" tabindex="0" @input="manageInput">
                                <input name="secondtdigit" class="digit text-center" type="password" required ref="digit2"
                                    id="digit2" size="1" maxlength="1" tabindex="1" @input="manageInput">
                                <input name="thirddigit" class="digit text-center" type="password" required ref="digit3"
                                    id="digit3" size="1" maxlength="1" tabindex="2" @input="manageInput">
                                <input name="fourthdigit" class="digit text-center" type="password" required ref="digit4"
                                    id="digit4" size="1" maxlength="1" tabindex="3" @input="manageInput">
                            </div>
                            <div class="text-center">
                                <button type="button" class="btn btn-primary action-btn btn-lg" v-t="'SEND'"
                                    v-on:click="manageClick" ref="send"></button>
                            </div>
                            <div class="text-center">
                                <button type="button" class="btn btn-primary action-btn btn-light" v-t="'NEW_CODE'"
                                    v-on:click="generateCode(true)"></button>
                            </div>
                        </div>

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
            modal: null,
            challenge: ""
        }
    },
    methods: {
        manageClick() {
            let code = ""
            code += this.$refs.digit1.value
            code += this.$refs.digit2.value
            code += this.$refs.digit3.value
            code += this.$refs.digit4.value
            code += this.challenge
            location = vlcApi.verifyCode(sha256(code))
        },
        manageInput(event) {
            if (event.target.value.length > 0) {
                switch (event.target.id) {
                    case "digit1":
                        this.$refs.digit2.focus()
                        break;
                    case "digit2":
                        this.$refs.digit3.focus()
                        break;
                    case "digit3":
                        this.$refs.digit4.focus()
                        break;
                    default:
                        this.$refs.send.focus()
                }
            }
        },
        generateCode(force) {
            let component = this
            let body = { challenge: "" }
            if (force) body = { challenge: this.challenge }
            axios.post(vlcApi.code, body, {
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                }
            })
                .then((response) => {
                    this.loaded = true;
                    if (response) {
                        component.forbidden = false;
                        this.challenge = response.data
                    }
                    component.appStore.loading = false
                });
        }
    },
    mounted: function () {
        this.appStore.loading = false
        this.modal = new Modal(document.getElementById('loginModal'), {})
        this.modal.show()
        this.generateCode(false)
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
