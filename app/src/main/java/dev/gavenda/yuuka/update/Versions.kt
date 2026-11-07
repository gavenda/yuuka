package dev.gavenda.yuuka.update

/**
 * Whether the release tagged [tag] (`v5.1.0`) is newer than [current] (`5.0.0`, the build's `versionName`).
 * Compared part by part as numbers, so 5.10.0 is newer than 5.9.0; a missing part counts as zero. A tag that
 * is not a version at all is never newer.
 */
fun isNewerVersion(tag: String, current: String): Boolean {
    val release = versionParts(tag) ?: return false
    val installed = versionParts(current) ?: return true
    for (i in 0 until maxOf(release.size, installed.size)) {
        val a = release.getOrElse(i) { 0 }
        val b = installed.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}

private fun versionParts(version: String): List<Int>? =
    version.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }
