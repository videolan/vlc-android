package org.videolan.tools

import android.annotation.TargetApi
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import java.util.*
import kotlin.collections.ArrayList

object LocaleUtils {
    fun getLocalesUsedInProject(projectLocales: Array<String>, defaultLocaleText: String): LocalePair {

        val localesEntry = arrayOfNulls<String>(projectLocales.size)
        for (i in projectLocales.indices) {

            val localesEntryValue = projectLocales[i]

            val locale = getLocaleFromString(localesEntryValue)

            val displayLanguage = locale.getDisplayLanguage(locale)
            val displayCountry = locale.getDisplayCountry(locale)
            if (displayCountry.isEmpty()) {
                localesEntry[i] = displayLanguage.firstLetterUppercase()
            } else {
                localesEntry[i] = "${displayLanguage.firstLetterUppercase()} - ${displayCountry.firstLetterUppercase()}"
            }
        }

        //sort
        val localeTreeMap = TreeMap<String, String>()
        for (i in projectLocales.indices) {
            localeTreeMap[localesEntry[i]!!] = projectLocales[i]
        }

        val finalLocaleEntries = ArrayList<String>(localeTreeMap.size + 1).apply { add(0, defaultLocaleText) }
        val finalLocaleEntryValues = ArrayList<String>(localeTreeMap.size + 1).apply { add(0, "") }

        var i = 1
        for ((key, value) in localeTreeMap) {
            finalLocaleEntries.add(i, key)
            finalLocaleEntryValues.add(i, value)
            i++
        }

        return LocalePair(finalLocaleEntries.toTypedArray(), finalLocaleEntryValues.toTypedArray())
    }

    fun getLocaleFromString(string: String): Locale {

        /**
         * See [android.content.res.AssetManager.getLocales]
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return Locale.forLanguageTag(string)
        }

        //Best effort on determining the locale

        val separators = arrayOf("_", "-")

        for (separator in separators) {
            //see if there is a language and a country
            if (string.contains(separator)) {
                val splittedLocale = string.split(separator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (splittedLocale.size == 2) {
                    return Locale(splittedLocale[0], splittedLocale[1])
                }
            }
        }


        return Locale(string)
    }
}

class LocalePair(val localeEntries: Array<String>, val localeEntryValues: Array<String>)

@Suppress("DEPRECATION")
fun ContextWrapper.wrap(language: String): ContextWrapper {
    val config = baseContext.resources.configuration
    val sysLocale: Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        this.getSystemLocale()
    } else {
        this.getSystemLocaleLegacy()
    }

    if (language.isNotEmpty() && sysLocale.language != language) {
        val locale = if (language.contains("-")) Locale(language.substringBefore("-"), language.substringAfter("-")) else Locale(language)
        Locale.setDefault(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.setSystemLocale(locale)
        } else {
            this.setSystemLocaleLegacy(locale)
        }
    }

    val context = baseContext.createConfigurationContext(config)
    return ContextWrapper(context)
}

@Suppress("DEPRECATION")
fun ContextWrapper.getSystemLocaleLegacy(): Locale = baseContext.resources.configuration.locale

@TargetApi(Build.VERSION_CODES.N)
fun ContextWrapper.getSystemLocale(): Locale = baseContext.resources.configuration.locales[0]

@Suppress("DEPRECATION")
fun Context.getLocaleLanguages(): List<String> {
    val locales = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val locales = ArrayList<Locale>()
        for (i in 0 until resources.configuration.locales.size()) {
            locales.add(resources.configuration.locales.get(i))
        }
        locales
    } else arrayListOf(resources.configuration.locale)
    return locales.map { it.language }
}

@Suppress("DEPRECATION")
fun ContextWrapper.setSystemLocaleLegacy(locale: Locale) {
    baseContext.resources.configuration.locale = locale
}

@TargetApi(Build.VERSION_CODES.N)
fun ContextWrapper.setSystemLocale(locale: Locale) {
    baseContext.resources.configuration.setLocale(locale)
}

fun Context.getContextWithLocale(appLocale: String?): Context {
    appLocale.takeIf { !it.isNullOrEmpty() }?.let {
        return ContextWrapper(this).wrap(it)
    }
    return this
}