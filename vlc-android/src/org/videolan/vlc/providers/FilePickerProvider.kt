package org.videolan.vlc.providers

import org.videolan.libvlc.util.MediaBrowser
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.util.LiveDataset


class FilePickerProvider(dataset: LiveDataset<MediaLibraryItem>, url: String?) : FileBrowserProvider(dataset, url, true, false) {

    override fun getFlags(): Int {
        return MediaBrowser.Flag.Interact or MediaBrowser.Flag.NoSlavesAutodetect
    }

    override fun initBrowser() {
        super.initBrowser()
        mediabrowser.setIgnoreFileTypes("db,nfo,ini,jpg,jpeg,ljpg,gif,png,pgm,pgmyuv,pbm,pam,tga,bmp,pnm,xpm,xcf,pcx,tif,tiff,lbm,sfv")
    }

    override fun addMedia(media: MediaLibraryItem) {
        if (media is MediaWrapper && media.type == MediaWrapper.TYPE_SUBTITLE) super.addMedia(media)
    }
}