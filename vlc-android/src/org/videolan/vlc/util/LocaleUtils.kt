package org.videolan.vlc.util

import android.annotation.TargetApi
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import org.videolan.vlc.VLCApplication
import java.util.*

@Suppress("DEPRECATION")
fun ContextWrapper.wrap(language: String): ContextWrapper {
    val config = baseContext.resources.configuration
    val sysLocale: Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        this.getSystemLocale()
    } else {
        this.getSystemLocaleLegacy()
    }

    if (language.isNotEmpty() && sysLocale.language != language) {
        val locale = Locale(language)
        Locale.setDefault(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.setSystemLocale(locale)
        } else {
            this.setSystemLocaleLegacy(locale)
        }
    }

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        val context = baseContext.createConfigurationContext(config)
        ContextWrapper(context)
    } else {
        baseContext.resources.updateConfiguration(config, baseContext.resources.displayMetrics)
        ContextWrapper(baseContext)
    }
}

@Suppress("DEPRECATION")
fun ContextWrapper.getSystemLocaleLegacy(): Locale = baseContext.resources.configuration.locale

@TargetApi(Build.VERSION_CODES.N)
fun ContextWrapper.getSystemLocale(): Locale = baseContext.resources.configuration.locales[0]

@Suppress("DEPRECATION")
fun ContextWrapper.setSystemLocaleLegacy(locale: Locale) {
    baseContext.resources.configuration.locale = locale
}

@TargetApi(Build.VERSION_CODES.N)
fun ContextWrapper.setSystemLocale(locale: Locale) {
    baseContext.resources.configuration.setLocale(locale)
}

fun Context.getContextWithLocale(): Context {
    VLCApplication.locale.takeIf { !it.isNullOrEmpty() }?.let {
        return ContextWrapper(this).wrap(it)
    }
    return this
}