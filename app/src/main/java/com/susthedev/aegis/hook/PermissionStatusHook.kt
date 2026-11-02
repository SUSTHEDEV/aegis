package com.susthedev.aegis.hook

import com.susthedev.aegis.AegisPolicy
import com.susthedev.aegis.XposedHelper
import com.susthedev.aegis.XposedPrefs

/**
 * Status reporting + policy gate for grant/revoke dispatch.
 *
 * ONE combined hooker on the PermissionManagerService delegator (all grant/revoke paths
 * funnel through it):
 *   - BEFORE (policy gate): decision checked against AegisPolicy (remote preferences / overlay).
 *   - AFTER (status capture): records dispatch outcome and returns custom status
 *     through terminal, logcat, and remote prefs.
 */
object PermissionStatusHook : BaseHook() {
    override val name = "PermissionStatusHook"

    private const val STATUS_KEY = "last_status"

    /** Last dispatch outcome, captured by the after-phase. */
    @JvmStatic @Volatile var lastStatus: String? = null

    /** Diagnostic: per-section registration status, visible through the decisive log. */
    @JvmStatic @Volatile var decisiveState: String = "not-run"

    /** THE decision override: false so policy decisions (AegisPolicy) determine outcome. */
    @JvmStatic @Volatile var denyAll: Boolean = false

    override fun hook() {
        val pms = XposedHelper.hostClassLoader
            .loadClass("com.android.server.pm.permission.PermissionManagerService")

        val grant = pms.declaredMethods.first {
            it.name == "grantRuntimePermission" && it.parameterTypes.size == 4
        }
        val revoke = pms.declaredMethods.first {
            it.name == "revokeRuntimePermission" && it.parameterTypes.size == 5
        }

        val shellPrinted = { shell: Any, status: String ->
            runCatching {
                val out = shell.javaClass.getMethod("getOutPrintWriter").invoke(shell)
                out.javaClass.getMethod("println", String::class.java)
                    .invoke(out, "[AEGIS] $status")
                true
            }.getOrDefault(false)
        }

        // ---- POLICY GATE + DISCOVERY PASSTHROUGH ----
        runCatching {
            val modernSvcClass = XposedHelper.hostClassLoader
                .loadClass("com.android.server.permission.access.permission.PermissionService")
            val grantKt = modernSvcClass.declaredMethods.first {
                it.name == "grantRuntimePermission" && it.parameterTypes.size == 4
            }
            XposedHelper.hookBefore(grantKt) { cb ->
                val pkg = cb.args.getOrNull(0) as? String ?: return@hookBefore
                val perm = cb.args.getOrNull(1) as? String ?: return@hookBefore
                val user = cb.args.getOrNull(3) as? Int ?: -1

                // discovery passthrough: no tag in the app's manifest -> not ours to judge
                if (!Discovery.declaredPerm(pkg, perm, user)) return@hookBefore

                val decision = AegisPolicy.decide(pkg, perm)
                if (decision == AegisPolicy.Decision.APPROVED) return@hookBefore // let it run

                XposedHelper.log("Aegis [POLICY DENIED]: GRANT $pkg / $perm @u$user decision=$decision")
                throw SecurityException("AEGIS|GRANT|$pkg|$perm|u$user|DENIED:$decision")
            }
        }.onFailure {
            XposedHelper.log("Aegis: policy gate hook failed", it)
        }

        // ---- combined hooker: STATUS CAPTURE (after) on the legacy delegator ----
        XposedHelper.hookAround(
            grant,
            beforeCallback = { },
            afterCallback = { cb ->
                val pkg = cb.args.getOrNull(0) as? String ?: return@hookAround
                val perm = cb.args.getOrNull(1) as? String ?: return@hookAround
                val user = cb.args.getOrNull(3) as? Int ?: -1
                val status = if (cb.throwable == null) "SUCCESS"
                else "FAILED:${cb.throwable?.message ?: "error"}"
                lastStatus = "GRANT|$pkg|$perm|u$user|$status"
                XposedHelper.log("Aegis [STATUS] $lastStatus")
                runCatching {
                    XposedPrefs.getOrNull()?.edit()?.putString(STATUS_KEY, lastStatus ?: "")?.apply()
                }
                if (denyAll && cb.throwable != null) {
                    cb.throwable = null
                    cb.result = null
                }
            },
        )

        XposedHelper.hookAfter(revoke) { cb ->
            val pkg = cb.args.getOrNull(0) as? String ?: return@hookAfter
            val perm = cb.args.getOrNull(1) as? String ?: return@hookAfter
            val user = cb.args.getOrNull(3) as? Int ?: -1
            val status = if (cb.throwable == null) "SUCCESS"
            else "FAILED:${cb.throwable?.message ?: "error"}"
            lastStatus = "REVOKE|$pkg|$perm|u$user|$status"
            XposedHelper.log("Aegis [STATUS] $lastStatus")

            if (cb.throwable == null) {
                // Successful revoke -> clear rule only if not explicitly DENIED by user
                val currentRule = AegisPolicy.getRule(pkg, perm)
                if (currentRule != AegisPolicy.Decision.DENIED) {
                    AegisPolicy.clearRule(pkg, perm)
                    XposedHelper.log("Aegis [REVOKE]: rule entry cleared for $pkg / $perm")
                } else {
                    XposedHelper.log("Aegis [REVOKE]: preserving explicit DENIED rule for $pkg / $perm")
                }
            }

            runCatching {
                XposedPrefs.getOrNull()?.edit()?.putString(STATUS_KEY, lastStatus ?: "")?.apply()
            }
            if (denyAll && cb.throwable != null) {
                cb.throwable = null
                cb.result = null
            }
        }

        // ---- terminal output: print captured statuses into the shell command's writer ----
        runCatching {
            val shellClass: Class<*> = XposedHelper.hostClassLoader
                .loadClass("com.android.server.pm.PackageManagerShellCommand")
            val onCommand: java.lang.reflect.Method = shellClass.declaredMethods.first { m ->
                m.name == "onCommand" && m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == String::class.java
            }
            XposedHelper.hookAfter(onCommand) { cb ->
                val shell = cb.thisObject ?: return@hookAfter
                val cmd = cb.args.getOrNull(0) as? String ?: return@hookAfter
                if (cmd != "grant" && cmd != "revoke") return@hookAfter
                val status = lastStatus ?: return@hookAfter
                lastStatus = null
                val printed = shellPrinted(shell, status)

                if (cb.throwable != null) {
                    cb.throwable = SecurityException("[AEGIS] $status")
                }
                if (!printed) XposedHelper.log("Aegis [STATUS] $status")
            }
        }.onFailure {
            XposedHelper.log("Aegis: shell status hook failed", it)
        }

        decisiveState = "statusOK"
    }
}
