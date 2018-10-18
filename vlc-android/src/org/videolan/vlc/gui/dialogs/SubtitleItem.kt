package org.videolan.vlc.gui.dialogs

data class SubtitleItem (
    val idSubtitle: String,
    val mediaPath: String,
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
