/*
 * ************************************************************************
 *  OnboardingActivity.kt
 * *************************************************************************
 * Copyright © 2021 VLC authors and VideoLAN
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
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.videolan.resources.TV_MAIN_ACTIVITY
import org.videolan.television.ui.compose.composable.screens.OnboardingScreen
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.tools.KEY_TV_ONBOARDING_DONE
import org.videolan.tools.Settings
import org.videolan.tools.putSingle

class OnboardingActivity : ComponentActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VlcTVTheme {
                OnboardingScreen(onFinish = {
                    Settings.getInstance(this).putSingle(KEY_TV_ONBOARDING_DONE, true)
                    finish()
                    val intent = Intent(Intent.ACTION_VIEW).setClassName(this, TV_MAIN_ACTIVITY)
                    startActivity(intent)
                })
            }
        }
    }
}
