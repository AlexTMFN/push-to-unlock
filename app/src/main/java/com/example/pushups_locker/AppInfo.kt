package com.example.pushups_locker

import android.graphics.drawable.Drawable

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    var isLocked: Boolean = false,
    var timeLimitSeconds: Int = 300, // Default 5 mins
    var pushupsRequired: Int = 10
)