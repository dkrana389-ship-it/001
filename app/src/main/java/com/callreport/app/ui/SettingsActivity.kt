package com.callreport.app.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.callreport.app.R
import com.callreport.app.service.CallMonitorService
import com.callreport.app.util.OemAutoStartHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Settings Activity (STEP 6):
 * - Master ON/OFF Switch to enable or disable call monitoring.
 * - Battery Optimization "Unrestricted" shortcut.
 * - OEM Autostart shortcuts for Xiaomi, Vivo, Oppo/Realme, and Samsung.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var switchService: SwitchMaterial
    private lateinit var tvStatusText: TextView
    private lateinit var btnBatteryOpt: MaterialButton
    private lateinit var btnXiaomiAutostart: MaterialButton
    private lateinit var btnVivoAutostart: MaterialButton
    private lateinit var btnOppoAutostart: MaterialButton
    private lateinit var btnSamsungAutostart: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(CallMonitorService.PREFS_NAME, Context.MODE_PRIVATE)

        initViews()
        setupListeners()
        updateUIState()
    }

    private fun initViews() {
        switchService = findViewById(R.id.switchService)
        tvStatusText = findViewById(R.id.tvStatusText)
        btnBatteryOpt = findViewById(R.id.btnBatteryOpt)
        btnXiaomiAutostart = findViewById(R.id.btnXiaomiAutostart)
        btnVivoAutostart = findViewById(R.id.btnVivoAutostart)
        btnOppoAutostart = findViewById(R.id.btnOppoAutostart)
        btnSamsungAutostart = findViewById(R.id.btnSamsungAutostart)

        // Highlight detected OEM
        val currentOem = OemAutoStartHelper.getDeviceManufacturer()
        when (currentOem) {
            OemAutoStartHelper.Manufacturer.XIAOMI -> btnXiaomiAutostart.text = "Xiaomi / Redmi / POCO (Detected)"
            OemAutoStartHelper.Manufacturer.VIVO -> btnVivoAutostart.text = "Vivo / iQOO (Detected)"
            OemAutoStartHelper.Manufacturer.OPPO_REALME -> btnOppoAutostart.text = "Oppo / Realme / OnePlus (Detected)"
            OemAutoStartHelper.Manufacturer.SAMSUNG -> btnSamsungAutostart.text = "Samsung OneUI (Detected)"
            else -> {}
        }
    }

    private fun setupListeners() {
        switchService.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(CallMonitorService.KEY_SERVICE_ENABLED, isChecked).apply()
            updateUIState()
            if (isChecked) {
                CallMonitorService.startService(this)
            } else {
                CallMonitorService.stopService(this)
            }
        }

        btnBatteryOpt.setOnClickListener {
            OemAutoStartHelper.openBatteryOptimization(this)
        }

        btnXiaomiAutostart.setOnClickListener {
            OemAutoStartHelper.openAutoStartSettings(this, OemAutoStartHelper.Manufacturer.XIAOMI)
        }

        btnVivoAutostart.setOnClickListener {
            OemAutoStartHelper.openAutoStartSettings(this, OemAutoStartHelper.Manufacturer.VIVO)
        }

        btnOppoAutostart.setOnClickListener {
            OemAutoStartHelper.openAutoStartSettings(this, OemAutoStartHelper.Manufacturer.OPPO_REALME)
        }

        btnSamsungAutostart.setOnClickListener {
            OemAutoStartHelper.openAutoStartSettings(this, OemAutoStartHelper.Manufacturer.SAMSUNG)
        }
    }

    private fun updateUIState() {
        val isEnabled = prefs.getBoolean(CallMonitorService.KEY_SERVICE_ENABLED, true)
        switchService.isChecked = isEnabled
        tvStatusText.text = if (isEnabled) {
            "CallReport is Active. Popup will appear when any call ends."
        } else {
            "CallReport is Paused. No popups or monitoring."
        }
    }
}
