package com.glaciersys.batterylog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder

class BatteryService : Service() {

    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = handle(i)
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("battery", "บันทึกแบตเตอรี่", NotificationManager.IMPORTANCE_LOW)
        )
        val n = buildNotification("กำลังบันทึก…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        val sticky = registerReceiver(rx, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) handle(sticky)
    }

    private fun handle(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val pct = level * 100 / scale
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL

        if (Store.record(this, pct, charging)) {
            sendBroadcast(Intent(Store.ACTION_UPDATED).setPackage(packageName))
        }
        getSystemService(NotificationManager::class.java)
            .notify(1, buildNotification("แบตเตอรี่ $pct%" + if (charging) " (กำลังชาร์จ)" else ""))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, "battery")
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("บันทึกแบตเตอรี่")
            .setContentText(text)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        try { unregisterReceiver(rx) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
