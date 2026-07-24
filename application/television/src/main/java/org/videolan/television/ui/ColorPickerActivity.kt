/*
 * ************************************************************************
 *  ColorPickerActivity.kt
 * *************************************************************************
 * Copyright © 2026 VLC authors and VideoLAN
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

package org.videolan.television.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.videolan.resources.util.applyOverscanMargin
import org.videolan.television.R
import org.videolan.television.ui.compose.composable.screens.ColorPickerScreen
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.television.viewmodel.ColorPickerViewModel

const val COLOR_PICKER_SELECTED_COLOR = "color_picker_selected_color"
const val COLOR_PICKER_TITLE = "color_picker_title"

@AndroidEntryPoint
class ColorPickerActivity : ComponentActivity() {
    private val viewModel: ColorPickerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyOverscanMargin(this)

        val title = intent.extras?.getString(COLOR_PICKER_TITLE) ?: getString(R.string.subtitles_color)
        val previousColor = intent.extras?.getInt(COLOR_PICKER_SELECTED_COLOR) ?: Color.BLACK

        viewModel.initState(title, previousColor)

        setContent {
            VlcTVTheme {
                ColorPickerScreen(
                    viewModel = viewModel,
                    onOk = { selectedColor ->
                        setResult(RESULT_OK, Intent().apply { putExtra(COLOR_PICKER_SELECTED_COLOR, selectedColor) })
                        finish()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }
}
