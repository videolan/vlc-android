<template>
  <div class="container" v-if="this.appStore.showAddStream">
    <div class="modal fade" id="streamModal" ref="streamModal" tabindex="-1" role="dialog"
      aria-labelledby="streamModalTitle" @close="hidden" aria-hidden="true">
      <div class="modal-dialog modal-dialog-centered" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title" id="streamModalTitle" v-t="'NEW_STREAM'"></h5>
          </div>
          <div class="modal-body">
            <div class="container">
              <div class="d-flex align-items-center justify-content-center">
                <input type="text" id="searchInput" class="search-input" ref="searchText"
                  :placeholder="$t('ENTER_STREAM')" @keydown.enter="onPressEnter">
                <ImageButton type="send" data-bs-toggle="tooltip" data-bs-placement="bottom" v-on:click="play()" :title="$t('SEND')" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
  
<script>
import { useAppStore } from '../stores/AppStore'
import { mapStores } from 'pinia'
import { Modal } from 'bootstrap'
import ImageButton from '../components/ImageButton.vue'
import { nextTick } from 'vue'

export default {
  name: 'App',
  components: {
    ImageButton,
  },
  computed: {
    ...mapStores(useAppStore)
  },
  data() {
    return {
      modal: null,
    }
  },
  methods: {
    play() {
      this.$play({ path: this.$refs.searchText.value, id: -1 }, "stream", false, false)
      this.appStore.showAddStream = false
    },
    hidden() {
      this.appStore.showAddStream = false
    }
  },
  mounted: function () {
    this.appStore.$subscribe(() => {
      if (this.appStore.showAddStream) {
        this.modal = new Modal(document.getElementById('streamModal'), {})
        this.modal.show()
        nextTick(() => {
          this.$refs.streamModal.addEventListener('hidden.bs.modal', () => {
            this.appStore.showAddStream = false
          })
        })

      } else {
        if (this.modal) this.modal.hide()
      }
    })

  },
}
</script>
  
<style>
</style>
  