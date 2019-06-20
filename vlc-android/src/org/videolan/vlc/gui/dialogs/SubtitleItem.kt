package org.videolan.vlc.gui.dialogs

import android.net.Uri

data class SubtitleItem (
        val idSubtitle: String,
        val mediaUri: Uri,
        val subLanguageID: String,
        val movieReleaseName: String,
        val state: State,
        val zipDownloadLink: String
)

enum class State {
    Downloading,
    Downloaded,
    NotDownloaded
}
