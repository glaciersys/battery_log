package com.glaciersys.batterylog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            context.startForegroundService(Intent(context, BatteryService::class.java))
        } catch (_: Exception) {
            // Some Android versions block this; opening the app once restarts logging.
        }
    }
}
