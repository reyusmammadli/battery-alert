package com.batteryalert.app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class ChargingActivity : Activity() {

    private lateinit var pctView: TextView
    private lateinit var infoView: TextView
    private var preview = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            update(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preview = intent.getBooleanExtra("preview", false)

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val sp = Prefs.sp(this)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setBackgroundColor(Color.BLACK)

        pctView = TextView(this)
        pctView.textSize = sp.getInt(Prefs.CHARGE_SIZE, 110).toFloat()
        pctView.setTextColor(Prefs.color(this, Prefs.CHARGE_COLOR, 2))
        pctView.typeface = Typeface.DEFAULT_BOLD
        pctView.gravity = Gravity.CENTER
        pctView.setSingleLine(true)
        pctView.text = "--%"

        infoView = TextView(this)
        infoView.textSize = 28f
        infoView.setTextColor(Color.WHITE)
        infoView.gravity = Gravity.CENTER

        val hint = TextView(this)
        hint.textSize = 16f
        hint.setTextColor(Color.GRAY)
        hint.gravity = Gravity.CENTER
        hint.text = "Нажмите на экран, чтобы закрыть"

        layout.addView(
            pctView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        layout.addView(
            infoView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        val hintLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        hintLp.topMargin = (48 * resources.displayMetrics.density).toInt()
        layout.addView(hint, hintLp)

        layout.setOnClickListener { finish() }
        setContentView(layout)

        val sticky = registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) update(sticky)
    }

    private fun update(i: Intent) {
        val lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (lv >= 0 && scale > 0) {
            pctView.text = "${lv * 100 / scale}%"
        }
        infoView.text = if (plugged) "⚡ Заряжается" else "Не заряжается"
        if (!plugged && !preview) finish()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }
}
