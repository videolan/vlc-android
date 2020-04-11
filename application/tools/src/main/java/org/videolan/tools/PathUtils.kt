package org.videolan.tools

/**
 *  Checks if the specified (sanitized) path is contained in this collection (of sanitized paths)
 */
fun List<String>.containsPath(path: String) = any { it.sanitizePath() == path.sanitizePath() }

/**
 *  Checks if the specified (sanitized) path is contained in this array (of sanitized paths)
 */
fun Array<String>.containsPath(path: String) = any { it.sanitizePath() == path.sanitizePath() }

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
