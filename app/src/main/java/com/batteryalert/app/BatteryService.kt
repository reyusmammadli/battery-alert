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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class BatteryService : Service() {

    companion object {
        const val CH_STATUS = "status"
        const val CH_ALERT = "alert"
        const val ID_STATUS = 1
        const val ID_ALERT = 2
    }

    private lateinit var wm: WindowManager
    private var overlayView: TextView? = null
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
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
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
            refreshUi()
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
        removeOverlay()
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
        if (prevCharging == false && plugged) {
            showChargeScreen()
        }

        lastLevel = pct
        lastCharging = plugged
        refreshUi()
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

    private fun showChargeScreen() {
        val sp = Prefs.sp(this)
        if (!sp.getBoolean(Prefs.CHARGE_SCREEN_ON, true)) return
        if (!Settings.canDrawOverlays(this)) return
        try {
            val i = Intent(this, ChargingActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(i)
        } catch (e: Exception) {
        }
    }

    // ---------- интерфейс ----------

    private fun refreshUi() {
        getSystemService(NotificationManager::class.java).notify(ID_STATUS, buildNotification())
        updateOverlay()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        val status = NotificationChannel(CH_STATUS, "Индикатор заряда", NotificationManager.IMPORTANCE_LOW)
        status.setShowBadge(false)
        nm.createNotificationChannel(status)
        val alert = NotificationChannel(CH_ALERT, "Оповещения о заряде", NotificationManager.IMPORTANCE_HIGH)
        alert.setSound(null, null)
        nm.createNotificationChannel(alert)
    }

    private fun buildNotification(): Notification {
        val sp = Prefs.sp(this)
        val showNumber = sp.getBoolean(Prefs.STATUS_ICON_ON, true) && level >= 0
        val icon = if (showNumber) {
            numberIcon(level.toString())
        } else {
            Icon.createWithResource(this, R.drawable.ic_stat)
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CH_STATUS)
            .setSmallIcon(icon)
            .setContentTitle(if (level >= 0) "Заряд: $level%" else "Заряд батареи")
            .setContentText(if (charging) "Заряжается" else "Не заряжается")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(pi)
            .build()
    }

    private fun numberIcon(text: String): Icon {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.WHITE
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = 90f
        val w = p.measureText(text)
        if (w > size) p.textSize = p.textSize * size / w
        val y = size / 2f - (p.descent() + p.ascent()) / 2f
        canvas.drawText(text, size / 2f, y, p)
        return Icon.createWithBitmap(bmp)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun removeOverlay() {
        overlayView?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
            }
        }
        overlayView = null
    }

    private fun updateOverlay() {
        val sp = Prefs.sp(this)
        val enabled = sp.getBoolean(Prefs.OVERLAY_ON, true) &&
                Settings.canDrawOverlays(this) && level >= 0
        if (!enabled) {
            removeOverlay()
            return
        }
        val tv = overlayView ?: TextView(this).also { overlayView = it }
        tv.text = (if (charging) "⚡" else "") + "$level%"
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp.getInt(Prefs.OVERLAY_SIZE, 40).toFloat())
        tv.setTextColor(Prefs.color(this, Prefs.OVERLAY_COLOR, 1))
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setShadowLayer(8f, 0f, 0f, Color.BLACK)
        tv.setBackgroundColor(0x99000000.toInt())
        tv.setPadding(dp(10), dp(2), dp(10), dp(2))

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        val horizontal = when (sp.getInt(Prefs.OVERLAY_POS, 2)) {
            0 -> Gravity.START
            1 -> Gravity.CENTER_HORIZONTAL
            else -> Gravity.END
        }
        lp.gravity = Gravity.TOP or horizontal
        lp.y = dp(sp.getInt(Prefs.OVERLAY_Y, 0))

        try {
            if (tv.parent == null) wm.addView(tv, lp) else wm.updateViewLayout(tv, lp)
        } catch (e: Exception) {
        }
    }
}
