/*
 * *************************************************************************
 *  BaseHeadlessFragment.kt
 * **************************************************************************
 *  Copyright © 2017-2018 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.helpers.hf

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CompletableDeferred

internal typealias PermissionResults = IntArray

open class BaseHeadlessFragment : Fragment() {

    protected val model : PermissionViewmodel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    protected fun exit() {
        retainInstance = false
        activity?.run {
            if (!isFinishing) supportFragmentManager
                .beginTransaction()
                .remove(this@BaseHeadlessFragment)
                .commitAllowingStateLoss()
        }
    }

    protected fun PermissionResults.granted() = isNotEmpty() && get(0) == PackageManager.PERMISSION_GRANTED
}

class PermissionViewmodel : ViewModel() {

    var permissionRationaleShown = false
    lateinit var deferredGrant : CompletableDeferred<Boolean>
    val permissionPending : Boolean
        get() = ::deferredGrant.isInitialized && !deferredGrant.isCompleted
    val isCompleted : Boolean
        get() = ::deferredGrant.isInitialized && deferredGrant.isCompleted

    fun complete(value:Boolean) {
        if (::deferredGrant.isInitialized) deferredGrant.complete(value)
    }

    fun setupDeferred() {
        deferredGrant = CompletableDeferred<Boolean>().apply {
            invokeOnCompletion { permissionRationaleShown = false }
        }
    }
}