package com.susthedev.aegis

import android.content.SharedPreferences

/** Shared accessor for the remote prefs seam (both sides bind into this). */
object XposedPrefs {
    @Volatile
    private var provider: (() -> SharedPreferences?)? = null
    private val listeners = mutableListOf<() -> Unit>()

    /** Called once per side: hooks bind XposedHelper.prefs, the app binds App.rwPrefs. */
    fun bind(p: () -> SharedPreferences?) {
        provider = p
        val activeListeners = synchronized(listeners) {
            val list = listeners.toList()
            listeners.clear()
            list
        }
        activeListeners.forEach { runCatching { it() } }
    }

    fun addListener(listener: () -> Unit) {
        if (getOrNull() != null) {
            runCatching { listener() }
        } else {
            synchronized(listeners) {
                listeners.add(listener)
            }
        }
    }

    fun getOrNull(): SharedPreferences? = try {
        provider?.invoke()
    } catch (_: Throwable) {
        null
    }
}
