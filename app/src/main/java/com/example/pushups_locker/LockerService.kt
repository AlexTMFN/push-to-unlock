package com.example.pushups_locker

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.util.*

class LockerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var usageStatsManager: UsageStatsManager
    
    private var lastRealAppPackage: String? = null
    private var lastRealAppName: String? = null
    private var isAppInForeground: Boolean = false
    
    // Memory cache for app timers: packageName -> secondsLeft
    private val appTimers = mutableMapOf<String, Int>()
    private var isCurrentlyLocked = false
    
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "LockerChannel"
    private var launcherPackage: String? = null

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.pushups_locker.UNLOCKED") {
                val packageName = intent.getStringExtra("package_name")
                if (packageName != null) {
                    Log.d("LockerService", "Resetting timer after unlock for: $packageName")
                    resetTimerForApp(packageName)
                    isCurrentlyLocked = false
                    updateNotification()
                }
            }
        }
    }

    private val monitorRunnable = object : Runnable {
        override fun run() {
            try {
                checkForegroundApp()
                updateTimerAndNotification()
            } catch (e: Exception) {
                Log.e("LockerService", "Error in monitor loop", e)
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        launcherPackage = getLauncherPackageName()
        createNotificationChannel()
        loadAllTimersFromPrefs()
        
        val filter = IntentFilter("com.example.pushups_locker.UNLOCKED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(unlockReceiver, filter)
        }

        startForeground(NOTIFICATION_ID, createNotification("Push-to-Unlock Active"))
        handler.post(monitorRunnable)
    }

    private fun getLauncherPackageName(): String? {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION") packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolveInfo?.activityInfo?.packageName
    }

    private fun checkForegroundApp() {
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - 3000, time)
        val event = UsageEvents.Event()
        
        var topPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                topPackage = event.packageName
            }
        }

        // Essential Fix: Only pause if it's explicitly the Launcher or an app that ISN'T SystemUI
        // Being in SystemUI (notification shade) should NOT change the 'isAppInForeground' state 
        // if we were already in a blocked app.
        if (topPackage != null) {
            val isLauncher = topPackage == launcherPackage || 
                             topPackage == "com.google.android.nexuslauncher" ||
                             topPackage == "com.google.android.googlequicksearchbox" ||
                             topPackage == "com.android.launcher3"

            val isSystemUI = topPackage == "com.android.systemui" || topPackage == "android"
            val isOurs = topPackage == packageName

            if (isLauncher) {
                isAppInForeground = false
            } else if (!isSystemUI && !isOurs) {
                // It's a real 3rd party app
                if (topPackage != lastRealAppPackage) {
                    lastRealAppPackage = topPackage
                    lastRealAppName = getAppNameFromPackage(topPackage)
                    ensureTimerExists(topPackage)
                }
                isAppInForeground = true
            }
            // If it is SystemUI or our app, we do NOT change isAppInForeground.
            // This ensures the timer keeps running if the user opens the shade while in Instagram.
        }
    }

    private fun updateTimerAndNotification() {
        if (lastRealAppPackage != null && isAppInForeground && !isCurrentlyLocked) {
            val timeLeft = appTimers[lastRealAppPackage!!] ?: -1
            if (timeLeft > 0) {
                val newTime = timeLeft - 1
                appTimers[lastRealAppPackage!!] = newTime
                saveTimerToPrefs(lastRealAppPackage!!, newTime)
                
                if (newTime <= 0) {
                    isCurrentlyLocked = true
                    lockApp(lastRealAppPackage!!)
                }
            } else if (timeLeft == 0) {
                isCurrentlyLocked = true
                lockApp(lastRealAppPackage!!)
            }
        }
        
        updateNotification()
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun ensureTimerExists(packageName: String) {
        if (!appTimers.containsKey(packageName)) {
            val prefs = getSharedPreferences("PushupsPrefs", Context.MODE_PRIVATE)
            val configStr = prefs.getString("locked_apps_config", "{}") ?: "{}"
            val configJson = JSONObject(configStr)
            
            if (configJson.has(packageName)) {
                // Try to load current progress first, otherwise load initial limit
                val savedProgress = prefs.getInt("timer_$packageName", -1)
                if (savedProgress != -1) {
                    appTimers[packageName] = savedProgress
                } else {
                    val limit = configJson.getJSONObject(packageName).optInt("timeLimit", 300)
                    appTimers[packageName] = limit
                }
            }
        }
    }

    private fun resetTimerForApp(packageName: String) {
        val prefs = getSharedPreferences("PushupsPrefs", Context.MODE_PRIVATE)
        val configStr = prefs.getString("locked_apps_config", "{}") ?: "{}"
        val configJson = JSONObject(configStr)

        if (configJson.has(packageName)) {
            val initialSeconds = configJson.getJSONObject(packageName).optInt("timeLimit", 300)
            appTimers[packageName] = initialSeconds
            saveTimerToPrefs(packageName, initialSeconds)
        }
    }

    private fun saveTimerToPrefs(packageName: String, seconds: Int) {
        getSharedPreferences("PushupsPrefs", Context.MODE_PRIVATE).edit()
            .putInt("timer_$packageName", seconds)
            .apply()
    }

    private fun loadAllTimersFromPrefs() {
        val prefs = getSharedPreferences("PushupsPrefs", Context.MODE_PRIVATE)
        val configStr = prefs.getString("locked_apps_config", "{}") ?: "{}"
        val configJson = JSONObject(configStr)
        configJson.keys().forEach { pkg ->
            val saved = prefs.getInt("timer_$pkg", -1)
            if (saved != -1) appTimers[pkg] = saved
        }
    }

    private fun updateNotification() {
        val message = when {
            isCurrentlyLocked -> "LOCKED! Complete your pushups for $lastRealAppName."
            lastRealAppPackage != null && appTimers.containsKey(lastRealAppPackage) -> {
                val time = appTimers[lastRealAppPackage] ?: 0
                val h = time / 3600
                val m = (time % 3600) / 60
                val s = time % 60
                val timeStr = if (h > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s) 
                              else String.format(Locale.getDefault(), "%02d:%02d", m, s)
                val status = if (isAppInForeground) "Active" else "Paused"
                "[$status] $lastRealAppName: $timeStr"
            }
            else -> "Push-to-Unlock: Monitoring..."
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Push-to-Unlock")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_pushups)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun lockApp(packageName: String) {
        val intent = Intent(this, LockerActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.putExtra("package_name", packageName)
        
        val prefs = getSharedPreferences("PushupsPrefs", Context.MODE_PRIVATE)
        val configJson = JSONObject(prefs.getString("locked_apps_config", "{}") ?: "{}")
        val pushups = if (configJson.has(packageName)) configJson.getJSONObject(packageName).optInt("pushups", 10) else 10
        intent.putExtra("target_pushups", pushups)
        
        startActivity(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Push-to-Unlock Service", NotificationManager.IMPORTANCE_HIGH)
            serviceChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(unlockReceiver) } catch (e: Exception) {}
        handler.removeCallbacks(monitorRunnable)
        super.onDestroy()
    }
}