/*
 * *************************************************************************
 *  CustomEditTextPreferenceDialogFragment.kt
 * **************************************************************************
 *  Copyright © 2022 VLC authors and VideoLAN
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

package org.videolan.television.ui.preferences

import android.R
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.EditText
import androidx.core.os.bundleOf
import androidx.preference.EditTextPreferenceDialogFragment

class CustomEditTextPreferenceDialogFragment : EditTextPreferenceDialogFragment() {

    private var customFilters = emptyArray<InputFilter>()
    private var customInputType = InputType.TYPE_NULL

    companion object {
        fun newInstance(key: String?) =
            CustomEditTextPreferenceDialogFragment().apply {
                arguments = bundleOf(ARG_KEY to key)
            }
    }

    override fun onBindDialogView(view: View) {
        view.findViewById<EditText>(R.id.edit).apply {
            if (customFilters.isNotEmpty()) filters = customFilters
            if (customInputType != InputType.TYPE_NULL) inputType = customInputType
        }
        super.onBindDialogView(view)
    }

    fun setFilters(filters: Array<InputFilter>) {
        this.customFilters = filters
    }

    fun setInputType(inputType: Int) {
        this.customInputType = inputType
    }
}