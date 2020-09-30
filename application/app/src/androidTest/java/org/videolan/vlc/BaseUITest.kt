package org.videolan.vlc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import androidx.test.runner.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.videolan.resources.util.startMedialibrary
import org.videolan.vlc.util.TestCoroutineContextProvider

@RunWith(AndroidJUnit4::class)
abstract class BaseUITest {
    @Rule
    @JvmField
    val storagePermissionGrant = GrantPermissionRule.grant(
            "android.permission.READ_EXTERNAL_STORAGE")

    val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun init() {
        context.startMedialibrary(coroutineContextProvider = TestCoroutineContextProvider())
        beforeTest()
    }

    abstract fun beforeTest()
}