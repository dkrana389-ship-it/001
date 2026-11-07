package com.callreport.app.util

import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Data model for Step 1 onboarding permission item with bilingual reasoning.
 */
data class PermissionItem(
    val id: String,
    val title: String,
    val purpose: String,
    val detail: String,
    val isGranted: Boolean,
    val isRequired: Boolean = true
)

/**
 * Utility helper to check Android runtime permissions, overlay permission,
 * and Telecom Call Screening role.
 */
object PermissionHelper {

    const val ID_PHONE_STATE = "permission_phone_state"
    const val ID_CALL_LOG = "permission_call_log"
    const val ID_CONTACTS = "permission_contacts"
    const val ID_POST_NOTIFICATIONS = "permission_post_notifications"
    const val ID_SYSTEM_ALERT_WINDOW = "permission_system_alert_window"
    const val ID_ROLE_CALL_SCREENING = "role_call_screening"

    fun getPermissionList(context: Context): List<PermissionItem> {
        val list = mutableListOf<PermissionItem>()

        // 1. Phone State
        list.add(
            PermissionItem(
                id = ID_PHONE_STATE,
                title = "Phone State",
                purpose = "Required to accurately detect when a call begins and when it disconnects.",
                detail = "Enables TelephonyCallback to listen for OFFHOOK to IDLE state changes.",
                isGranted = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            )
        )

        // 2. Call Log
        list.add(
            PermissionItem(
                id = ID_CALL_LOG,
                title = "Call Log",
                purpose = "Calculates today's total conversation duration and call count with this caller.",
                detail = "Read-only query directly from CallLog.Calls (date >= 00:00:00). Zero persistent storage.",
                isGranted = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_CALL_LOG
                ) == PackageManager.PERMISSION_GRANTED
            )
        )

        // 3. Contacts
        list.add(
            PermissionItem(
                id = ID_CONTACTS,
                title = "Contacts",
                purpose = "Resolves the incoming or outgoing number to their saved contact display name.",
                detail = "Lookups via ContactsContract.PhoneLookup. If absent, displays Unknown.",
                isGranted = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
            )
        )

        // 4. Notifications (API 33+)
        val isNotifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        list.add(
            PermissionItem(
                id = ID_POST_NOTIFICATIONS,
                title = "Foreground Notification",
                purpose = "Maintains the continuous low-priority background monitoring service.",
                detail = "Allows CallMonitorService to stay active without being killed by Android memory manager.",
                isGranted = isNotifGranted
            )
        )

        // 5. Draw Over Other Apps (SYSTEM_ALERT_WINDOW)
        val canDrawOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
        list.add(
            PermissionItem(
                id = ID_SYSTEM_ALERT_WINDOW,
                title = "Display Over Other Apps",
                purpose = "Allows the summary popup dialog to appear immediately after you hang up.",
                detail = "Used by PopupActivity to float over the home screen or third-party apps.",
                isGranted = canDrawOverlay
            )
        )

        // 6. Call Screening Role
        val isScreeningGranted = isCallScreeningRoleHeld(context)
        list.add(
            PermissionItem(
                id = ID_ROLE_CALL_SCREENING,
                title = "Call Screening Role",
                purpose = "Securely captures active phone numbers for talk-time calculation without blocking calls.",
                detail = "Registers CallScreeningService to obtain the call handle and allow all calls immediately.",
                isGranted = isScreeningGranted
            )
        )

        return list
    }

    fun isCallScreeningRoleHeld(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) ?: false
        } else {
            true
        }
    }

    fun areAllEssentialPermissionsGranted(context: Context): Boolean {
        val items = getPermissionList(context)
        return items.all { it.isGranted }
    }
}
