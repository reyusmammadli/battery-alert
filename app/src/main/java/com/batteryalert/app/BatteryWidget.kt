package com.batteryalert.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews

class BatteryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        BatteryWidget.update(context)
    }

    override fun onEnabled(context: Context) {
        if (Prefs.sp(context).getBoolean(Prefs.SERVICE_ON, false)) {
            try {
                context.startForegroundService(Intent(context, BatteryService::class.java))
            } catch (e: Exception) {
            }
        }
    }
}

object BatteryWidget {

    fun update(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, BatteryWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return
        val lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (lv < 0 || scale <= 0) return
        val pct = lv * 100 / scale

        val sp = Prefs.sp(context)
        val size = sp.getInt(Prefs.W_SIZE, 60)
        val textColor = Prefs.pick(Prefs.COLORS, sp.getInt(Prefs.W_COLOR, 0))
        val bgBase = Prefs.pick(Prefs.BG_COLORS, sp.getInt(Prefs.W_BG_COLOR, 0))
        val opacity = sp.getInt(Prefs.W_BG_OPACITY, 60).coerceIn(0, 100)
        val showStatus = sp.getBoolean(Prefs.W_STATUS, true)

        val layout = when (sp.getInt(Prefs.W_ALIGN, 1)) {
            0 -> R.layout.widget_left
            1 -> R.layout.widget_center
            else -> R.layout.widget_right
        }

        val rv = RemoteViews(context.packageName, layout)
        rv.setTextViewText(R.id.pct, "$pct%")
        rv.setTextViewTextSize(R.id.pct, TypedValue.COMPLEX_UNIT_SP, size.toFloat())
        rv.setTextColor(R.id.pct, textColor)

        rv.setTextViewText(R.id.info, if (plugged) "⚡ Заряжается" else "Не заряжается")
        rv.setTextViewTextSize(R.id.info, TypedValue.COMPLEX_UNIT_SP, maxOf(12f, size * 0.28f))
        rv.setTextColor(R.id.info, textColor)
        rv.setViewVisibility(R.id.info, if (showStatus) View.VISIBLE else View.GONE)

        rv.setInt(R.id.bg, "setColorFilter", bgBase)
        rv.setInt(R.id.bg, "setImageAlpha", opacity * 255 / 100)

        val pi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        rv.setOnClickPendingIntent(R.id.root, pi)

        mgr.updateAppWidget(ids, rv)
    }
}
