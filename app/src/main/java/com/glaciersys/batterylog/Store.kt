package com.glaciersys.batterylog

import android.content.Context
import java.io.File

object Store {
    const val ACTION_UPDATED = "com.glaciersys.batterylog.UPDATED"
    private const val FILE = "log.txt"
    private const val MAX_LINES = 3000

    data class Entry(val ts: Long, val from: Int, val to: Int, val charging: Boolean)

    /** Appends a line only when the level differs from the last recorded one. Returns true if written. */
    @Synchronized
    fun record(ctx: Context, level: Int, charging: Boolean): Boolean {
        val prefs = ctx.getSharedPreferences("state", Context.MODE_PRIVATE)
        val last = prefs.getInt("last", -1)
        if (last == level) return false
        prefs.edit().putInt("last", level).apply()

        val from = if (last < 0) level else last
        val f = File(ctx.filesDir, FILE)
        f.appendText("${System.currentTimeMillis()},$from,$level,${if (charging) 1 else 0}\n")

        if (f.length() > 200_000) {
            val lines = f.readLines()
            if (lines.size > MAX_LINES) f.writeText(lines.takeLast(MAX_LINES).joinToString("\n") + "\n")
        }
        return true
    }

    /** Newest first. */
    @Synchronized
    fun readAll(ctx: Context): List<Entry> {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { line ->
            val p = line.split(",")
            if (p.size < 4) null
            else try {
                Entry(p[0].toLong(), p[1].toInt(), p[2].toInt(), p[3] == "1")
            } catch (e: Exception) { null }
        }.asReversed()
    }

    @Synchronized
    fun clear(ctx: Context) {
        File(ctx.filesDir, FILE).delete()
        ctx.getSharedPreferences("state", Context.MODE_PRIVATE).edit().remove("last").apply()
    }
}
