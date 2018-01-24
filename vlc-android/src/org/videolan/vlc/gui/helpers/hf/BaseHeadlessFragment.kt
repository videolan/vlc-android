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

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity

open class BaseHeadlessFragment : Fragment() {
    protected var mActivity: FragmentActivity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is FragmentActivity) mActivity = context
    }

    override fun onDetach() {
        super.onDetach()
        mActivity = null
    }

    protected fun exit() {
        if (mActivity?.isFinishing == false) mActivity!!.supportFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
    }

    companion object {
        internal var callback: Runnable? = null

        internal fun executeCallback() {
            callback?.let {
                try {
                    Handler().postDelayed(it, 500);
                } catch (ignored: Exception) {
                } finally {
                    callback = null
                }
            }
        }
    }
}
