<template>
  <button class="btn btn-lg btn-player">
    <!-- <div class="mdc-icon-button__ripple">
    </div> -->
    <i :class="iclass"></i>
  </button>
</template>

<script>
export default {
  props: {
    type: String
  },
  computed: {
    iclass: function () {
      return `fa fa-6x fa-` + this.type
    },
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