package com.batteryalert.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.sp(context).getBoolean(Prefs.SERVICE_ON, false)) {
            try {
                context.startForegroundService(Intent(context, BatteryService::class.java))
            } catch (e: Exception) {
            }
        }
    }
}
