package com.susthedev.aegis

/**
 * Dynamic decision engine backed by LSPosed remote preferences (XposedPrefs).
 *
 * Semantics:
 *  - apps WITHOUT the aegis.devgrant tag never reach decide() (passthrough — stock OS behavior)
 *  - Existing rule found in XposedPrefs -> returns stored Decision (APPROVED / DENIED)
 *  - Missing entry -> triggers an interactive System Overlay prompt window (PromptOverlay)
 *    and persists the user's choice to XposedPrefs.
 */
object AegisPolicy {
    enum class Decision { APPROVED, DENIED, PENDING }

    /**
     * Definition-wide stamping (PermissionDevelopmentHook sections 2-4): OFF in the per-pair
     * model — signature definitions stay unflagged at boot, and per-pair stamping happens
     * at grant-check time for APPROVED pairs only.
     */
    const val DEFINITION_STAMPING = false

    fun getRule(pkg: String, perm: String): Decision? {
        val prefs = XposedPrefs.getOrNull()
        val permRule = prefs?.getString("rule:$pkg:$perm", null) ?: return null
        return runCatching { Decision.valueOf(permRule) }.getOrNull()
    }

    fun decide(pkg: String, perm: String): Decision {
        val prefs = XposedPrefs.getOrNull()

        // 1. Check specific rule entry
        val permRule = prefs?.getString("rule:$pkg:$perm", null)
        if (!permRule.isNullOrBlank()) {
            val decision = runCatching { Decision.valueOf(permRule) }.getOrNull()
            if (decision != null) return decision
        }

        // 2. Check package-wide fallback rule entry
        val appRule = prefs?.getString("app_rule:$pkg", null)
        if (!appRule.isNullOrBlank()) {
            val decision = runCatching { Decision.valueOf(appRule) }.getOrNull()
            if (decision != null) return decision
        }

        // 3. Entry missing -> prompt the user via System Overlay window
        XposedHelper.log("AegisPolicy: no rule entry found for $pkg / $perm -> triggering PromptOverlay")
        val result = PromptOverlay.showPrompt(pkg, perm)

        // 4. Persist choice to XposedPrefs (synchronous commit)
        runCatching {
            prefs?.edit()?.putString("rule:$pkg:$perm", result.name)?.commit()
        }.onFailure { t ->
            XposedHelper.log("AegisPolicy: failed to write decision preference for $pkg / $perm", t)
        }

        return result
    }

    fun setRule(pkg: String, perm: String, decision: Decision) {
        XposedPrefs.getOrNull()?.edit()?.putString("rule:$pkg:$perm", decision.name)?.commit()
    }

    fun clearRule(pkg: String, perm: String) {
        XposedPrefs.getOrNull()?.edit()?.remove("rule:$pkg:$perm")?.commit()
    }

    fun setAppRule(pkg: String, decision: Decision) {
        XposedPrefs.getOrNull()?.edit()?.putString("app_rule:$pkg", decision.name)?.commit()
    }

    fun clearAppRule(pkg: String) {
        XposedPrefs.getOrNull()?.edit()?.remove("app_rule:$pkg")?.commit()
    }
}
