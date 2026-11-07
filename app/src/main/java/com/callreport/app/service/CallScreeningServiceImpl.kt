package com.callreport.app.service

import android.telecom.Call
import android.telecom.CallScreeningService
import com.callreport.app.CallReportApp

/**
 * CallScreeningService implementation for CallReport.
 * 
 * Required by STEP 2:
 * 1. Registered with android.permission.BIND_SCREENING_SERVICE.
 * 2. ONLY allows the call (never blocks or rejects).
 * 3. Remembers the active incoming/outgoing phone number in transient memory.
 */
class CallScreeningServiceImpl : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        try {
            // Extract the phone number from the Call.Details handle (URI format: tel:1234567890)
            val handleUri = callDetails.handle
            val rawNumber = handleUri?.schemeSpecificPart

            if (!rawNumber.isNullOrBlank()) {
                // Keep the active number in transient memory so CallMonitorService
                // can correlate it when the call transitions to IDLE.
                CallReportApp.updateActiveNumber(rawNumber)
            }

            // Always allow the call: do not disallow, do not reject, do not skip log or notification
            val response = CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()

            respondToCall(callDetails, response)
        } catch (e: Exception) {
            e.printStackTrace()
            // If an exception occurs, ensure the call is not interrupted
            try {
                val fallbackResponse = CallResponse.Builder().build()
                respondToCall(callDetails, fallbackResponse)
            } catch (ignored: Exception) {}
        }
    }
}
