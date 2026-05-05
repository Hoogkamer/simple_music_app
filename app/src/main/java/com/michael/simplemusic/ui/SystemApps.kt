package com.michael.simplemusic.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.AlarmClock

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable?
)

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

    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val myPackage = context.packageName
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        return resolveInfos.mapNotNull {
            if (it.activityInfo.packageName == myPackage) return@mapNotNull null
            
            AppInfo(
                label = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName,
                icon = it.loadIcon(pm)
            )
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    fun launchApp(context: Context, packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(launchIntent)
        }
    }

    fun launchSystemAllApps(context: Context): Boolean {
        val intent = Intent("android.intent.action.ALL_APPS")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
