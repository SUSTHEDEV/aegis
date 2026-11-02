package com.susthedev.aegis.hook

import android.content.pm.PermissionInfo
import com.susthedev.aegis.AegisPolicy
import com.susthedev.aegis.XposedHelper

/**
 * The core mechanism, blind: every signature-tier permission definition is widened with
 * PROTECTION_FLAG_DEVELOPMENT, making it adb-grantable ("pm grant"). No policy, no tiers —
 * callers own the checking.
 *
 * Fixes baked in (Android 16/17 access permission framework):
 *  - BOTH definition stores are covered: legacy (services/core pm.permission.Permission)
 *    and modern (services/permission access.permission.Permission).
 *  - The modern model derives getProtectionFlags() from PermissionInfo#protectionLevel,
 *    so widening protectionLevel is sufficient for both consumers.
 *  - DECISIVE: definitions are re-stamped inside PermissionService#setRuntimePermissionGranted
 *    — the exact site that throws "not a changeable permission type" — because definition
 *    maps are rebuilt/reloaded during the session and lose constructor-time mutations.
 *    That method is a member-extension Kotlin fun: 10 compiled params (extension receiver
 *    first), permissionName at args[3].
 *  - AppIdPermissionPolicy#getPermissions is a trivial getter that ART inlines at optimized
 *    call sites; it is hooked AND deoptimized so the read path always hits the hook.
 *  - Per-section registration statuses (decisiveState) are surfaced for runtime diagnosis,
 *    since init-time logs are lost during system_server startup.
 */
object PermissionDevelopmentHook : BaseHook() {
    override val name = "PermissionDevelopmentHook"

    /** Diagnostic: decisive re-stamp fires. */
    @JvmStatic var restampFires: Int = 0

    /** Diagnostic: per-section registration status, visible through the runtime gate log. */
    @JvmStatic var decisiveState: String = "not-run"

    private fun isSignature(info: PermissionInfo): Boolean =
        (info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) ==
            PermissionInfo.PROTECTION_SIGNATURE

    private fun widen(info: PermissionInfo) {
        if (!AegisPolicy.DEFINITION_STAMPING) return // per-pair model: no definition-wide stamping
        if (!isSignature(info)) return // the development flag only matters in the signature tier
        info.protectionLevel = info.protectionLevel or PermissionInfo.PROTECTION_FLAG_DEVELOPMENT
    }

