package com.susthedev.aegis.hook

import com.susthedev.aegis.XposedHelper

abstract class BaseHook {
    abstract val name: String

    abstract fun hook()

    fun init() {
        try {
            hook()
            XposedHelper.log("Aegis: $name installed")
        } catch (t: Throwable) {
            throw RuntimeException("Aegis: $name init error", t)
        }
    }
}
