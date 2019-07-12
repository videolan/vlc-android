package org.videolan.vlc.viewmodels.mobile

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.vlc.BaseTest

import org.junit.Assert.*
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.stubs.StubDataSource

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class PlaylistViewModelTest : BaseTest() {
    private lateinit var playlistViewModel: PlaylistViewModel

    override fun beforeTest() {
        super.beforeTest()
        StubDataSource.getInstance().resetData()
    }

    internal fun setupViewModel(parent: MediaLibraryItem) {
        playlistViewModel = PlaylistViewModel(context, application, parent)
    }


}