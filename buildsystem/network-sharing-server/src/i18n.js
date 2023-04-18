import { createI18n } from 'vue-i18n'
import { API_URL } from './config'

export const i18n = createI18n({
    locale: 'default',
    fallbackLocale: 'default'
})

var loadedLanguages = false

function setI18nLanguage(lang) {
    i18n.locale = lang
    document.querySelector('html').setAttribute('lang', lang)
    return lang
}

export async function loadLanguageAsync() {
    if (loadedLanguages) {
        if (i18n.locale !== "default") setI18nLanguage("default")
        return Promise.resolve()
    }
    const response = await fetch(`${API_URL}translation`)
    const msgs = await response.json()
    loadedLanguages = true
    i18n.global.setLocaleMessage("default", msgs)
    return setI18nLanguage("default")
}