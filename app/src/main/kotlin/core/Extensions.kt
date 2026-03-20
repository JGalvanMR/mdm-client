package com.mdm.client.core

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Context ───────────────────────────────────────────────────────────────────
fun Context.sendLocalBroadcast(action: String, configure: Intent.() -> Unit = {}) {
    val intent = Intent(action).apply {
        setPackage(packageName)
        configure()
    }
    sendBroadcast(intent)
}

// ── Logging con nivel ─────────────────────────────────────────────────────────
object MdmLog {
    private const val MAX_TAG = 23
    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun i(tag: String, msg: String) { Log.i(tag.take(MAX_TAG), msg) }
    fun d(tag: String, msg: String) { Log.d(tag.take(MAX_TAG), msg) }
    fun w(tag: String, msg: String) { Log.w(tag.take(MAX_TAG), msg) }
    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.e(tag.take(MAX_TAG), msg, t)
        else Log.e(tag.take(MAX_TAG), msg)
    }

    fun timestamp(): String = sdf.format(Date())
    fun formatted(level: String, tag: String, msg: String) =
        "[${timestamp()}] $level/$tag: $msg"
}

// ── Strings ───────────────────────────────────────────────────────────────────
fun Long.toReadableDate(): String {
    val sdf = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(this))
}

fun String.truncate(maxLength: Int, ellipsis: String = "…"): String =
    if (length <= maxLength) this else take(maxLength) + ellipsis