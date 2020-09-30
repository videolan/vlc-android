package org.videolan.vlc

import android.os.Bundle
import androidx.multidex.MultiDex
import androidx.test.runner.AndroidJUnitRunner

class MultidexTestRunner: AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        MultiDex.install(targetContext)
        super.onCreate(arguments)
    }
}
