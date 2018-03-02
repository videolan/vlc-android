package org.videolan.vlc.viewmodels

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import org.videolan.libvlc.util.MediaBrowser


class FilePickerProvider(url: String?) : FileBrowserProvider(url, true) {

    override fun getFlags(): Int {
        return MediaBrowser.Flag.Interact or MediaBrowser.Flag.NoSlavesAutodetect
    }

    override fun initBrowser(listener: MediaBrowser.EventListener) {
        super.initBrowser(listener)
        mediabrowser?.setIgnoreFileTypes("db,nfo,ini,jpg,jpeg,ljpg,gif,png,pgm,pgmyuv,pbm,pam,tga,bmp,pnm,xpm,xcf,pcx,tif,tiff,lbm,sfv");
    }
    class Factory(val url: String?): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return FilePickerProvider(url) as T
        }
    }
}