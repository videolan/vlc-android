package org.videolan.vlc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import androidx.test.runner.AndroidJUnit4
import org.junit.Rule
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
open class BaseUITest {
    @Rule
    @JvmField
    val storagePermissionGrant = GrantPermissionRule.grant(
            "android.permission.READ_EXTERNAL_STORAGE")


    val context: Context = ApplicationProvider.getApplicationContext()
}