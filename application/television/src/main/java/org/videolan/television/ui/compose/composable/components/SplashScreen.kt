/*
 * ************************************************************************
 *  SplashScreen.kt
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

package org.videolan.television.ui.compose.composable.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import org.videolan.vlc.R

@Composable
fun SplashScreen(content: @Composable () -> Unit) {
    var showSplashScreen by remember { mutableStateOf(true) }
    var startingTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect("splash_screen") {
        while (System.currentTimeMillis() < startingTime + 1000) {
            delay(100)
        }
        showSplashScreen = false
    }
    val duration = 300

    AnimatedContent(
        targetState = showSplashScreen,
        transitionSpec = {
            if (!targetState) {
                fadeIn(tween(duration)) togetherWith
                        fadeOut(tween(duration))
            } else {
                fadeIn(tween(duration)) togetherWith
                        fadeOut(tween(duration))
            }.using(
                SizeTransform(clip = false)
            )
        }, label = "tabs collapsing animation"
    ) { visible ->

        if (visible) {
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.launch_screen_anim)
            var atEnd by remember { mutableStateOf(false) }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Image(
                    painter = rememberAnimatedVectorPainter(image, atEnd),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = ContentScale.Crop
                )
            }
            DisposableEffect(Unit) {
                atEnd = !atEnd
                onDispose { }
            }
        } else
            content()
    }
}