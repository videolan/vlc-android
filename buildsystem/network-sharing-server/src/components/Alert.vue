<template>
      <div class="alert alert-warning alert-dismissible fade d-inline-block" role="alert" ref="warning" :class="this.appStore.warning !== undefined && this.appStore.warning.type == 'warning' ? 'show' : ''">
        <p v-if="this.appStore.warning !== undefined">{{ this.appStore.warning.message }}</p>
        <button type="button" v-on:click.stop="hideWarning()"  class="btn-close" aria-label="Close"></button>
      </div>
      
      <div class="alert alert-success alert-dismissible fade d-inline-block" role="alert" ref="warning" :class="this.appStore.warning !== undefined && this.appStore.warning.type == 'message' ? 'show' : ''">
        <p v-if="this.appStore.warning !== undefined">{{ this.appStore.warning.message }}</p>
        <button type="button" v-on:click.stop="hideWarning()"  class="btn-close" aria-label="Close"></button>
      </div>
  </template>
  
  <script>
  import { useAppStore } from '../stores/AppStore'
  import { mapStores } from 'pinia'
  
  export default {
    name: 'App',
    components: {
    },
    computed: {
      ...mapStores(useAppStore)
    },
    methods: {
      hideWarning() {
        this.appStore.warning = undefined
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
  