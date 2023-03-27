<template>
  <footer class="footer position-fixed bottom-0 text-center text-lg-start" id="app-footer">
    <div class="text-center p-3">
      <p>VLC for Android is free, open-source software published by
        <a href="http://www.videolan.org" target="_blank">VideoLAN</a>. <br />Modification
        and redistribution is subject to both the <a href="http://opensource.org/licenses/GPL-2.0" target="_blank">GPLv2
          (or
          later)</a> and the <a href="http://opensource.org/licenses/MPL-2.0" target="_blank">MPLv2</a> as
        well as further rights
        reserved by the VideoLAN non-profit organization.<br />You can find more information
        about licensing in the About dialog within the app.<br />VideoLAN, VLC and VLC media
        player are internationally registered trademarks of the <a href="http://www.videolan.org/videolan/"
          target="_blank">VideoLAN non-profit
          organization</a>.
      </p>
    </div>
  </footer>
</template>

<script>
export default {

}
</script>

<style lang="scss">
@import '../scss/app.scss';

.footer {
  background-color: $primary-color;
  padding: 8px;
  left: 0;
  right: 0;
  height: var(--navbar-height);
  color: #fff;
  // color: var(--light-grey, #fff);
}
</style>