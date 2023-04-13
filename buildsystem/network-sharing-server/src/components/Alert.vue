<template>
      <div class="alert alert-warning alert-dismissible fade d-inline-block" role="alert" ref="warning" :class="this.playerStore.warning !== undefined && this.playerStore.warning.type == 'warning' ? 'show' : ''">
        <p v-if="this.playerStore.warning !== undefined">{{ this.playerStore.warning.message }}</p>
        <button type="button" v-on:click.stop="hideWarning()"  class="btn-close" aria-label="Close"></button>
      </div>
      
      <div class="alert alert-success alert-dismissible fade d-inline-block" role="alert" ref="warning" :class="this.playerStore.warning !== undefined && this.playerStore.warning.type == 'message' ? 'show' : ''">
        <p v-if="this.playerStore.warning !== undefined">{{ this.playerStore.warning.message }}</p>
        <button type="button" v-on:click.stop="hideWarning()"  class="btn-close" aria-label="Close"></button>
      </div>
  </template>
  
  <script>
  import { playerStore } from '../stores/PlayerStore'
  import { mapStores } from 'pinia'
  
  export default {
    name: 'App',
    components: {
    },
    computed: {
      ...mapStores(playerStore)
    },
    methods: {
      hideWarning() {
        this.playerStore.warning = undefined
      }
    },
  }
  </script>
  
  <style>

  #app .alert {
    position: fixed;
    bottom: 0;
    left: 16px;
    z-index: 1022;
  }
  </style>
  