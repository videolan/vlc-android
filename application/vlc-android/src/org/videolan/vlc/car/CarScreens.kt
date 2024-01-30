/*
 * ************************************************************************
 *  CarScreens.kt
 * *************************************************************************
 * Copyright © 2023 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 * **************************************************************************
 *
 *
 */
package org.videolan.vlc.car

import android.content.Intent
import androidx.annotation.StringRes
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemList
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import androidx.core.content.edit
import org.videolan.resources.AppContextProvider
import org.videolan.tools.ENABLE_ANDROID_AUTO_SEEK_BUTTONS
import org.videolan.tools.ENABLE_ANDROID_AUTO_SPEED_BUTTONS
import org.videolan.tools.KEY_PLAYBACK_SPEED_PERSIST
import org.videolan.tools.Settings
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.preferences.PreferencesActivity

class CarSettingsScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val controlItems = ItemList.Builder().apply {
            addItem(buildBrowsableRow(R.string.audio, AutoControlScreen(carContext)))
        }.build()

        val helpItems = ItemList.Builder().apply {
            addItem(buildBrowsableRow(R.string.podcast_mode,
                    LongMessageScreen(carContext, R.string.podcast_mode, R.string.podcast_mode_help, R.string.podcast_mode_genres)))
            addItem(buildBrowsableRow(R.string.voice_control,
                    LongMessageScreen(carContext, R.string.voice_control, R.string.voice_control_help)))
        }.build()

        val moreItems = ItemList.Builder().apply {
            addItem(Row.Builder().apply {
                setTitle(AppContextProvider.appContext.getString(R.string.open_on_phone))
                setOnClickListener(ParkedOnlyOnClickListener.create(::openPreferencesOnPhone))
            }.build())
        }.build()

        return ListTemplate.Builder().apply {
            setHeaderAction(Action.BACK)
            setTitle(AppContextProvider.appContext.getString(R.string.preferences))
            addSectionedList(SectionedItemList.create(controlItems, AppContextProvider.appContext.getString(R.string.controls_prefs_category)))
            addSectionedList(SectionedItemList.create(helpItems, AppContextProvider.appContext.getString(R.string.help)))
            addSectionedList(SectionedItemList.create(moreItems, AppContextProvider.appContext.getString(R.string.more_preferences)))
        }.build()
    }

    private fun buildBrowsableRow(@StringRes title: Int, screen: Screen): Row {
        return Row.Builder().apply {
            setBrowsable(true)
            setTitle(AppContextProvider.appContext.getString(title))
            setOnClickListener { screenManager.push(screen) }
        }.build()
    }

    private fun openPreferencesOnPhone() {
        val intent = Intent(carContext, PreferencesActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        carContext.startActivity(intent)
        CarToast.makeText(carContext, AppContextProvider.appContext.getText(R.string.prefs_opened_on_phone), CarToast.LENGTH_SHORT).show()
    }
}

class LongMessageScreen(carContext: CarContext, @StringRes private val titleRes: Int, @StringRes private val messageRes: Int, @StringRes private val messageRes2: Int? = null) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val title = AppContextProvider.appContext.getString(titleRes)
        val message = messageRes2?.let {
            AppContextProvider.appContext.getString(messageRes).format(AppContextProvider.appContext.getString(it))
        } ?: AppContextProvider.appContext.getString(messageRes)
        return LongMessageTemplate.Builder(message).apply {
            setHeaderAction(Action.BACK)
            setTitle(title)
        }.build()
    }
}

class AutoControlScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val itemList = ItemList.Builder().apply {
            addItem(buildToggleRow(R.string.playback_speed_title,
                    R.string.playback_speed_summary,
                    KEY_PLAYBACK_SPEED_PERSIST))
            addItem(buildToggleRow(
                    R.string.enable_android_auto_speed_buttons,
                    R.string.enable_android_auto_speed_buttons_summary,
                    ENABLE_ANDROID_AUTO_SPEED_BUTTONS))
            addItem(buildToggleRow(
                    R.string.enable_android_auto_seek_buttons,
                    R.string.enable_android_auto_seek_buttons_summary,
                    ENABLE_ANDROID_AUTO_SEEK_BUTTONS))
        }.build()

        return ListTemplate.Builder().apply {
            setSingleList(itemList)
            setHeaderAction(Action.BACK)
            setTitle(AppContextProvider.appContext.getString(R.string.audio))
        }.build()
    }

    private fun buildToggleRow(@StringRes titleRes: Int, @StringRes summaryRes: Int, key: String, defValue: Boolean = false): Row {
        val settings = Settings.getInstance(carContext)
        val onCheckedListener = Toggle.OnCheckedChangeListener { isChecked ->
            settings.edit { putBoolean(key, isChecked) }
            // Invoke publishState to update the Android Auto UI
            PlaybackService.updateState()
        }
        return Row.Builder().apply {
            setTitle(AppContextProvider.appContext.getString(titleRes))
            addText(AppContextProvider.appContext.getString(summaryRes))
            setToggle(Toggle.Builder(onCheckedListener).apply {
                setChecked(settings.getBoolean(key, defValue))
            }.build())
        }.build()
    }
}
