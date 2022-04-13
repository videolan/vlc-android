/**
 * **************************************************************************
 * ConfirmDeleteDialog.kt
 * ****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
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
 * ***************************************************************************
 */
package org.videolan.vlc.gui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import org.videolan.vlc.R
import org.videolan.vlc.gui.view.SwipeToUnlockView
import org.videolan.vlc.util.FeatureFlag

const val FEATURE_FLAG_WARNING_TITLE = "FEATURE_FLAG_WARNING_TITLE"
const val FEATURE_FLAG_WARNING_TEXT = "FEATURE_FLAG_WARNING_TEXT"

open class FeatureFlagWarningDialog : VLCBottomSheetDialogFragment() {

    private lateinit var title: TextView
    private lateinit var warning: TextView
    private lateinit var swipeToEnable: SwipeToUnlockView
    private var titleString: String? = null
    private var warningString: String? = null
    protected lateinit var listener: () -> Unit

    companion object {

        /**
         * Create a new FeatureFlagWarningDialog
         * @param featureFlag the feature flag to display a warning for
         */
        fun newInstance(featureFlag: FeatureFlag, listener: () -> Unit): FeatureFlagWarningDialog {

            return FeatureFlagWarningDialog().apply {
                arguments = bundleOf(FEATURE_FLAG_WARNING_TEXT to featureFlag.warning, FEATURE_FLAG_WARNING_TITLE to featureFlag.title)
                this.listener = listener
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        arguments?.getInt(FEATURE_FLAG_WARNING_TITLE)?.let { titleString = getString(it) }
        arguments?.getInt(FEATURE_FLAG_WARNING_TEXT)?.let { warningString = getString(it) }
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_feature_flag_warning, container)
        title = view.findViewById(R.id.title)
        warning = view.findViewById(R.id.warning)
        swipeToEnable = view.findViewById(R.id.swipe_to_enable)

        swipeToEnable.setOnStartTouchingListener { isCancelable = false }
        swipeToEnable.setOnStopTouchingListener { isCancelable = true }
        swipeToEnable.setOnUnlockListener {
            dismiss()
            listener.invoke()
        }
        
        
        title.text = titleString
        warning.text = warningString
        return view
    }

    override fun getDefaultState(): Int {
        return STATE_EXPANDED
    }

    override fun initialFocusedView(): View = swipeToEnable

    override fun needToManageOrientation(): Boolean {
        return true
    }
}