package com.pksafe.lock.manager.util

/**
 * Single source of Device Owner + permission commands.
 * Cable (USB ADB) and Wireless ADB both run this same list.
 */
object DeviceOwnerSetup {
    const val PACKAGE = "com.pksafe.lock.manager"
    const val ADMIN_COMPONENT = "$PACKAGE/.receiver.AdminReceiver"
    const val ACCESSIBILITY_COMPONENT =
        "$PACKAGE/com.pksafe.lock.manager.service.AntiUninstallService"

    // Shell snippet that appends our service to existing enabled accessibility services
    // instead of overwriting them, and enables accessibility.
    const val ACCESSIBILITY_ENABLE_COMMAND =
        "comp=\"$ACCESSIBILITY_COMPONENT\"; " +
        "svc=\"\$(settings get secure enabled_accessibility_services)\"; " +
        "if [ \"\$svc\" = \"null\" ] || [ -z \"\$svc\" ]; then settings put secure enabled_accessibility_services \"\$comp\"; " +
        "elif echo \"\$svc\" | grep -vq \"\$comp\"; then settings put secure enabled_accessibility_services \"\${svc}:\${comp}\"; fi; " +
        "settings put secure accessibility_enabled 1"

    val runtimePermissions = listOf(
        "android.permission.READ_PHONE_STATE",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.POST_NOTIFICATIONS"
    )

    /** Ordered ADB shell commands: Device Owner first, then every permission cable needs. */
    val shellCommands: List<Pair<String, String>> = buildList {
        add("dpm set-device-owner --user 0 $ADMIN_COMPONENT" to "Device Owner")
        add("dpm set-device-owner $ADMIN_COMPONENT" to "Device Owner (fallback)")
        runtimePermissions.forEach { perm ->
            add("pm grant $PACKAGE $perm" to perm.substringAfterLast('.'))
        }
        add("appops set $PACKAGE SYSTEM_ALERT_WINDOW allow" to "Overlay")
        add("appops set $PACKAGE android:system_alert_window allow" to "Overlay (appops)")
        add("appops set $PACKAGE android:get_usage_stats allow" to "Usage stats")
        add("appops set $PACKAGE android:write_settings allow" to "Write settings")
        add(ACCESSIBILITY_ENABLE_COMMAND to "Anti-uninstall guard")
        add("settings put secure user_setup_complete 1" to "Setup complete")
        add("settings put global device_provisioned 1" to "Device provisioned")
    }
}
