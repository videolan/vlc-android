<template>
  <button class="material-icons mdc-top-app-bar__action-item mdc-icon-button"
        aria-label="play">
    <div class="mdc-icon-button__ripple">
    </div>
    {{type}}
  </button>
</template>

<script>
export default {
  props: {
    type: String
  }
}
</script>