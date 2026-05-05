package com.michael.simplemusic.ui

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

object SystemApps {
    fun launchClock(context: Context) {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: search for any app with "clock" in package name
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(0)
            val clockPackage = packages.find { 
                it.packageName.contains("clock", ignoreCase = true) || 
                it.packageName.contains("alarm", ignoreCase = true) 
            }
            if (clockPackage != null) {
                val launchIntent = pm.getLaunchIntentForPackage(clockPackage.packageName)
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                }
            }
        }
    }
}
