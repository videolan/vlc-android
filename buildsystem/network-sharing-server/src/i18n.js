import { createI18n } from 'vue-i18n'
import { vlcApi } from './plugins/api'

var i18nInstance;

export async function loadLanguage() {
    const response = await fetch(vlcApi.translation).catch(function () {
        return new Response(JSON.stringify({
            code: 400,
            message: '{}'
        }))
    })
    return await response.json()
}

export default function geti18n() {
    return i18nInstance;
}


export function initI18n() {

    return loadLanguage().then(function (data) {
        let i18n = createI18n({
            locale: 'default',
            fallbackLocale: 'default',
            messages: data
        })
        i18n.global.setLocaleMessage("default", data)
        i18nInstance = i18n
        return i18n
    })
}