    override fun hook() {
        val piClass = PermissionInfo::class.java
        val modernPermClass = XposedHelper.hostClassLoader
            .loadClass("com.android.server.permission.access.permission.Permission")
        val statuses = StringBuilder()

        // ---- SECTION 1: decisive grant-check re-stamp ----
        runCatching {
            val psClass = XposedHelper.hostClassLoader
                .loadClass("com.android.server.permission.access.permission.PermissionService")
            val candidates = psClass.declaredMethods.filter { it.name == "setRuntimePermissionGranted" }
            val srpg = candidates.first { it.parameterTypes.size == 10 } // extension receiver + 9 declared
            val serviceF = psClass.getDeclaredField("service").apply { isAccessible = true }
            val stateF = serviceF.type.getDeclaredField("state").apply { isAccessible = true }
            val infoF = modernPermClass.getDeclaredField("permissionInfo").apply { isAccessible = true }
            val levelF = piClass.getDeclaredField("protectionLevel").apply { isAccessible = true }

            fun restampViaState(thisObject: Any, permName: String): Boolean {
                val svc = serviceF.get(thisObject) ?: return false
                val state = stateF.get(svc) ?: return false
                val sys = state.javaClass.getMethod("getSystemState").invoke(state)
                val map = sys.javaClass.getMethod("getPermissions").invoke(sys)
                val getM = map.javaClass.methods.first { m -> m.name == "get" && m.parameterCount == 1 }
                val perm = getM.invoke(map, permName) ?: return false
                val info = infoF.get(perm) as? PermissionInfo ?: return false
                if (!isSignature(info)) return false
                levelF.setInt(info, info.protectionLevel or PermissionInfo.PROTECTION_FLAG_DEVELOPMENT)
                return true
            }

            XposedHelper.hookBefore(srpg) { cb ->
                val permName = cb.args.getOrNull(3) as? String ?: return@hookBefore
                val pkgState = cb.args.getOrNull(1)
                val pkg = pkgState?.javaClass?.getMethod("getPackageName")?.invoke(pkgState) as? String
                    ?: return@hookBefore
                // per-pair stamping: only stamp when the checker approves the pair
                if (AegisPolicy.decide(pkg, permName) != AegisPolicy.Decision.APPROVED) {
                    return@hookBefore
                }
                if (restampViaState(cb.thisObject!!, permName)) {
                    restampFires++
                    XposedHelper.log(
                        "Aegis [re-stamp] decisive: $pkg / $permName ok=true statusHook=${PermissionStatusHook.decisiveState}"
                    )
                }
            }
            statuses.append("decisiveOK ")
        }.onFailure {
            statuses.append("decisiveFail:${it.javaClass.simpleName}:${it.message} ")
            decisiveState = statuses.toString()
        }

        // ---- SECTION 2: getPermissions read-path re-stamp ----
        runCatching {
            val policyClass = XposedHelper.hostClassLoader
                .loadClass("com.android.server.permission.access.permission.AppIdPermissionPolicy")
            val getPerms = policyClass.declaredMethods.first {
                it.name == "getPermissions" && it.parameterTypes.size == 1
            }
            val infoF = modernPermClass.getDeclaredField("permissionInfo").apply { isAccessible = true }
            val levelF = piClass.getDeclaredField("protectionLevel").apply { isAccessible = true }
            XposedHelper.hookAfter(getPerms) { cb ->
                if (PermissionStatusHook.denyAll) return@hookAfter
                val map = cb.result ?: return@hookAfter
                val keys = map.javaClass.getMethod("keys").invoke(map) as? Set<*> ?: return@hookAfter
                val getM = map.javaClass.methods.first { m -> m.name == "get" && m.parameterCount == 1 }
                for (k in keys) {
                    if (k == null) continue
                    val perm = getM.invoke(map, k) ?: continue
                    val info = infoF.get(perm) as? PermissionInfo ?: continue
                    if (!isSignature(info)) continue
                    levelF.setInt(info, info.protectionLevel or PermissionInfo.PROTECTION_FLAG_DEVELOPMENT)
                }
            }
            // trivial getter — ART inlines it at optimized call sites; force callers through us
            XposedHelper.deoptimize(getPerms)
            statuses.append("restampOK ")
        }.onFailure {
            statuses.append("restampFail:${it.javaClass.simpleName}:${it.message} ")
            decisiveState = statuses.toString()
        }

        // ---- SECTION 3: legacy store constructor stamping ----
        runCatching {
            val legacyPermClass = XposedHelper.hostClassLoader
                .loadClass("com.android.server.pm.permission.Permission")
            legacyPermClass.declaredConstructors
                .filter { it.parameterTypes.contains(piClass) }
                .forEach { ctor ->
                    XposedHelper.hookAfter(ctor) { cb ->
                        val info = cb.args[ctor.parameterTypes.indexOf(piClass)] as PermissionInfo
                        widen(info)
                    }
                }
            statuses.append("legacyOK ")
        }.onFailure {
            statuses.append("legacyFail:${it.javaClass.simpleName}:${it.message} ")
            decisiveState = statuses.toString()
        }

        // ---- SECTION 4: modern store constructor stamping ----
        if (AegisPolicy.DEFINITION_STAMPING) runCatching {
            modernPermClass.declaredConstructors
                .filter { it.parameterTypes.contains(piClass) }
                .forEach { ctor ->
                    XposedHelper.hookAfter(ctor) { cb ->
                        val idx = ctor.parameterTypes.indexOf(piClass)
                        (cb.args[idx] as? PermissionInfo)?.let { widen(it) }
                    }
                }
            statuses.append("modernOK")
        }.onFailure {
            statuses.append("modernFail:${it.javaClass.simpleName}:${it.message}")
        }

        decisiveState = statuses.toString()
    }
}
