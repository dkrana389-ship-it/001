package com.callreport.app.data

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Information regarding the previous/last call with this number prior to the current call.
 */
data class PreviousCallInfo(
    val hasPreviousCall: Boolean,
    val timestamp: Long = 0L,
    val durationSeconds: Long = 0L,
    val callType: Int = CallLog.Calls.INCOMING_TYPE,
    val relativeTimeAgo: String = "No previous calls", // e.g. "3 days ago" or "2 hours ago"
    val formattedDateTime: String = "",               // e.g. "Nov 3, 2026 at 04:15 PM"
    val callTypeLabel: String = "",                  // e.g. "Incoming Call"
    val formattedDuration: String = "",              // e.g. "4 min 12 sec"
    val summaryText: String = "First time caller"    // e.g. "3 days ago (Nov 3, 2026 at 04:15 PM)"
)

/**
 * Data summary structure for the post-call Call Report dialog.
 * Transient in-memory model only.
 */
data class TodayCallSummary(
    val contactName: String,
    val phoneNumber: String,
    val totalSeconds: Long,
    val callCount: Int,
    val formattedDurationText: String, // e.g. "Today's Total: 12 min 40 sec (3 calls)"
    val lastCallInfo: PreviousCallInfo
)

/**
 * Clean Architecture Repository for querying Android CallLog and ContactsContract.
 * 
 * Rules strictly enforced:
 * 1. Read-only queries directly from system content providers.
 * 2. Zero local database, zero SharedPreferences storage, zero logging of private numbers.
 * 3. Graceful fallback when permissions are revoked or numbers are private/unknown.
 */
class CallRepository(private val context: Context) {

    /**
     * Queries CallLog for:
     * 1. Today's calls since 00:00:00 (summing duration and counting calls).
     * 2. The previous/last call with this number prior to the one that just completed
     *    (including how many days/hours ago it happened, exact date & time, duration).
     */
    fun getTodaySummary(rawNumber: String?): TodayCallSummary {
        val safeNumber = rawNumber?.trim().orEmpty()
        val contactName = resolveContactName(safeNumber)

        val defaultPreviousCall = PreviousCallInfo(
            hasPreviousCall = false,
            summaryText = "First call from this number · No earlier history"
        )

        if (safeNumber.isEmpty() || !hasCallLogPermission()) {
            return TodayCallSummary(
                contactName = contactName,
                phoneNumber = safeNumber.ifEmpty { "Private / Unknown" },
                totalSeconds = 0,
                callCount = 0,
                formattedDurationText = formatEnglishDuration(0, 0),
                lastCallInfo = defaultPreviousCall
            )
        }

        // Compute midnight (00:00:00) timestamp for today
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDayMillis = calendar.timeInMillis
        val now = System.currentTimeMillis()

        var todayTotalSeconds = 0L
        var todayCallCount = 0

        var previousCallFound = false
        var previousCallInfo = defaultPreviousCall

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE
        )

        // Query call log sorted by DATE DESC to evaluate today and historical calls
        val sortOrder = "${CallLog.Calls.DATE} DESC"

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.let {
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)

                var matchedCount = 0

