package com.nothingx.bluetooth.log

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Drop-in replacement for [android.util.Log] (same method names/signatures,
 * swapped in at call sites via `import ... as Log`) that also appends every
 * line to a file in app-internal storage. Exists so a relay bug can be
 * captured and handed back without the reporter needing `adb` set up at
 * all — [init] from the phone app's entry points ([com.nothingx.phone.MainActivity],
 * [com.nothingx.phone.relay.PhoneRelayService], [com.nothingx.phone.relay.AutoRelayReceiver])
 * turns on file persistence; call sites elsewhere (the watch side) that
 * never call [init] just get normal logcat behavior, unaffected.
 *
 * Not used on the watch side by request — this is specifically for
 * diagnosing the phone relay, where the failure lives in a background
 * service (`PhoneRelayService`) a non-technical reporter can't easily
 * attach `adb logcat` to mid-repro.
 */
object NothingXLog {
    const val DEBUG = Log.DEBUG

    private const val FILE_NAME = "nothingx_log.txt"
    private const val MAX_BYTES = 1_000_000L // trimmed to half once exceeded, on init
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile private var logFile: File? = null

    fun init(context: Context) {
        if (logFile != null) return
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        if (file.exists() && file.length() > MAX_BYTES) trim(file)
        logFile = file
    }

    /** The exportable log file, once [init] has been called — null otherwise. */
    fun currentFile(): File? = logFile

    fun isLoggable(tag: String, level: Int): Boolean = Log.isLoggable(tag, level)

    fun v(tag: String, msg: String) {
        Log.v(tag, msg)
        write("V", tag, msg)
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        write("D", tag, msg)
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        write("I", tag, msg)
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        write("W", tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable) {
        Log.w(tag, msg, tr)
        write("W", tag, "$msg — ${tr}")
    }

    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
        write("E", tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable) {
        Log.e(tag, msg, tr)
        write("E", tag, "$msg — ${tr}")
    }

    private fun write(level: String, tag: String, msg: String) {
        val file = logFile ?: return
        try {
            FileWriter(file, true).use { it.appendLine("${timeFormat.format(Date())} $level $tag: $msg") }
        } catch (e: IOException) {
            // Losing a line to disk pressure shouldn't crash anything.
        }
    }

    private fun trim(file: File) {
        try {
            val lines = file.readLines()
            file.writeText(lines.takeLast(lines.size / 2).joinToString("\n") + "\n")
        } catch (e: IOException) {
            // Best-effort.
        }
    }
}
