package org.videolan.vlc

import android.app.Application
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.ILibVLCFactory
import org.videolan.libvlc.IMediaFactory
import org.videolan.libvlc.test.TestLibVLCFactory
import org.videolan.libvlc.test.TestMediaFactory
import org.videolan.medialibrary.MLServiceLocator

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class VLCTestApplication : Application() {
    init {
        MLServiceLocator.setLocatorMode(MLServiceLocator.LocatorMode.TESTS)
        FactoryManager.registerFactory(IMediaFactory.factoryId, TestMediaFactory())
        FactoryManager.registerFactory(ILibVLCFactory.factoryId, TestLibVLCFactory())
    }
}