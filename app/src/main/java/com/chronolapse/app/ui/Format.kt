package com.chronolapse.app.ui

import java.util.Locale

/** "2s", "1m30s", "1h5m" - free-form interval display */
fun fmtInterval(ms: Int): String {
    if (ms < 1_000) return "${ms}ms"
    val s = ms / 1000
    val h = s / 3600
    val m = s % 3600 / 60
    val sec = s % 60
    return buildString {
        if (h > 0) append(h).append('h')
        if (m > 0) append(m).append('m')
        if (sec > 0 || (h == 0 && m == 0)) append(sec).append('s')
    }
}

/** "0.8s" below ten seconds, "m:ss" above, "h:mm:ss" beyond an hour */
fun fmtDuration(ms: Long): String = when {
    ms < 500 -> "0s"
    ms < 9_950 -> String.format(Locale.US, "%.1fs", ms / 1000.0)
    else -> {
        val s = ms / 1000
        if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60)
        else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }
}

/** REC clock: HH:MM:SS */
fun fmtElapsed(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60)
}

/** "0:42" */
fun fmtClock(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}
