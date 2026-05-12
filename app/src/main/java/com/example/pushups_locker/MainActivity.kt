package com.example.pushups_locker

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var appAdapter: AppAdapter
    private lateinit var apps: List<AppInfo>
    private lateinit var startFab: ExtendedFloatingActionButton
    private lateinit var stopFab: ExtendedFloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView: RecyclerView = findViewById(R.id.appsRecyclerView)
        startFab = findViewById(R.id.startServiceFab)
        stopFab = findViewById(R.id.stopServiceFab)
        val searchEditText: EditText = findViewById(R.id.searchEditText)

        apps = getInstalledApps()
        loadSettings()

        appAdapter = AppAdapter(
            apps,
            onAppClick = { app -> showConfigDialog(app) },
            onAppToggle = { _, _ -> saveSettings() }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = appAdapter

        updateServiceButtons()

        startFab.setOnClickListener {
            if (checkPermissions()) {
                startLockerService()
                updateServiceButtons()
            } else {
                requestPermissions()
            }
        }

        stopFab.setOnClickListener {
            stopLockerService()
            updateServiceButtons()
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                appAdapter.filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun updateServiceButtons() {
        if (isServiceRunning(LockerService::class.java)) {
            startFab.text = "Service Running"
            startFab.setIconResource(android.R.drawable.ic_media_pause)
            stopFab.visibility = View.VISIBLE
        } else {
            startFab.text = "Start Service"
            startFab.setIconResource(android.R.drawable.ic_media_play)
            stopFab.visibility = View.GONE
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun showConfigDialog(app: AppInfo) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_config_app, null)
        val nameText = dialogView.findViewById<TextView>(R.id.dialogAppName)
        val pickerH = dialogView.findViewById<NumberPicker>(R.id.pickerHours)
        val pickerM = dialogView.findViewById<NumberPicker>(R.id.pickerMinutes)
        val pickerS = dialogView.findViewById<NumberPicker>(R.id.pickerSeconds)
        val pushupsEdit = dialogView.findViewById<EditText>(R.id.editPushups)

        nameText.text = app.name
        
        pickerH.minValue = 0
        pickerH.maxValue = 23
        pickerM.minValue = 0
        pickerM.maxValue = 59
        pickerS.minValue = 0
        pickerS.maxValue = 59

        val h = app.timeLimitSeconds / 3600
        val m = (app.timeLimitSeconds % 3600) / 60
        val s = app.timeLimitSeconds % 60
        
        pickerH.value = h
        pickerM.value = m
        pickerS.value = s
        pushupsEdit.setText(app.pushupsRequired.toString())

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Configure Lock")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val totalSeconds = (pickerH.value * 3600) + (pickerM.value * 60) + pickerS.value
                app.timeLimitSeconds = if (totalSeconds < 1) 1 else totalSeconds
                app.pushupsRequired = pushupsEdit.text.toString().toIntOrNull() ?: 10
                saveSettings()
                appAdapter.notifyDataSetChanged()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return installedApps.filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map {
                AppInfo(
                    it.loadLabel(pm).toString(),
                    it.packageName,
                    it.loadIcon(pm)
                )
            }.sortedBy { it.name }
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("PushupsPrefs", Context.MODE_PRIVATE)
        val lockedAppsJson = JSONObject()
        
        apps.filter { it.isLocked }.forEach {
            val appJson = JSONObject()
            appJson.put("timeLimit", it.timeLimitSeconds)
            appJson.put("pushups", it.pushupsRequired)
            lockedAppsJson.put(it.packageName, appJson)
        }

        prefs.edit().putString("locked_apps_config", lockedAppsJson.toString()).apply()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("PushupsPrefs", Context.MODE_PRIVATE)
        val configStr = prefs.getString("locked_apps_config", "{}") ?: "{}"
        val configJson = JSONObject(configStr)

        apps.forEach { app ->
            if (configJson.has(app.packageName)) {
                val appJson = configJson.getJSONObject(app.packageName)
                app.isLocked = true
                app.timeLimitSeconds = appJson.optInt("timeLimit", 300)
                app.pushupsRequired = appJson.optInt("pushups", 10)
            }
        }
    }

    private fun checkPermissions(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        
        val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val cameraPermission = checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        return mode == AppOpsManager.MODE_ALLOWED && Settings.canDrawOverlays(this) && notificationPermission && cameraPermission
    }

    private fun requestPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        if (mode != AppOpsManager.MODE_ALLOWED) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 102)
        }
    }

    private fun startLockerService() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            startActivity(intent)
        }

        val intent = Intent(this, LockerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Service Started", Toast.LENGTH_SHORT).show()
    }

    private fun stopLockerService() {
        val intent = Intent(this, LockerService::class.java)
        stopService(intent)
        Toast.makeText(this, "Service Stopped", Toast.LENGTH_SHORT).show()
    }
}