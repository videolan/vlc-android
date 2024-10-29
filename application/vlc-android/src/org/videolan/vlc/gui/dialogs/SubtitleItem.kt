package org.videolan.vlc.gui.dialogs

import android.net.Uri
import org.videolan.tools.readableNumber

data class SubtitleItem(
    val idSubtitle: String,
    val mediaUri: Uri,
    val subLanguageID: String,
    val movieReleaseName: String,
    val state: State,
    val zipDownloadLink: String,
    val hearingImpaired: Boolean,
    val rating: Int,
    val downloadNumber: Long
) {
    fun getReadableDownloadNumber() = downloadNumber.readableNumber()
}

enum class State {
    Downloading,
    Downloaded,
    NotDownloaded
}
