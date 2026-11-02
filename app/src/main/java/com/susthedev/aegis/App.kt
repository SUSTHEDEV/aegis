package com.susthedev.aegis

import android.app.Application
import android.content.Context
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.github.libxposed.service.XposedServiceHelper.OnServiceListener

/**
 * The app binds the shared prefs seam (LSPosed remote preferences); the hooks read
 * the same store via XposedModule#getRemotePreferences.
 */
class App : Application(), OnServiceListener {

    companion object {
        @JvmStatic
        var rwPrefs: android.content.SharedPreferences? = null
            private set
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        synchronized(this) {
            rwPrefs = service.getRemotePreferences("aegis_conf")
            XposedPrefs.bind { rwPrefs }
        }
    }

    override fun onServiceDied(service: XposedService) {
        synchronized(this) {
            rwPrefs = null
        }
    }
}
