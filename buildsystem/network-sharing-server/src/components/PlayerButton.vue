<template>
  <button class="btn btn-lg btn-player" v-bind="$attrs">
    <span class="material-symbols-outlined"></span>
  </button>
</template>

<script>

export default {
  props: {
    type: String
  },
  mounted: function () {
    this.$el.querySelector(".material-symbols-outlined").innerHTML = this.type
  },
}
</script>

<style>
.btn-player {
  color: #000;
  height: 40px;
  padding: 8px;
}

.btn-player.big {
  height: 58px;
}

.btn-player.medium {
  height: 48px;
}

.btn-player.small {
  height: 40px;
}

.btn-player:hover {
  color: grey;
}
.big .material-symbols-outlined {
  font-size: 42px;
}

.medium .material-symbols-outlined {
  font-size: 32px;
}
.small .material-symbols-outlined {
  font-size: 24px;
}
</style>