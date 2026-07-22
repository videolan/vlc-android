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

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import org.videolan.resources.SCHEME_PACKAGE
import org.videolan.resources.util.canReadStorage
import org.videolan.television.ui.compose.theme.BackgroundColorDark
import org.videolan.television.ui.compose.theme.White
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.television.ui.compose.theme.Orange500
import org.videolan.television.ui.compose.utils.VlcPreview
import org.videolan.vlc.R as vlcR

enum class PermissionLevel {
    NONE, MEDIA, ALL
}

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

    var permissionGranted by remember { mutableStateOf(canReadStorage(context)) }
    var selectedLevel by remember { mutableStateOf(PermissionLevel.ALL) }
    var hasDeniedPermission by remember { mutableStateOf(false) }
    var waitingForPermission by remember { mutableStateOf(false) }

    // Re-check permission status when returning to the app
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionGranted = canReadStorage(context)
        if (pagerState.currentPage == 1) {
            if (permissionGranted) {
                scope.launch { pagerState.animateScrollToPage(2) }
            } else if (waitingForPermission) {
                hasDeniedPermission = true
            }
        }
        waitingForPermission = false
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Logic handled in ON_RESUME
    }

    LaunchedEffect(pagerState.currentPage) {
        permissionGranted = canReadStorage(context)
        if (pagerState.currentPage == 1 && permissionGranted) {
            pagerState.animateScrollToPage(2)
        }
    }

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
            when (page) {
                0 -> WelcomePage(onNext = { scope.launch { pagerState.animateScrollToPage(1) } })
                1 -> PermissionsPage(
                    isVisible = pagerState.currentPage == 1,
                    permissionGranted = permissionGranted,
                    hasDeniedPermission = hasDeniedPermission,
                    selectedLevel = selectedLevel,
                    onLevelFocused = { selectedLevel = it },
                    onResetDenial = { hasDeniedPermission = false },
                    onNext = { scope.launch { pagerState.animateScrollToPage(2) } },
                    onRequestPermission = {
                        when (selectedLevel) {
                            PermissionLevel.NONE -> {
                                hasDeniedPermission = true
                            }
                            PermissionLevel.MEDIA -> {
                                waitingForPermission = true
                                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
                                } else {
                                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                                launcher.launch(permissions)
                            }
                            PermissionLevel.ALL -> {
                                waitingForPermission = true
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val uri = Uri.fromParts(SCHEME_PACKAGE, context.packageName, null)
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
                                    context.startActivity(intent)
                                } else {
                                    launcher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                                }
                            }
                        }
                    }
                )
                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Page $page", color = White)
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePage(onNext: () -> Unit) {
    OnboardingPage(
        buttonText = vlcR.string.next,
        onButtonClick = onNext
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = vlcR.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(200.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(id = vlcR.string.welcome_title),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )
            Text(
                text = stringResource(id = vlcR.string.welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
        }
    }
}

@Composable
private fun PermissionsPage(
    isVisible: Boolean,
    permissionGranted: Boolean,
    hasDeniedPermission: Boolean,
    selectedLevel: PermissionLevel,
    onLevelFocused: (PermissionLevel) -> Unit,
    onResetDenial: () -> Unit,
    onNext: () -> Unit,
    onRequestPermission: () -> Unit
) {
    if (hasDeniedPermission && !permissionGranted) {
        NoPermissionView(onResetDenial, onNext)
    } else {
        PermissionSelectionView(isVisible, selectedLevel, onLevelFocused, onRequestPermission)
    }
}

@Composable
private fun PermissionSelectionView(
    isVisible: Boolean,
    selectedLevel: PermissionLevel,
    onLevelFocused: (PermissionLevel) -> Unit,
    onRequestPermission: () -> Unit
) {
    val allFilesFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            allFilesFocusRequester.requestFocus()
        }
    }

    OnboardingPage {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(id = vlcR.string.permission),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = vlcR.string.permission_media),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PermissionOption(
                    icon = vlcR.drawable.ic_perm_none,
                    isSelected = selectedLevel == PermissionLevel.NONE,
                    onFocused = { onLevelFocused(PermissionLevel.NONE) },
                    onClick = onRequestPermission
                )
                Spacer(modifier = Modifier.width(24.dp))
                PermissionOption(
                    icon = vlcR.drawable.ic_perm_media,
                    isSelected = selectedLevel == PermissionLevel.MEDIA,
                    onFocused = { onLevelFocused(PermissionLevel.MEDIA) },
                    onClick = onRequestPermission
                )
                Spacer(modifier = Modifier.width(24.dp))
                PermissionOption(
                    icon = vlcR.drawable.ic_perm_all,
                    isSelected = selectedLevel == PermissionLevel.ALL,
                    onFocused = { onLevelFocused(PermissionLevel.ALL) },
                    onClick = onRequestPermission,
                    modifier = Modifier.focusRequester(allFilesFocusRequester)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val description = when (selectedLevel) {
                PermissionLevel.NONE -> vlcR.string.permission_onboarding_no_perm
                PermissionLevel.MEDIA -> vlcR.string.permission_onboarding_perm_media
                PermissionLevel.ALL -> vlcR.string.permission_onboarding_perm_all
            }

            Text(
                text = stringResource(id = description),
                style = MaterialTheme.typography.bodyMedium,
                color = Orange500,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(horizontal = 64.dp)
                    .height(48.dp)
            )
        }
    }
}

@Composable
private fun PermissionOption(
    modifier: Modifier = Modifier,
    icon: Int,
    isSelected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused || isSelected) 1.1f else 0.8f, label = "scale")
    val tint by animateColorAsState(if (isFocused || isSelected) Orange500 else Color.White, label = "tint")

    Box(
        modifier = modifier
            .scale(scale)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onKeyEvent {
                if (it.key == Key.DirectionCenter || it.key == Key.Enter) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(16.dp)
            .then(
                if (isFocused || isSelected) {
                    Modifier.border(2.dp, Orange500, RoundedCornerShape(8.dp))
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            colorFilter = ColorFilter.tint(tint)
        )
    }
}

@Composable
private fun NoPermissionView(onBackToSelection: () -> Unit, onNext: () -> Unit) {
    val askAgainFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        askAgainFocusRequester.requestFocus()
    }

    OnboardingPage(
        buttonText = vlcR.string.next,
        onButtonClick = onNext
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = vlcR.drawable.ic_onboarding_no_permission),
                contentDescription = null,
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(id = vlcR.string.permission_not_granted),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            OnboardingButton(
                text = vlcR.string.permission_ask_again,
                onClick = onBackToSelection,
                modifier = Modifier.focusRequester(askAgainFocusRequester)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(id = vlcR.string.permission_expanation_no_allow),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
            Text(
                text = stringResource(id = vlcR.string.permission_expanation_allow),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
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

@Preview(device = "id:tv_1080p")
@Composable
private fun WelcomePagePreview() {
    VlcPreview {
        WelcomePage(onNext = {})
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun NoPermissionViewPreview() {
    VlcPreview {
        NoPermissionView(onBackToSelection = {}, onNext = {})
    }
}

@Preview
@Composable
private fun OnboardingButtonPreview() {
    VlcPreview {
        OnboardingButton(text = vlcR.string.next, onClick = {})
    }
}
