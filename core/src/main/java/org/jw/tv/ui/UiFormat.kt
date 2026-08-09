package org.jw.tv.ui

/**
 * Shared duration formatting ("h:mm:ss" / "mm:ss") used by both the TV and
 * phone UIs. Lives in :core, same package as before, so TV call sites compile
 * unchanged.
 */
fun formatDuration(durationSeconds: Double?): String {
    if (durationSeconds == null || durationSeconds <= 0) return ""
    val total = durationSeconds.toInt()
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}
