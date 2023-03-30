<template>
  <button class="btn btn-lg btn-player">
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
  color: #FFFFFF;
}

.btn-player:hover {
  color: grey;
}
</style>