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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.videolan.vlc.util.AppScope

open class
BaseHeadlessFragment : androidx.fragment.app.Fragment() {
    protected var mActivity: androidx.fragment.app.FragmentActivity? = null
    var channel: SendChannel<Unit>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is androidx.fragment.app.FragmentActivity) mActivity = context
    }

    override fun onDetach() {
        super.onDetach()
        mActivity = null
    }

    protected fun exit() {
        if (mActivity?.isFinishing == false) mActivity!!.supportFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
    }

    fun executePendingAction() {
        channel?.let { it.offer(Unit) }
        channel = null
    }

    companion object {

        internal fun waitForIt(channel: Channel<Unit>, cb: Runnable) {
            AppScope.launch {
                channel.receive()
                channel.close()
                cb.run()
            }
        }
    }
}
