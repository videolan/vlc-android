/*
 * ************************************************************************
 *  OnboardingScreen.kt
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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import org.videolan.television.ui.compose.theme.BackgroundColorDark
import org.videolan.television.ui.compose.theme.White
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.television.ui.compose.utils.VlcPreview
import org.videolan.vlc.R as vlcR

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColorDark)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false
        ) { page ->
            // Pages will be implemented in subsequent steps
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Page $page", color = White)
            }
        }
    }
}

/**
 * Local button component for onboarding to avoid modifying the common VLCButton
 * and to satisfy the "no icon" requirement.
 */
@Composable
fun OnboardingButton(text: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Button(
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) White else BackgroundColorDark.copy(alpha = 0.4F),
            contentColor = if (focused) MaterialTheme.colorScheme.background else White.copy(alpha = 0.4F)
        ),
        onClick = onClick,
        modifier = modifier
            .padding(8.dp)
            .onFocusChanged {
                focused = it.isFocused
            }
    ) {
        Text(
            text = stringResource(text),
            modifier = Modifier.padding(8.dp)
        )
    }
}

/**
 * Common layout wrapper for onboarding pages to ensure consistent button placement.
 */
@Composable
fun OnboardingPage(
    modifier: Modifier = Modifier,
    buttonText: Int? = null,
    onButtonClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = VlcTVTheme.dimens.overscanVertical)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (buttonText != null) 64.dp else 0.dp),
            contentAlignment = Alignment.Center,
            content = content
        )

        if (buttonText != null && onButtonClick != null) {
            OnboardingButton(
                text = buttonText,
                onClick = onButtonClick,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun OnboardingScreenPreview() {
    VlcPreview {
        OnboardingScreen(onFinish = {})
    }
}

@Preview
@Composable
private fun OnboardingButtonPreview() {
    VlcPreview {
        OnboardingButton(text = vlcR.string.next, onClick = {})
    }
}
