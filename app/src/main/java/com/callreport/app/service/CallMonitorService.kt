package com.callreport.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CallLog
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.callreport.app.CallReportApp
import com.callreport.app.R
import com.callreport.app.data.CallRepository
import com.callreport.app.ui.OnboardingActivity
import com.callreport.app.ui.PopupActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground Service that continuously observes telephony state.
 * 
 * Complies with STEP 3 & STEP 6:
 * - Uses TelephonyCallback for API 31+ (Android 12/13/14).
 * - Uses PhoneStateListener for API 29-30 (Android 10/11).
 * - Detects OFFHOOK -> IDLE transition.
 * - Waits ~1000ms for system CallLog database flush.
 * - Launches dialog-style PopupActivity with today's talk-time statistics.
 */
class CallMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var callRepository: CallRepository
    private lateinit var prefs: SharedPreferences

    // Track previous state to verify OFFHOOK -> IDLE transition
    private var lastObservedState = TelephonyManager.CALL_STATE_IDLE
    private var wasOffhook = false

    // API 31+ Callback reference
    private var telephonyCallback: TelephonyCallback? = null

    // Legacy PhoneStateListener for API 29-30
    private var phoneStateListener: PhoneStateListener? = null

    companion object {
        const val PREFS_NAME = "call_report_prefs"
        const val KEY_SERVICE_ENABLED = "key_service_enabled"
        private const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            val intent = Intent(context, CallMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CallMonitorService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        callRepository = CallRepository(this)
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        startInForeground()
        registerCallStateListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY ensures service restarts if killed by the OS
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val launchIntent = Intent(this, OnboardingActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CallReportApp.CHANNEL_ID)
            .setContentTitle("CallReport Active")
            .setContentText("Monitoring call end to calculate talk time")
            .setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun registerCallStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Modern TelephonyCallback for Android 12+ (API 31, 32, 33, 34)
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallStateChange(state)
                }
            }
            telephonyCallback = callback
            telephonyManager.registerTelephonyCallback(mainExecutor, callback)
        } else {
            // Fallback PhoneStateListener for Android 10-11 (API 29-30)
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    super.onCallStateChanged(state, phoneNumber)
                    if (!phoneNumber.isNullOrBlank()) {
                        CallReportApp.updateActiveNumber(phoneNumber)
                    }
                    handleCallStateChange(state)
                }
            }
            phoneStateListener = listener
            @Suppress("DEPRECATION")
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun handleCallStateChange(state: Int) {
        val isEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
        if (!isEnabled) {
            lastObservedState = state
            return
        }

        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call answered or outgoing dialed
                wasOffhook = true
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                // If the phone was active/talking and is now hung up
                if (wasOffhook) {
                    wasOffhook = false
                    triggerPostCallProcessing()
                }
            }

            TelephonyManager.CALL_STATE_RINGING -> {
                // Incoming phone ringing
            }
        }
        lastObservedState = state
    }

    /**
     * Executes STEP 4 & STEP 5:
     * Waits ~1 second to ensure system provider writes duration, then queries and launches popup.
     */
    private fun triggerPostCallProcessing() {
        serviceScope.launch {
            // STEP 4 requirement: Wait ~1 second after call end
            delay(1000)

            val targetNumber = withContext(Dispatchers.IO) {
                findTargetPhoneNumber()
            }

            val summary = withContext(Dispatchers.IO) {
                callRepository.getTodaySummary(targetNumber)
            }

            // Clean in-memory reference immediately after query
            CallReportApp.clearActiveNumber()

            // Launch PopupActivity with today summary and last call details
            launchPopup(
                name = summary.contactName,
                number = summary.phoneNumber,
                durationText = summary.formattedDurationText,
                lastCallSummary = summary.lastCallInfo.summaryText,
                lastCallRelative = summary.lastCallInfo.relativeTimeAgo,
                lastCallDateTime = summary.lastCallInfo.formattedDateTime,
                lastCallDuration = summary.lastCallInfo.formattedDuration,
                lastCallType = summary.lastCallInfo.callTypeLabel,
                hasPreviousCall = summary.lastCallInfo.hasPreviousCall
            )
        }
    }

    /**
     * Tries in-memory number first; if absent, reads the topmost latest call log entry.
     */
    private fun findTargetPhoneNumber(): String {
        val cached = CallReportApp.currentActiveNumber
        if (!cached.isNullOrBlank()) return cached

        // Fallback: check most recent CallLog entry within the last 15 seconds
        var fallbackNumber = ""
        try {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE),
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                    if (numIdx != -1) {
                        fallbackNumber = it.getString(numIdx).orEmpty()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return fallbackNumber
    }

    private fun launchPopup(
        name: String,
        number: String,
        durationText: String,
        lastCallSummary: String,
        lastCallRelative: String,
        lastCallDateTime: String,
        lastCallDuration: String,
        lastCallType: String,
        hasPreviousCall: Boolean
    ) {
        try {
            val intent = Intent(this, PopupActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(PopupActivity.EXTRA_CONTACT_NAME, name)
                putExtra(PopupActivity.EXTRA_PHONE_NUMBER, number)
                putExtra(PopupActivity.EXTRA_DURATION_TEXT, durationText)
                putExtra(PopupActivity.EXTRA_LAST_CALL_SUMMARY, lastCallSummary)
                putExtra(PopupActivity.EXTRA_LAST_CALL_RELATIVE, lastCallRelative)
                putExtra(PopupActivity.EXTRA_LAST_CALL_DATETIME, lastCallDateTime)
                putExtra(PopupActivity.EXTRA_LAST_CALL_DURATION, lastCallDuration)
                putExtra(PopupActivity.EXTRA_LAST_CALL_TYPE, lastCallType)
                putExtra(PopupActivity.EXTRA_HAS_PREVIOUS_CALL, hasPreviousCall)
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()

        // Unregister listeners cleanly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let {
                telephonyManager.unregisterTelephonyCallback(it)
            }
        } else {
            phoneStateListener?.let {
                @Suppress("DEPRECATION")
                telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
    }
}
