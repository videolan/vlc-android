package org.videolan.vlc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import androidx.test.runner.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.videolan.medialibrary.MLServiceLocator


@RunWith(AndroidJUnit4::class)
abstract class BaseUITest {
    @Rule
    @JvmField
    val storagePermissionGrant = GrantPermissionRule.grant(
            "android.permission.READ_EXTERNAL_STORAGE")


    val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun init() {
        context.startMedialibrary()
        beforeTest()
    }

    abstract fun beforeTest()
}