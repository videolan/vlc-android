<template>
    <form :action="getFormUrl()" method="post" ref="form" autocomplete="off"></form>
    <div class="container">
        <div class="modal fade" id="loginModal" data-bs-backdrop="static" data-bs-keyboard="false" tabindex="-1"
            aria-labelledby="loginModalLabel" aria-hidden="true">
            <div class="modal-dialog modal-fullscreen">
                <div class="modal-content">
                    <div class="modal-body">
                        <div class="container">
                            <h5 class="text-center explanation" v-t="'CODE_REQUEST_EXPLANATION'"></h5>
                            <p v-show="this.$route.meta.showError" v-t="'INVALID_OTP'"
                                class="text-danger text-center fs-5 text fw-bold"></p>
                            <div class="text-center digits">
                                <input autocomplete="new-password" pattern="[0-9]*" inputmode="numeric" class="digit text-center" type="password" required
                                    ref="digit0" id="digit0" size="1" maxlength="1" tabindex="1" @input="manageInput"
                                    @keydown="handleKeyDown($event, 0)">
                                <input autocomplete="new-password" pattern="[0-9]*" inputmode="numeric" class="digit text-center" type="password" required
                                    ref="digit1" id="digit1" size="1" maxlength="1" tabindex="2" @input="manageInput"
                                    @keydown="handleKeyDown($event, 1)">
                                <input autocomplete="new-password" pattern="[0-9]*" inputmode="numeric" class="digit text-center" type="password" required
                                    ref="digit2" id="digit2" size="1" maxlength="1" tabindex="3" @input="manageInput"
                                    @keydown="handleKeyDown($event, 2)">
                                <input autocomplete="new-password" pattern="[0-9]*" inputmode="numeric" class="digit text-center" type="password" required
                                    ref="digit3" id="digit3" size="1" maxlength="1" tabindex="4" @input="manageInput"
                                    @keydown="handleKeyDown($event, 3)">
                            </div>
                            <div class="text-center">
                                <button type="button" class="btn btn-primary action-btn btn-lg" v-t="'SEND'" tabindex="5"
                                    v-on:click="manageClick" ref="send"></button>
                            </div>
                            <div class="text-center">
                                <button type="button" class="btn btn-primary action-btn btn-light" v-t="'NEW_CODE'"
                                    tabindex="6" v-on:click="generateCode(true)"></button>
                            </div>
                        </div>

                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
import http from '../plugins/auth'
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
            challenge: "",
            inputs: [this.$refs.digit1, this.$refs.digit2, this.$refs.digit3, this.$refs.digit4]
        }
    },
    methods: {
        handleKeyDown(event, index) {
            this.inputs = [this.$refs.digit0, this.$refs.digit1, this.$refs.digit2, this.$refs.digit3]
            if (event.key !== "Tab" &&
                event.key !== "ArrowRight" &&
                event.key !== "ArrowLeft"
            ) {
                event.preventDefault();
            }

            if (event.key === "Backspace") {
                if (event.target.value !== "") {
                    event.target.value = ""
                    return
                }
                let i = index - 1
                if (i >= 0) {
                    console.log(`Resetting input at index ${i}`)
                    this.inputs[i].value = ""
                    this.inputs[i].focus()
                }

                return;
            }

            if ((new RegExp('^([0-9])$')).test(event.key)) {
                event.target.value = event.key;

                this.manageInput(event)

            }
        },
        getFormUrl() {
            return vlcApi.verifyCode
        },
        manageClick() {
            let form = this.$refs.form
            let code = ""
            code += this.$refs.digit0.value
            code += this.$refs.digit1.value
            code += this.$refs.digit2.value
            code += this.$refs.digit3.value
            code += this.challenge

            var hidden = document.createElement("input");
            hidden.type = "hidden";
            hidden.name = "code";
            hidden.value = sha256(code);
            form.appendChild(hidden);
            form.submit();
        },
        manageInput(event) {
            if (event.target.value.length > 0) {
                switch (event.target.id) {
                    case "digit0":
                        this.$refs.digit1.focus()
                        break;
                    case "digit1":
                        this.$refs.digit2.focus()
                        break;
                    case "digit2":
                        this.$refs.digit3.focus()
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
            http.post(vlcApi.code, body, {
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
        this.generateCode(false)
        let component = this
        document.getElementById('loginModal').addEventListener('shown.bs.modal', function () {
            component.$refs.digit0.focus()
        })
        this.modal.show()
    },
    unmounted: function () {
        this.modal.hide()
    }
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
