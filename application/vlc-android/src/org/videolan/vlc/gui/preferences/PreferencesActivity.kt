/*****************************************************************************
 * PreferencesActivity.java
 *
 * Copyright © 2011-2014 VLC authors and VideoLAN
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
 */

package org.videolan.vlc.gui.preferences

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.resources.ACTIVITY_RESULT_PREFERENCES
import org.videolan.resources.util.parcelable
import org.videolan.tools.KEY_RESTRICT_SETTINGS
import org.videolan.tools.RESULT_RESTART
import org.videolan.tools.RESULT_RESTART_APP
import org.videolan.tools.RESULT_UPDATE_ARTISTS
import org.videolan.tools.Settings
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.BaseActivity
import org.videolan.vlc.gui.PinCodeActivity
import org.videolan.vlc.gui.PinCodeReason
import org.videolan.vlc.gui.preferences.search.PreferenceItem
import org.videolan.vlc.gui.preferences.search.PreferenceParser
import org.videolan.vlc.gui.preferences.search.PreferenceSearchActivity

const val EXTRA_PREF_END_POINT = "extra_pref_end_point"
class PreferencesActivity : BaseActivity() {

    private val searchRequestCode = 167
    private var mAppBarLayout: AppBarLayout? = null
    override val displayTitle = true
    private var pinCodeResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            finish()
        }
    }
    override fun getSnackAnchorView(overAudioPlayer:Boolean): View? = findViewById(android.R.id.content)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.getInstance(this).getBoolean(KEY_RESTRICT_SETTINGS, false)) {
            val intent = PinCodeActivity.getIntent(this, PinCodeReason.CHECK)
            pinCodeResult.launch(intent)
        }

        setContentView(R.layout.preferences_activity)
        setSupportActionBar(findViewById<View>(R.id.main_toolbar) as Toolbar)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_placeholder, PreferencesFragment().apply { if (intent.hasExtra(EXTRA_PREF_END_POINT)) arguments = bundleOf(EXTRA_PREF_END_POINT to intent.parcelable(EXTRA_PREF_END_POINT)) })
                    .commit()
        }
        mAppBarLayout = findViewById(R.id.appbar)
        mAppBarLayout!!.post { ViewCompat.setElevation(mAppBarLayout!!, resources.getDimensionPixelSize(R.dimen.default_appbar_elevation).toFloat()) }
    }

    internal fun expandBar() {
        mAppBarLayout!!.setExpanded(true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_prefs, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (!supportFragmentManager.popBackStackImmediate())
                    finish()
                return true
            }
            R.id.menu_pref_search -> {
                startActivityForResult(Intent(this, PreferenceSearchActivity::class.java), searchRequestCode)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == searchRequestCode && resultCode == RESULT_OK) {
            data?.extras?.parcelable<PreferenceItem>(EXTRA_PREF_END_POINT)?.let {
                supportFragmentManager.popBackStack()
                supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_placeholder, PreferencesFragment().apply { arguments = bundleOf(EXTRA_PREF_END_POINT to it) })
                        .commit()
            }
        }

    }

    fun exitAndRescan() {
        setRestart()
        val intent = intent
        finish()
        startActivity(intent)
    }

    fun setRestart() {
        setResult(RESULT_RESTART)
    }

    fun setRestartApp() {
        setResult(RESULT_RESTART_APP)
    }

    fun updateArtists() {
        setResult(RESULT_UPDATE_ARTISTS)
    }

    fun detectHeadset(detect: Boolean) {
        val le = PlaybackService.headSetDetection
        if (le.hasObservers()) le.value = detect
    }

    companion object {
        /**
         * Launch the preferences and redirect to a given preference
         * @param activity The calling activity
         * @param prefKey The preference key to redirect to
         * @throws NoSuchElementException if the key is not found
         */
        suspend fun launchWithPref(activity: FragmentActivity, prefKey:String) {
            val pref = withContext(Dispatchers.IO) {
                PreferenceParser.parsePreferences(activity)
            }.first { it.key == prefKey }
            val intent = Intent(activity, PreferencesActivity::class.java)
            intent.putExtra(EXTRA_PREF_END_POINT, pref)
            activity.startActivityForResult(intent, ACTIVITY_RESULT_PREFERENCES)
        }
    }
}
