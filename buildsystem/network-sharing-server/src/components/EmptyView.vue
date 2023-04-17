<template>
  <div class="all-center container">
    <img v-bind:src="getAppAsset('ic_empty')">
    <p>{{message}}</p>
  </div>
</template>

<script>
import { API_URL } from '../config.js'

export default {
  methods: {
    getAppAsset(name) {
      return API_URL + 'icon?id=' + name + '&width=64'
    }
  },
  props: {
        message: String
  },
}
</script>

<style>
.all-center {
  height: 108px;
  display: flex;
  vertical-align: middle;
  justify-content: center;
  flex-direction: column;
  align-items: center;
  margin-top: 24px;
}
.all-center p {
  margin-top: 8px;
  font-size: 1.2em;
  font-weight: bold;
}
</style>