package com.callreport.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.callreport.app.R
import com.google.android.material.button.MaterialButton

/**
 * Small dialog-style translucent popup shown after every call ends.
 * 
 * Features (STEP 5 & STEP 7):
 * - Displays Contact Name, Phone Number, and "Aaj total X min Y sec (Z calls)"
 * - 8-second auto-dismiss countdown timer
 * - Call Back button firing Intent.ACTION_DIAL
 * - Direct Sponsor Ad Banner loaded locally without AdMob
 */
class PopupActivity : Activity() {

    companion object {
        const val EXTRA_CONTACT_NAME = "extra_contact_name"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_DURATION_TEXT = "extra_duration_text"
        const val EXTRA_LAST_CALL_SUMMARY = "extra_last_call_summary"
        const val EXTRA_LAST_CALL_RELATIVE = "extra_last_call_relative"
        const val EXTRA_LAST_CALL_DATETIME = "extra_last_call_datetime"
        const val EXTRA_LAST_CALL_DURATION = "extra_last_call_duration"
        const val EXTRA_LAST_CALL_TYPE = "extra_last_call_type"
        const val EXTRA_HAS_PREVIOUS_CALL = "extra_has_previous_call"
        
        private const val AUTO_CLOSE_MILLIS = 8000L
        private const val SPONSOR_CLICK_URL = "https://example.com/sponsor"
    }

    private var countDownTimer: CountDownTimer? = null
    private var targetPhoneNumber: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_popup)

        // Configure window as floating translucent dialog
        setupWindowLayout()

        val contactName = intent.getStringExtra(EXTRA_CONTACT_NAME) ?: "Unknown"
        targetPhoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
        val durationText = intent.getStringExtra(EXTRA_DURATION_TEXT) ?: "Today's total: 0 sec (0 calls)"
        val hasPreviousCall = intent.getBooleanExtra(EXTRA_HAS_PREVIOUS_CALL, false)
        val lastCallRelative = intent.getStringExtra(EXTRA_LAST_CALL_RELATIVE) ?: "First time caller"
        val lastCallDateTime = intent.getStringExtra(EXTRA_LAST_CALL_DATETIME) ?: "No previous calls"
        val lastCallDuration = intent.getStringExtra(EXTRA_LAST_CALL_DURATION) ?: ""
        val lastCallType = intent.getStringExtra(EXTRA_LAST_CALL_TYPE) ?: "Call"

        val tvName = findViewById<TextView>(R.id.tvContactName)
        val tvNumber = findViewById<TextView>(R.id.tvPhoneNumber)
        val tvDuration = findViewById<TextView>(R.id.tvDurationSummary)
        val tvLastCallRelative = findViewById<TextView>(R.id.tvLastCallRelative)
        val tvLastCallDateTime = findViewById<TextView>(R.id.tvLastCallDateTime)
        val tvLastCallDetails = findViewById<TextView>(R.id.tvLastCallDetails)
        val tvTimer = findViewById<TextView>(R.id.tvAutoCloseTimer)
        val btnCallBack = findViewById<MaterialButton>(R.id.btnCallBack)
        val btnClose = findViewById<MaterialButton>(R.id.btnClose)
        val ivSponsorBanner = findViewById<ImageView>(R.id.ivSponsorBanner)

        tvName.text = contactName
        tvNumber.text = targetPhoneNumber.ifBlank { "Private Number" }
        tvDuration.text = durationText

        if (hasPreviousCall) {
            tvLastCallRelative.text = lastCallRelative
            tvLastCallDateTime.text = lastCallDateTime
            tvLastCallDetails.text = if (lastCallDuration.isNotBlank()) {
                "$lastCallType · $lastCallDuration"
            } else {
                lastCallType
            }
        } else {
            tvLastCallRelative.text = "First Call"
            tvLastCallDateTime.text = "No earlier call history with this number"
            tvLastCallDetails.text = "New Contact"
        }

        // Call Back Button: Uses ACTION_DIAL
        btnCallBack.setOnClickListener {
            cancelTimer()
            if (targetPhoneNumber.isNotBlank()) {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$targetPhoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    startActivity(dialIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            finish()
        }

        // Close Button
        btnClose.setOnClickListener {
            cancelTimer()
            finish()
        }

        // Direct Sponsor Banner Click (No AdMob, direct click intent)
        ivSponsorBanner.setOnClickListener {
            cancelTimer()
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(SPONSOR_CLICK_URL)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(browserIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            finish()
        }

        // Start 8-second auto-close countdown
        startAutoCloseCountdown(tvTimer)
    }

    private fun setupWindowLayout() {
        window?.let { win ->
            win.setGravity(Gravity.CENTER)
            val params = win.attributes
            val displayMetrics = resources.displayMetrics
            // Compact small popup width: max 330dp, ~82% of screen width
            val maxWidth = (330 * displayMetrics.density).toInt()
            val targetWidth = (displayMetrics.widthPixels * 0.82).toInt()
            params.width = minOf(maxWidth, targetWidth)
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            win.attributes = params
        }
    }

    private fun startAutoCloseCountdown(tvTimer: TextView) {
        countDownTimer = object : CountDownTimer(AUTO_CLOSE_MILLIS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                tvTimer.text = "Closing in ${secondsLeft}s"
            }

            override fun onFinish() {
                tvTimer.text = "Closing..."
                if (!isFinishing && !isDestroyed) {
                    finish()
                }
            }
        }.start()
    }

    private fun cancelTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelTimer()
    }
}
