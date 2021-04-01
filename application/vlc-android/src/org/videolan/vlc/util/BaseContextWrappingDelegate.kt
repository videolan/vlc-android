/*
 * ************************************************************************
 *  BaseContextWrappingDelegate.kt
 * *************************************************************************
 * Copyright © 2021 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
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

package androidx.appcompat.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.AttributeSet
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import org.videolan.resources.AppContextProvider
import org.videolan.tools.getContextWithLocale

/**
 * Context wrapping used to force the locale in all activities
 * See https://stackoverflow.com/a/58004553
 */
class BaseContextWrappingDelegate(private val superDelegate: AppCompatDelegate) : AppCompatDelegate() {

    override fun getSupportActionBar() = superDelegate.supportActionBar

    override fun setSupportActionBar(toolbar: Toolbar?) = superDelegate.setSupportActionBar(toolbar)

    override fun getMenuInflater(): MenuInflater? = superDelegate.menuInflater

    override fun onCreate(savedInstanceState: Bundle?) {
        superDelegate.onCreate(savedInstanceState)
        removeActivityDelegate(superDelegate)
        addActiveDelegate(this)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) = superDelegate.onPostCreate(savedInstanceState)

    override fun onConfigurationChanged(newConfig: Configuration?) = superDelegate.onConfigurationChanged(newConfig)

    override fun onStart() = superDelegate.onStart()

    override fun onStop() = superDelegate.onStop()

    override fun onPostResume() = superDelegate.onPostResume()

    override fun setTheme(themeResId: Int) = superDelegate.setTheme(themeResId)

    override fun <T : View?> findViewById(id: Int) = superDelegate.findViewById<T>(id)

    override fun setContentView(v: View?) = superDelegate.setContentView(v)

    override fun setContentView(resId: Int) = superDelegate.setContentView(resId)

    override fun setContentView(v: View?, lp: ViewGroup.LayoutParams?) = superDelegate.setContentView(v, lp)

    override fun addContentView(v: View?, lp: ViewGroup.LayoutParams?) = superDelegate.addContentView(v, lp)

    override fun attachBaseContext2(context: Context) = wrap(superDelegate.attachBaseContext2(super.attachBaseContext2(context)))

    override fun setTitle(title: CharSequence?) = superDelegate.setTitle(title)

    override fun invalidateOptionsMenu() = superDelegate.invalidateOptionsMenu()

    override fun onDestroy() {
        superDelegate.onDestroy()
        removeActivityDelegate(this)
    }

    override fun getDrawerToggleDelegate() = superDelegate.drawerToggleDelegate

    override fun requestWindowFeature(featureId: Int) = superDelegate.requestWindowFeature(featureId)

    override fun hasWindowFeature(featureId: Int) = superDelegate.hasWindowFeature(featureId)

    override fun startSupportActionMode(callback: ActionMode.Callback) = superDelegate.startSupportActionMode(callback)

    override fun installViewFactory() = superDelegate.installViewFactory()

    override fun createView(parent: View?, name: String?, context: Context, attrs: AttributeSet): View? = superDelegate.createView(parent, name, context, attrs)

    override fun setHandleNativeActionModesEnabled(enabled: Boolean) {
        superDelegate.isHandleNativeActionModesEnabled = enabled
    }

    override fun isHandleNativeActionModesEnabled() = superDelegate.isHandleNativeActionModesEnabled

    override fun onSaveInstanceState(outState: Bundle?) = superDelegate.onSaveInstanceState(outState)

    override fun applyDayNight() = superDelegate.applyDayNight()

    override fun setLocalNightMode(mode: Int) {
        superDelegate.localNightMode = mode
    }

    override fun getLocalNightMode() = superDelegate.localNightMode

    private fun wrap(context: Context) = context.getContextWithLocale(AppContextProvider.locale)
}