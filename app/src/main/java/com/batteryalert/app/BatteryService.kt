package com.batteryalert.app

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
import android.graphics.drawable.Icon
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder

class BatteryService : Service() {

    companion object {
        const val CH_STATUS = "status"
        const val CH_ALERT = "alert"
        const val ID_STATUS = 1
        const val ID_ALERT = 2
    }

    private var registered = false
    private var level = -1
    private var charging = false
    private var lastLevel = -1
    private var lastCharging: Boolean? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) onBattery(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_STATUS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID_STATUS, n)
        }
        if (!registered) {
            val sticky = registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            registered = true
            if (sticky != null) onBattery(sticky)
        } else {
            BatteryWidget.update(this)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (registered) {
            try {
                unregisterReceiver(receiver)
            } catch (e: Exception) {
            }
            registered = false
        }
        super.onDestroy()
    }

    // ---------- события батареи ----------

    private fun onBattery(intent: Intent) {
        val lv = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (lv < 0 || scale <= 0) return
        val pct = lv * 100 / scale

        val prev = lastLevel
        val prevCharging = lastCharging

        level = pct
        charging = plugged

        if (prev != -1 && prevCharging == plugged && prev != pct) {
            checkAlerts(prev, pct, plugged)
        }

        lastLevel = pct
        lastCharging = plugged

        getSystemService(NotificationManager::class.java).notify(ID_STATUS, buildNotification())
        BatteryWidget.update(this)
    }

    private fun checkAlerts(prev: Int, now: Int, plugged: Boolean) {
        val sp = Prefs.sp(this)
        if (!sp.getBoolean(Prefs.ALERTS_ON, true)) return
        if (plugged) {
            val hit = Prefs.getSet(this, Prefs.HIGH_SET, setOf("80"))
                .filter { it > prev && it <= now }
            if (hit.isNotEmpty()) alert(true, now)
        } else {
            val hit = Prefs.getSet(this, Prefs.LOW_SET, setOf("20"))
                .filter { it >= now && it < prev }
            if (hit.isNotEmpty()) alert(false, now)
        }
    }

    private fun alert(high: Boolean, now: Int) {
        val sp = Prefs.sp(this)
        val type = if (high) sp.getInt(Prefs.HIGH_SOUND, 0) else sp.getInt(Prefs.LOW_SOUND, 1)
        Sounder.play(this, type)

        val text = if (high) {
            "Заряд $now%. Пора снять телефон с зарядки"
        } else {
            "Заряд $now%. Пора поставить телефон на зарядку"
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CH_ALERT)
            .setSmallIcon(Icon.createWithResource(this, R.drawable.ic_stat))
            .setContentTitle(if (high) "Снять с зарядки" else "Поставить на зарядку")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        getSystemService(NotificationManager::class.java).notify(ID_ALERT, n)
    }

    // ---------- уведомления ----------

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        val status = NotificationChannel(CH_STATUS, "Работа приложения", NotificationManager.IMPORTANCE_LOW)
        status.setShowBadge(false)
        nm.createNotificationChannel(status)
        val alert = NotificationChannel(CH_ALERT, "Оповещения о заряде", NotificationManager.IMPORTANCE_HIGH)
        alert.setSound(null, null)
        nm.createNotificationChannel(alert)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CH_STATUS)
            .setSmallIcon(Icon.createWithResource(this, R.drawable.ic_stat))
            .setContentTitle(if (level >= 0) "Заряд: $level%" else "Заряд батареи")
            .setContentText(if (charging) "Заряжается" else "Не заряжается")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(pi)
            .build()
    }
}