                while (it.moveToNext()) {
                    val logNumber = if (numberIndex != -1) it.getString(numberIndex) else null
                    val duration = if (durationIndex != -1) it.getLong(durationIndex) else 0L
                    val callDate = if (dateIndex != -1) it.getLong(dateIndex) else 0L
                    val callType = if (typeIndex != -1) it.getInt(typeIndex) else CallLog.Calls.INCOMING_TYPE

                    if (isNumberMatch(safeNumber, logNumber)) {
                        matchedCount++

                        // Sum today's calls
                        if (callDate >= startOfDayMillis) {
                            todayTotalSeconds += duration
                            todayCallCount++
                        }

                        // The 1st match in DESC order is the call that just ended.
                        // The 2nd match is the PREVIOUS call with this number!
                        if (matchedCount == 2 && !previousCallFound) {
                            previousCallFound = true
                            previousCallInfo = parsePreviousCall(callDate, duration, callType, now)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }

        val formattedText = formatEnglishDuration(todayTotalSeconds, todayCallCount)

        return TodayCallSummary(
            contactName = contactName,
            phoneNumber = safeNumber,
            totalSeconds = todayTotalSeconds,
            callCount = todayCallCount,
            formattedDurationText = formattedText,
            lastCallInfo = previousCallInfo
        )
    }

    /**
     * Resolves the phone number to a display name in ContactsContract.
     * Returns "Unknown" if not found or if READ_CONTACTS permission is missing.
     */
    fun resolveContactName(phoneNumber: String): String {
        if (phoneNumber.isEmpty() || !hasContactsPermission()) {
            return "Unknown"
        }

        var cursor: Cursor? = null
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

            cursor = context.contentResolver.query(uri, projection, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (nameIndex != -1) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) {
                        return name.trim()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }

        return "Unknown"
    }

    /**
     * Parses the previous call timestamp and formats both relative elapsed time
     * (e.g. "3 days ago", "2 hours ago") and exact date and time (e.g. "Nov 3, 2026 at 04:15 PM").
     */
    private fun parsePreviousCall(
        callDate: Long,
        durationSeconds: Long,
        callType: Int,
        now: Long
    ): PreviousCallInfo {
        val diffMillis = (now - callDate).coerceAtLeast(0L)
        val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
        val diffHours = TimeUnit.MILLISECONDS.toHours(diffMillis)
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

        val relativeTimeAgo = when {
            diffMinutes < 1 -> "Just now"
            diffMinutes < 60 -> "$diffMinutes min ago"
            diffHours < 24 -> if (diffHours == 1L) "1 hour ago" else "$diffHours hours ago"
            diffDays == 1L -> "Yesterday"
            diffDays < 7 -> "$diffDays days ago"
            diffDays < 30 -> "${diffDays / 7} week${if (diffDays >= 14) "s" else ""} ago"
            else -> "${diffDays / 30} month${if (diffDays >= 60) "s" else ""} ago"
        }

        val dateTimeFormat = SimpleDateFormat("MMM d, yyyy · hh:mm a", Locale.ENGLISH)
        val exactDateTime = dateTimeFormat.format(callDate)

        val typeLabel = when (callType) {
            CallLog.Calls.INCOMING_TYPE -> "Incoming Call"
            CallLog.Calls.OUTGOING_TYPE -> "Outgoing Call"
            CallLog.Calls.MISSED_TYPE -> "Missed Call"
            CallLog.Calls.REJECTED_TYPE -> "Declined Call"
            else -> "Call"
        }

        val hours = durationSeconds / 3600
        val remainingSeconds = durationSeconds % 3600
        val mins = remainingSeconds / 60
        val secs = remainingSeconds % 60
        val formattedDuration = buildString {
            if (hours > 0) append("${hours}h ")
            if (mins > 0 || hours > 0) append("${mins}m ")
            append("${secs}s")
        }.trim()

        val summary = "$relativeTimeAgo ($exactDateTime)"

        return PreviousCallInfo(
            hasPreviousCall = true,
            timestamp = callDate,
            durationSeconds = durationSeconds,
            callType = callType,
            relativeTimeAgo = relativeTimeAgo,
            formattedDateTime = exactDateTime,
            callTypeLabel = typeLabel,
            formattedDuration = formattedDuration,
            summaryText = summary
        )
    }

    /**
     * Formats duration into clean, proper English string:
     * Example: "Today's total: 12 min 40 sec (3 calls)"
     */
    private fun formatEnglishDuration(totalSeconds: Long, count: Int): String {
        val hours = totalSeconds / 3600
        val remainingSeconds = totalSeconds % 3600
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60

        val timeString = buildString {
            if (hours > 0) {
                append("${hours} hr ")
            }
            if (minutes > 0 || hours > 0) {
                append("${minutes} min ")
            }
            append("${seconds} sec")
        }

        val callSuffix = if (count == 1) "call" else "calls"
        return "Today's total: $timeString ($count $callSuffix)"
    }

    /**
     * Flexible phone number comparison matching normalized digits or suffixes.
     */
    private fun isNumberMatch(targetNumber: String, candidateNumber: String?): Boolean {
        if (candidateNumber.isNullOrBlank()) return false
        
        // Fast exact match
        if (targetNumber == candidateNumber) return true

        // Android telecom utility comparison
        if (PhoneNumberUtils.compare(context, targetNumber, candidateNumber)) return true

        // Fallback: match last 8-10 digits to ignore prefixes (+91, 0, etc.)
        val cleanTarget = targetNumber.filter { it.isDigit() }
        val cleanCandidate = candidateNumber.filter { it.isDigit() }

        if (cleanTarget.isEmpty() || cleanCandidate.isEmpty()) return false
        if (cleanTarget == cleanCandidate) return true

        val minLen = minOf(cleanTarget.length, cleanCandidate.length)
        if (minLen >= 8) {
            val suffixTarget = cleanTarget.takeLast(8)
            val suffixCandidate = cleanCandidate.takeLast(8)
            return suffixTarget == suffixCandidate
        }

        return false
    }

    private fun hasCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
