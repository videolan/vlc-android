package org.videolan.vlc

import android.app.Application
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.AppFactoryDelegate
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.interfaces.ILibVLCFactory
import org.videolan.libvlc.interfaces.IMediaFactory
import org.videolan.libvlc.stubs.StubLibVLCFactory
import org.videolan.libvlc.stubs.StubMediaFactory
import org.videolan.medialibrary.MLServiceLocator

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class VLCTestApplication : Application(), AppFactoryDelegate {
    init {
        MLServiceLocator.setLocatorMode(MLServiceLocator.LocatorMode.TESTS)
        FactoryManager.registerFactory(IMediaFactory.factoryId, StubMediaFactory())
        FactoryManager.registerFactory(ILibVLCFactory.factoryId, StubLibVLCFactory())
    }

    override fun getMediaFactory(): IMediaFactory {
        try {
            return FactoryManager.getFactory(IMediaFactory.factoryId) as IMediaFactory
        } catch (e: Exception) {
            FactoryManager.registerFactory(IMediaFactory.factoryId, StubMediaFactory())
        }
        return FactoryManager.getFactory(IMediaFactory.factoryId) as IMediaFactory
    }

    override fun getLibVLCFactory(): ILibVLCFactory {
        try {
            return FactoryManager.getFactory(ILibVLCFactory.factoryId) as ILibVLCFactory
        } catch (e: Exception) {
            FactoryManager.registerFactory(ILibVLCFactory.factoryId, StubLibVLCFactory())
        }
        return FactoryManager.getFactory(ILibVLCFactory.factoryId) as ILibVLCFactory
    }
}