/*
 * ************************************************************************
 *  ColorPickerScreen.kt
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

package org.videolan.television.ui.compose.composable.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.videolan.television.R
import org.videolan.television.ui.compose.composable.components.ColorPickerItem
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.television.viewmodel.ColorPickerViewModel

@Composable
fun ColorPickerScreen(
    viewModel: ColorPickerViewModel,
    onOk: (Int) -> Unit,
    onCancel: () -> Unit
) {
    val colors by viewModel.colors.collectAsState()
    val selectedHueIndex by viewModel.selectedHueIndex.collectAsState()
    val selectedVariantIndex by viewModel.selectedVariantIndex.collectAsState()
    val title by viewModel.title.collectAsState()
    val initialColor by viewModel.initialColor.collectAsState()

    ColorPickerContent(
        title = title,
        colors = colors,
        selectedHueIndex = selectedHueIndex,
        selectedVariantIndex = selectedVariantIndex,
        initialColor = initialColor,
        onHueSelected = viewModel::selectHue,
        onVariantSelected = viewModel::selectVariant,
        getVariantColor = viewModel::getVariantColor,
        onOk = onOk,
        onCancel = onCancel
    )
}

@Composable
fun ColorPickerContent(
    title: String,
    colors: List<Int>,
    selectedHueIndex: Int,
    selectedVariantIndex: Int,
    initialColor: Int,
    onHueSelected: (Int) -> Unit,
    onVariantSelected: (Int) -> Unit,
    getVariantColor: (Int, Int) -> Int,
    onOk: (Int) -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = title,
            fontSize = 22.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
            color = Color.White
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(20),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(4.dp)
        ) {
            itemsIndexed(colors) { index, color ->
                ColorPickerItem(
                    color = color,
                    isSelected = selectedHueIndex == index,
                    onClick = { onHueSelected(index) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item(span = { GridItemSpan(20) }) {
                Box(modifier = Modifier.padding(vertical = 16.dp)) {
                    HorizontalDivider(thickness = 1.dp, color = Color(0xFF424242))
                }
            }

            val baseColor = if (colors.isNotEmpty()) colors[selectedHueIndex] else android.graphics.Color.GRAY
            items(20) { index ->
                val variantColor = getVariantColor(baseColor, index)
                ColorPickerItem(
                    color = variantColor,
                    isSelected = selectedVariantIndex == index,
                    onClick = { onVariantSelected(index) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(text = stringResource(id = R.string.previous_color), color = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            ColorPickerItem(
                color = initialColor,
                isSelected = false,
                onClick = {},
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            val currentColor = if (colors.isNotEmpty()) getVariantColor(colors[selectedHueIndex], selectedVariantIndex) else initialColor
            ColorPickerItem(
                color = currentColor,
                isSelected = false,
                onClick = {},
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(id = R.string.new_color), color = Color.White)

            Spacer(modifier = Modifier.weight(1f))

            TextButton(onClick = onCancel) {
                Text(text = stringResource(id = R.string.cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onOk(currentColor) }) {
                Text(text = stringResource(id = R.string.ok))
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun ColorPickerScreenPreview() {
    VlcTVTheme {
        ColorPickerContent(
            title = "Subtitle color",
            colors = List(100) { android.graphics.Color.HSVToColor(floatArrayOf(3.6f * it, 1f, 1f)) },
            selectedHueIndex = 5,
            selectedVariantIndex = 10,
            initialColor = android.graphics.Color.RED,
            onHueSelected = {},
            onVariantSelected = {},
            getVariantColor = { c, i -> c },
            onOk = {},
            onCancel = {}
        )
    }
}
