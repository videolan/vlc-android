/*****************************************************************************
 * OtgAccess.kt
 *****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.gui.helpers.hf

import android.annotation.TargetApi
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import videolan.org.commontools.LiveEvent

const val SAF_REQUEST = 85
const val TAG = "OtgAccess"

const val OTG_CONTENT_AUTHORITY = "com.android.externalstorage.documents"
const val OTG_SCHEME = "otg"

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class OtgAccess : BaseHeadlessFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val safIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        try {
            startActivityForResult(safIntent, SAF_REQUEST)
        } catch (e: ActivityNotFoundException) {
            exit()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (intent != null && requestCode == SAF_REQUEST) (otgRoot as LiveEvent).value = intent.data
        else super.onActivityResult(requestCode, resultCode, intent)
        exit()
    }

    companion object {
        fun requestOtgRoot(activity: FragmentActivity?) {
            activity?.supportFragmentManager?.beginTransaction()?.add(OtgAccess(), TAG)?.commitAllowingStateLoss()
        }
        var otgRoot : LiveData<Uri> = LiveEvent()
    }
}

@WorkerThread
fun getDocumentFiles(context: Context, path: String) : List<AbstractMediaWrapper>? {
    val rootUri = OtgAccess.otgRoot.value ?: return null
//    else Uri.Builder().scheme("content")
//            .authority(OTG_CONTENT_AUTHORITY)
//            .path(path.substringBefore(':'))
//            .build()
    var documentFile = DocumentFile.fromTreeUri(context, rootUri)

    val parts = path.substringAfterLast(':').split("/".toRegex()).dropLastWhile { it.isEmpty() }
    for (part in parts) {
        if (part == "") continue
        documentFile = documentFile?.findFile(part)
    }

    if (documentFile == null) {
        Log.w(TAG, "Failed to find file")
        return null
    }

    // we have the end point DocumentFile, list the files inside it and return
    val list = mutableListOf<AbstractMediaWrapper>()
    for (file in documentFile.listFiles()) {
        if (file.exists() && file.canRead()) {
            if (file.name?.startsWith(".") == true) continue
            val mw = MLServiceLocator.getAbstractMediaWrapper(file.uri).apply {
                type = when {
                    file.isDirectory -> AbstractMediaWrapper.TYPE_DIR
                    file.type?.startsWith("video") == true -> AbstractMediaWrapper.TYPE_VIDEO
                    file.type?.startsWith("audio") == true -> AbstractMediaWrapper.TYPE_AUDIO
                    else -> type
                }
                title = file.name
            }
            list.add(mw)
        }
    }
    return list
}