package com.callreport.app.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

/**
 * Handles OEM-specific Auto-Start and Background Battery Management.
 * Crucial for aggressive battery kill systems on Xiaomi, Vivo, Oppo/Realme, and Samsung.
 */
object OemAutoStartHelper {

    enum class Manufacturer {
        XIAOMI,
        VIVO,
        OPPO_REALME,
        SAMSUNG,
        GENERIC
    }

    fun getDeviceManufacturer(): Manufacturer {
        val brand = Build.MANUFACTURER.lowercase()
        return when {
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") -> Manufacturer.XIAOMI
            brand.contains("vivo") || brand.contains("iqoo") -> Manufacturer.VIVO
            brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") -> Manufacturer.OPPO_REALME
            brand.contains("samsung") -> Manufacturer.SAMSUNG
            else -> Manufacturer.GENERIC
        }
    }

    /**
     * Opens system battery optimization settings to allow "Unrestricted" background execution.
     */
    @SuppressLint("BatteryLife")
    fun openBatteryOptimization(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isIgnoring = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

                if (!isIgnoring) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return
                }
            }
            // Fallback to general battery screen
            val generalIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(generalIntent)
        } catch (e: Exception) {
            openAppDetailsSettings(context)
        }
    }

    /**
     * Dispatches OEM-specific Autostart settings screen.
     */
    fun openAutoStartSettings(context: Context, target: Manufacturer? = null) {
        val manufacturer = target ?: getDeviceManufacturer()
        val intents = when (manufacturer) {
            Manufacturer.XIAOMI -> listOf(
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"))
            )
            Manufacturer.VIVO -> listOf(
                Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
                Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"))
            )
            Manufacturer.OPPO_REALME -> listOf(
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"))
            )
            Manufacturer.SAMSUNG -> listOf(
                Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
                Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"))
            )
            Manufacturer.GENERIC -> emptyList()
        }

        for (intent in intents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return
            } catch (ignored: Exception) {}
        }

        // If specific OEM intent fails, open application info page
        openAppDetailsSettings(context)
    }

    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Toast.makeText(context, "Settings -> Battery -> Allow Unrestricted background activity", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
