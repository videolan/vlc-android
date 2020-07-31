package org.videolan.tools

fun getMediaDescription(artist: String?, album: String?): String {
    val hasArtist = !artist.isNullOrEmpty()
    val hasAlbum = !album.isNullOrEmpty()
    if (!hasAlbum && !hasArtist) return ""
    val contentBuilder = StringBuilder(if (hasArtist) artist!! else "")
    if (hasArtist && hasAlbum) contentBuilder.append(" - ")
    if (hasAlbum) contentBuilder.append(album)
    return contentBuilder.toString()
}