package com.susthedev.aegis

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import com.susthedev.aegis.hook.PermissionDevelopmentHook
import com.susthedev.aegis.hook.PermissionStatusHook

/**
 * libxposed entrypoint. Scope: android (system_server).
 * Blind core mechanism: all signature-tier permissions become adb-grantable,
 * and grant/revoke dispatches report custom statuses.
 */
class XposedMain : XposedModule() {

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        super.onModuleLoaded(param)
        XposedHelper.onModuleLoaded(this)
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        super.onSystemServerStarting(param)
        XposedHelper.bindHost(param.classLoader)

        val hooks = listOf(
            PermissionDevelopmentHook,
            PermissionStatusHook,
        )
        hooks.forEach { h ->
            try {
                h.init()
            } catch (t: Throwable) {
                XposedHelper.log("Aegis: hook init FAILED: ${h.name}", t)
            }
        }
    }
}
