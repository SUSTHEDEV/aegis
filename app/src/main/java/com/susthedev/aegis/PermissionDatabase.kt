package com.susthedev.aegis

/**
 * Internal database of human-readable explanations for Android signature,
 * development, and privileged framework permissions.
 */
object PermissionDatabase {

    private val descriptions = mapOf(
        "android.permission.REAL_GET_TASKS" to
            "Allows accessing detailed task snapshots, recent task stacks, and foreground application states.",
        "android.permission.MANAGE_EXTERNAL_STORAGE" to
            "Allows full read/write file access to all shared external storage directories on the device.",
        "android.permission.WRITE_SECURE_SETTINGS" to
            "Allows modifying global system-level settings, secure flags, and device configuration values.",
        "android.permission.DUMP" to
            "Allows extracting internal state dumps, service metrics, and diagnostic logs from system services.",
        "android.permission.PACKAGE_USAGE_STATS" to
            "Allows querying application usage history, foreground session durations, and component stats.",
        "android.permission.SYSTEM_ALERT_WINDOW" to
            "Allows drawing floating windows, system overlays, and dialogs on top of all other applications.",
        "android.permission.INTERNAL_SYSTEM_WINDOW" to
            "Allows creating internal system windows that sit at high system window layers (such as status bar, keyguard, or system overlays).",
        "android.permission.INTERACT_ACROSS_USERS" to
            "Allows sending events, broadcasts, and performing Binder calls across multiple device user profiles.",
        "android.permission.INTERACT_ACROSS_USERS_FULL" to
            "Full unrestricted cross-user interaction and privileged Binder transaction rights across all profiles.",
        "android.permission.GRANT_RUNTIME_PERMISSIONS" to
            "Allows programmatically granting runtime permissions to installed applications.",
        "android.permission.REVOKE_RUNTIME_PERMISSIONS" to
            "Allows programmatically revoking runtime permissions from installed applications.",
        "android.permission.OBSERVE_GRANT_REVOKE_PERMISSIONS" to
            "Allows listening to framework permission grant and revoke dispatch events in real time.",
        "android.permission.READ_FRAME_BUFFER" to
            "Allows capturing raw screen framebuffer contents and taking system-level screenshots.",
        "android.permission.MODIFY_PHONE_STATE" to
            "Allows modifying telephony states, cellular radio settings, and mobile network configurations.",
        "android.permission.STATUS_BAR" to
            "Allows controlling the status bar, expanding notification panels, and adding status bar icons.",
        "android.permission.MANAGE_USERS" to
            "Allows creating, deleting, and managing device user accounts and guest profiles.",
        "android.permission.REBOOT" to
            "Allows issuing system reboot, shutdown, or recovery commands.",
        "android.permission.CHANGE_COMPONENT_ENABLED_STATE" to
            "Allows enabling or disabling application components, activities, services, and receivers.",
        "android.permission.INSTALL_PACKAGES" to
            "Allows installing application packages directly without opening the system package installer.",
        "android.permission.DELETE_PACKAGES" to
            "Allows quietly uninstalling application packages in the background.",
        "android.permission.MOUNT_UNMOUNT_FILESYSTEMS" to
            "Allows mounting and unmounting file systems for removable storage devices.",
        "android.permission.SET_ACTIVITY_WATCHER" to
            "Allows monitoring and controlling how the system launches and executes activities.",
        "android.permission.CONTROL_VPN" to
            "Allows establishing, configuring, and managing system-wide VPN connections.",
        "android.permission.START_ANY_ACTIVITY" to
            "Allows launching any activity regardless of exported status or permission constraints.",
        "android.permission.CAPTURE_VIDEO_OUTPUT" to
            "Allows capturing raw video frames output by display endpoints.",
        "android.permission.CAPTURE_AUDIO_OUTPUT" to
            "Allows capturing raw audio output streams from media playback endpoints."
    )

    fun getDescription(perm: String): String {
        return descriptions[perm]
            ?: "System-level signature permission. Grants privileged development access to internal framework APIs."
    }
}
