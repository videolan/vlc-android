/*
 * ************************************************************************
 *  Theme.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
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

package org.videolan.television.ui.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private val DarkColorScheme = darkColorScheme(
    primary = Orange500,
    secondary = Orange800,
    background = BackgroundColorMedium,
    surface = BackgroundColorMedium,
    surfaceVariant = BackgroundColor,
    onSurface = White,
    onPrimary = White,
    surfaceDim = BackgroundColorDark
)

private val SettingsColorScheme = darkColorScheme(
    primary = Orange800,
    onPrimary = White,
    primaryContainer = Orange800,
    onPrimaryContainer = White,
    secondary = Gray200,
    onSecondary = Black,
    background = BackgroundColorDark,
    onBackground = White,
    surface = BackgroundColorMedium,
    surfaceVariant = BackgroundColor,
    onSurface = White,
    onSurfaceVariant = Gray200,
    outline = Gray600,
    outlineVariant = Gray400,
    inverseOnSurface = Black,
    inverseSurface = White
)

object VlcTVTheme {
    val dimens: VlcDimens
        @Composable
        @ReadOnlyComposable
        get() = LocalVlcDimens.current
}

@Composable
fun VlcTVTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalVlcDimens provides VlcDimens()
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun VlcTVSettingsTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalVlcDimens provides VlcDimens()
    ) {
        MaterialTheme(
            colorScheme = SettingsColorScheme,
            typography = Typography,
            content = content
        )
    }
}
