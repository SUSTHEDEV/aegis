package com.susthedev.aegis.hook

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.susthedev.aegis.Config
import com.susthedev.aegis.XposedHelper

/**
 * Discovery: resolves whether a package actually declares permissions in its
 * `aegis.devgrant` manifest meta-data.
 * Supports:
 *  - Resource array references: android:value="@array/my_dev_perms"
 *  - String arrays / String lists
 *  - Comma / semicolon separated String lists
 */
object Discovery {

    private const val GET_META_DATA = PackageManager.GET_META_DATA.toLong()

    fun getDeclaredPermissions(ai: ApplicationInfo, pm: PackageManager? = null): List<String> {
        val meta = ai.metaData ?: return emptyList()
        if (!meta.containsKey(Config.DISCOVERY_TAG)) return emptyList()

        return try {
            val rawValue = meta.get(Config.DISCOVERY_TAG) ?: return emptyList()

            when (rawValue) {
                is Int -> {
                    val resId = rawValue
                    if (resId != 0 && pm != null) {
                        runCatching {
                            val res = pm.getResourcesForApplication(ai)
                            res.getStringArray(resId).toList()
                        }.getOrDefault(emptyList())
                    } else if (resId != 0) {
                        runCatching {
                            val contextClass = Class.forName("android.app.ActivityThread")
                            val currentAT = contextClass.getMethod("currentActivityThread").invoke(null)
                            val sysCtx = contextClass.getMethod("getSystemContext").invoke(currentAT) as? android.content.Context
                            val appPm = sysCtx?.packageManager
                            val res = appPm?.getResourcesForApplication(ai)
                            res?.getStringArray(resId)?.toList()
                        }.getOrNull() ?: emptyList()
                    } else emptyList()
                }
                is Array<*> -> rawValue.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
                is Collection<*> -> rawValue.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
                is String -> rawValue.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
                else -> emptyList()
            }
        } catch (t: Throwable) {
            XposedHelper.log("Aegis [discovery] getDeclaredPermissions error for ${ai.packageName}: ${t.message}")
            emptyList()
        }
    }

    fun declaredPerm(pkg: String, perm: String, user: Int): Boolean {
        return try {
            val ipm = XposedHelper.hostClassLoader
                .loadClass("android.app.AppGlobals")
                .getMethod("getPackageManager")
                .invoke(null)
            val ai = try {
                ipm.javaClass.getMethod(
                    "getApplicationInfo",
                    String::class.java, Long::class.javaPrimitiveType, Int::class.javaPrimitiveType
                ).invoke(ipm, pkg, GET_META_DATA, user) as? ApplicationInfo
            } catch (_: Throwable) {
                ipm.javaClass.getMethod(
                    "getApplicationInfo",
                    String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
                ).invoke(ipm, pkg, PackageManager.GET_META_DATA, user) as? ApplicationInfo
            }
            if (ai == null) false
            else {
                val perms = getDeclaredPermissions(ai)
                perms.contains(perm)
            }
        } catch (t: Throwable) {
            XposedHelper.log("Aegis [discovery] error for $pkg/$perm: ${t.message}")
            false
        }
    }
}
