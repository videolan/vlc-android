<template>
  <div class="btn btn-lg image-button" v-bind="$attrs">
    <img class="image-button-image" :src="(`./icons/${this.type}.svg`)" />

  </div>
</template>

<script>

export default {
  props: {
    type: String
  },
}
</script>

<style lang="scss">
@import '../scss/colors.scss';

.image-button {
  color: #000;
  padding: 8px;
  position: relative;
  filter: var(--img-tint);
}

.image-button-image.big {
  height: 58px;
}

.image-button-image.medium {
  height: 48px;
}

.image-button-image.small {
  height: 40px;
}

.big .image-button-image {
  width: 42px;
}

.medium .image-button-image {
  width: 32px;
}

.small .image-button-image {
  width: 24px;
}


.image-button::before {
  content: "";
  position: absolute;
  width: 40px;
  height: 40px;
  background-color: $primary-color;
  border-radius: 50%;
  opacity: 0;
  top: 4px;
  left: 8px;
  transition: opacity 0.3s ease;
}

.big.image-button::before {
  width: 58px;
  height: 58px;
  left: 8px;
  top: 0px;
}

.medium.image-button::before {
  width: 48px;
  height: 48px;
  left: 8px;
  top: 0px;
}

@media (hover: hover) {

  .image-button:hover::before {
    opacity: 0.3;
  }
}
</style>