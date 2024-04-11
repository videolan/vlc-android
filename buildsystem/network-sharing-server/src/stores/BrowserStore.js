import { defineStore } from 'pinia'
import { reactive } from 'vue'

/**
 * browser store. Manages browser data
 */
export const useBrowserStore = defineStore('browser', {

  state: () => ({
    breadcrumb: [],
    descriptions:reactive([]),
  }),
  getters: {
  },
  actions: {
  },
})