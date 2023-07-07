package org.videolan.vlc.gui.helpers


import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.ACTION_DISCOVER
import org.videolan.resources.ACTION_DISCOVER_DEVICE
import org.videolan.resources.AppContextProvider
import org.videolan.resources.EXTRA_PATH
import org.videolan.resources.util.launchForeground
import org.videolan.tools.runIO
import org.videolan.tools.stripTrailingSlash
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.util.encodeMrlWithTrailingSlash

object MedialibraryUtils {

    fun removeDir(path: String) {
        runIO(Runnable { Medialibrary.getInstance().removeFolder(path) })
    }

    fun banDir(path: String) {
        Medialibrary.getInstance().banFolder(path)
    }

    @JvmOverloads
    fun addDir(path: String, context: Context = AppContextProvider.appContext) {
        val intent = Intent(ACTION_DISCOVER, null, context, MediaParsingService::class.java)
        intent.putExtra(EXTRA_PATH, path)
        context.launchForeground(intent)
    }

    fun addDevice(path: String, context: Context) {
        val intent = Intent(ACTION_DISCOVER_DEVICE, null, context, MediaParsingService::class.java)
        intent.putExtra(EXTRA_PATH, path)
        context.launchForeground(intent)
    }

    fun isScanned(path: String): Boolean {
        //scheme is supported => test if the parent is scanned
        var isScanned = false
        Medialibrary.getInstance().foldersList.forEach search@{
            if (path.stripTrailingSlash().startsWith(it.toUri().toString().stripTrailingSlash())) {
                isScanned = true
                return@search
            }
        }
        return isScanned
    }
    
    /**
     * Return true if this uri/path is banned (because it is or a parent is)
     * @param uri the uri to test
     * return true is the uri is banned
     */
    fun isBanned(uri: Uri, bannedFolders: List<String>) = isBanned(uri.toString(), bannedFolders)
    fun isBanned(path: String, bannedFolders: List<String>) = bannedFolders.any { Tools.mlEncodeMrl(path).encodeMrlWithTrailingSlash().startsWith("$it") }

    /**
     * Return true if this uri/path is banned (but false if a parent is)
     * @param uri the uri to test
     * return true is the uri is strictly banned
     */
    fun isStrictlyBanned(uri: Uri, bannedFolders: List<String>) = isStrictlyBanned(uri.toString(), bannedFolders)
    fun isStrictlyBanned(path: String, bannedFolders: List<String>) = bannedFolders.any { Tools.mlEncodeMrl(path).encodeMrlWithTrailingSlash() == "$it" }
}
