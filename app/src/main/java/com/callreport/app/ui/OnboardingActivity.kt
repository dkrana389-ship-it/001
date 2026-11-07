package com.callreport.app.ui

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.callreport.app.R
import com.callreport.app.service.CallMonitorService
import com.callreport.app.util.PermissionHelper
import com.callreport.app.util.PermissionItem
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * Single onboarding screen that walks the user through permissions one by one.
 * 
 * Features (STEP 1 & STEP 2):
 * - Presents short Hindi and English reason for each permission
 * - Requests ROLE_CALL_SCREENING via RoleManager intent
 * - Launches overlay settings for SYSTEM_ALERT_WINDOW
 * - Starts CallMonitorService automatically once permissions are granted
 * - Provides access to SettingsActivity
 */
class OnboardingActivity : AppCompatActivity() {

    private var currentPermissionIndex = 0
    private var permissionList: List<PermissionItem> = emptyList()

    private lateinit var cardPermission: MaterialCardView
    private lateinit var tvStepIndicator: TextView
    private lateinit var tvPermissionTitle: TextView
    private lateinit var tvPurpose: TextView
    private lateinit var tvDetail: TextView
    private lateinit var btnGrant: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var cardSuccess: MaterialCardView
    private lateinit var tvSuccessStatus: TextView

    // Modern Android ActivityResult Launchers
    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        refreshAndAdvance()
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        refreshAndAdvance()
    }

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Call Screening Role Granted", Toast.LENGTH_SHORT).show()
        }
        refreshAndAdvance()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        initViews()
        refreshPermissionState()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    private fun initViews() {
        cardPermission = findViewById(R.id.cardPermission)
        tvStepIndicator = findViewById(R.id.tvStepIndicator)
        tvPermissionTitle = findViewById(R.id.tvPermissionTitle)
        tvPurpose = findViewById(R.id.tvPurpose)
        tvDetail = findViewById(R.id.tvDetail)
        btnGrant = findViewById(R.id.btnGrant)
        btnSettings = findViewById(R.id.btnSettings)
        cardSuccess = findViewById(R.id.cardSuccess)
        tvSuccessStatus = findViewById(R.id.tvSuccessStatus)

        btnGrant.setOnClickListener {
            requestCurrentPermission()
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun refreshPermissionState() {
        permissionList = PermissionHelper.getPermissionList(this)
        
        // Find the first ungranted permission index
        val firstUngrantedIndex = permissionList.indexOfFirst { !it.isGranted }

        if (firstUngrantedIndex == -1) {
            // All permissions granted!
            showAllPermissionsGranted()
        } else {
            currentPermissionIndex = firstUngrantedIndex
            showPermissionStep(permissionList[currentPermissionIndex])
        }
    }

    private fun showPermissionStep(item: PermissionItem) {
        cardPermission.visibility = View.VISIBLE
        cardSuccess.visibility = View.GONE

        val total = permissionList.size
        val currentStep = currentPermissionIndex + 1
        tvStepIndicator.text = "Permission $currentStep of $total"
        tvPermissionTitle.text = item.title
        tvPurpose.text = item.purpose
        tvDetail.text = item.detail
        btnGrant.text = "Allow Access"
    }

    private fun showAllPermissionsGranted() {
        cardPermission.visibility = View.GONE
        cardSuccess.visibility = View.VISIBLE
        tvSuccessStatus.text = "All required permissions are active. CallReport is monitoring calls."

        // Start CallMonitorService foreground service
        CallMonitorService.startService(this)
    }

    private fun requestCurrentPermission() {
        if (currentPermissionIndex >= permissionList.size) return
        val item = permissionList[currentPermissionIndex]

        when (item.id) {
            PermissionHelper.ID_PHONE_STATE -> {
                runtimePermissionLauncher.launch(android.Manifest.permission.READ_PHONE_STATE)
            }
            PermissionHelper.ID_CALL_LOG -> {
                runtimePermissionLauncher.launch(android.Manifest.permission.READ_CALL_LOG)
            }
            PermissionHelper.ID_CONTACTS -> {
                runtimePermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
            }
            PermissionHelper.ID_POST_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    runtimePermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    refreshAndAdvance()
                }
            }
            PermissionHelper.ID_SYSTEM_ALERT_WINDOW -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    overlayPermissionLauncher.launch(intent)
                } else {
                    refreshAndAdvance()
                }
            }
            PermissionHelper.ID_ROLE_CALL_SCREENING -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
                    if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                        roleRequestLauncher.launch(intent)
                    } else {
                        refreshAndAdvance()
                    }
                } else {
                    refreshAndAdvance()
                }
            }
        }
    }

    private fun refreshAndAdvance() {
        refreshPermissionState()
    }
}
