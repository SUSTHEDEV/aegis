package com.susthedev.aegis

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable

typealias BeforeCallback = (XposedHelper.BeforeHookCallback) -> Unit
typealias AfterCallback = (XposedHelper.AfterHookCallback) -> Unit

/**
 * Thin helpers over the libxposed API (structure mirrors LSPosed/CorePatch, Apache-2.0).
 * All hook-side reads go through remote preferences so the manager app and the
 * system_server hooks share one state store.
 */
object XposedHelper {
    lateinit var xposedModule: XposedModule
        private set
    lateinit var hostClassLoader: ClassLoader
        private set
    val prefs by lazy { xposedModule.getRemotePreferences("aegis_conf") }

    fun onModuleLoaded(module: XposedModule) {
        xposedModule = module
        // bind the shared prefs seam so Config/RulesStore read hook-side remote prefs
        XposedPrefs.bind { prefs }
    }

    fun bindHost(classLoader: ClassLoader) {
        hostClassLoader = classLoader
    }

    class BeforeHookCallback(private val chain: XposedInterface.Chain) {
        val thisObject: Any? get() = chain.thisObject
        val args: Array<Any?> = chain.args.toTypedArray()
        private var skipped = false
        private var skipResult: Any? = null

        fun returnAndSkip(result: Any?) {
            skipped = true
            skipResult = result
        }

        fun isSkipped() = skipped
        fun getSkipResult() = skipResult
    }

    class AfterHookCallback(
        private val chain: XposedInterface.Chain,
        var result: Any?,
        var throwable: Throwable?,
    ) {
        val thisObject: Any? get() = chain.thisObject
        val args: Array<Any?> get() = chain.args.toTypedArray()
    }

    private class CustomHooker(
        val beforeCallback: BeforeCallback,
        val afterCallback: AfterCallback,
    ) : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            var result: Any? = null
            var throwable: Throwable? = null

            val bcb = BeforeHookCallback(chain)
            beforeCallback(bcb)
            if (bcb.isSkipped()) return bcb.getSkipResult()

            try {
                result = chain.proceed(bcb.args)
            } catch (t: Throwable) {
                throwable = t
            }

            val acb = AfterHookCallback(chain, result, throwable)
            afterCallback(acb)
            if (acb.throwable != null) throw acb.throwable!!
            return acb.result
        }
    }

    fun hookBefore(member: Executable, callback: BeforeCallback): XposedInterface.HookHandle =
        xposedModule.hook(member).intercept(CustomHooker(callback) {})

    fun hookAfter(member: Executable, callback: AfterCallback): XposedInterface.HookHandle =
        xposedModule.hook(member).intercept(CustomHooker({}) { callback(it) })

    /** Combined before+after in ONE hooker (double-hooking the same method is unreliable). */
    fun hookAround(
        member: Executable,
        beforeCallback: BeforeCallback,
        afterCallback: AfterCallback,
    ): XposedInterface.HookHandle =
        xposedModule.hook(member).intercept(CustomHooker(beforeCallback, afterCallback))

    fun log(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            xposedModule.log(Log.ERROR, "Aegis", message, throwable)
        } else {
            xposedModule.log(Log.INFO, "Aegis", message)
        }
    }

    fun findClassIfExists(name: String): Class<*>? = try {
        hostClassLoader.loadClass(name)
    } catch (_: ClassNotFoundException) {
        null
    }

    /** Force ART to deoptimize a method so hooks intercept inlined call sites (system framework!). */
    fun deoptimize(method: java.lang.reflect.Method): Boolean = try {
        xposedModule.deoptimize(method)
    } catch (t: Throwable) {
        log("deoptimize failed: ${method.name}", t)
        false
    }
}
