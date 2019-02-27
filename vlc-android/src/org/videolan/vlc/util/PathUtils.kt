package org.videolan.vlc.util

/**
 *  Checks if the specified (sanitized) path is contained in this collection (of sanitized pathes)
 */
fun List<String>.containsPath(path: String): Boolean {
    for (string in this) {
        if (string.sanitizePath() == path.sanitizePath()) {
            return true
        }
    }
    return false
}

/**
 *  Checks if the specified (sanitized) path is contained in this array (of sanitized pathes)
 */
fun Array<String>.containsPath(path: String): Boolean {

    return toList().containsPath(path)
}

/**
 * Sanitize a path [String] to remove leading "file://" and trailing "/"
 */
fun String.sanitizePath(): String {
    var result = this
    if (result.endsWith('/')) {
        result = result.substringBeforeLast("/")
    }
    if (result.startsWith("file://")) {
        result = result.substring(7)
    }
    return result
}
