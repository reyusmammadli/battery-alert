package com.batteryalert.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper

object Prefs {
    const val SERVICE_ON = "service_on"

    // виджет
    const val W_SIZE = "w_size"
    const val W_COLOR = "w_color"
    const val W_ALIGN = "w_align"
    const val W_BG_COLOR = "w_bg_color"
    const val W_BG_OPACITY = "w_bg_opacity"
    const val W_STATUS = "w_status"

    // оповещения
    const val ALERTS_ON = "alerts_on"
    const val HIGH_SET = "high_set"
    const val LOW_SET = "low_set"
    const val HIGH_SOUND = "high_sound"
    const val LOW_SOUND = "low_sound"

    val HIGH_LEVELS = intArrayOf(70, 75, 80, 85, 90)
    val LOW_LEVELS = intArrayOf(30, 25, 20, 15, 10)

    val COLOR_NAMES = listOf("Белый", "Жёлтый", "Зелёный", "Голубой", "Красный", "Оранжевый")
    val COLORS = intArrayOf(
        Color.WHITE,
        Color.parseColor("#FFEB3B"),
        Color.parseColor("#00E676"),
        Color.parseColor("#40C4FF"),
        Color.parseColor("#FF5252"),
        Color.parseColor("#FFA726")
    )

    val BG_NAMES = listOf("Чёрный", "Тёмно-серый", "Белый")
    val BG_COLORS = intArrayOf(
        Color.BLACK,
        Color.parseColor("#37474F"),
        Color.WHITE
    )

    val ALIGN_NAMES = listOf("Слева", "По центру", "Справа")

    fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun getSet(c: Context, key: String, def: Set<String>): Set<Int> {
        val raw: Set<String> = sp(c).getStringSet(key, def) ?: def
        return raw.mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun setSet(c: Context, key: String, v: Set<Int>) {
        sp(c).edit().putStringSet(key, v.map { it.toString() }.toHashSet()).apply()
    }

    fun pick(colors: IntArray, index: Int): Int = colors[index.coerceIn(0, colors.size - 1)]
}

object Sounder {
    val NAMES = listOf("Звук уведомления", "Будильник", "Мелодия звонка")
    private val handler = Handler(Looper.getMainLooper())

    fun play(ctx: Context, type: Int) {
        val rt = when (type) {
            0 -> RingtoneManager.TYPE_NOTIFICATION
            1 -> RingtoneManager.TYPE_ALARM
            else -> RingtoneManager.TYPE_RINGTONE
        }
        val uri = RingtoneManager.getDefaultUri(rt) ?: return
        val r = RingtoneManager.getRingtone(ctx.applicationContext, uri) ?: return
        r.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        r.play()
        handler.postDelayed({
            try {
                r.stop()
            } catch (e: Exception) {
            }
        }, 5000)
    }
}
