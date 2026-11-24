package com.batteryalert.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var sp: SharedPreferences
    private lateinit var root: LinearLayout
    private lateinit var permView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sp = Prefs.sp(this)

        val scroll = ScrollView(this)
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(24), dp(16), dp(40))
        scroll.addView(root)
        setContentView(scroll)

        build()
    }

    override fun onResume() {
        super.onResume()
        updatePermStatus()
    }

    // ---------- построение экрана ----------

    private fun build() {
        val h = TextView(this)
        h.text = "Заряд батареи"
        h.textSize = 30f
        h.typeface = Typeface.DEFAULT_BOLD
        root.addView(h, lp())

        addSwitch("Приложение включено", Prefs.SERVICE_ON, false) { on ->
            if (on) {
                askNotificationPermission()
                startSvc()
            } else {
                stopService(Intent(this, BatteryService::class.java))
            }
        }

        // ----- разрешения -----
        addTitle("Разрешения")
        permView = TextView(this)
        permView.textSize = 18f
        root.addView(permView, lp())
        addButton("1. Уведомления") { openNotificationSettings() }
        addButton("2. Без ограничений батареи") {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
                openAppDetails()
            }
        }
        addButton("3. Xiaomi: автозапуск") { openMiuiPermissions() }

        // ----- виджет -----
        addTitle("Виджет на рабочем столе")
        val how = TextView(this)
        how.textSize = 16f
        how.text = "Как добавить: долгое нажатие на пустое место рабочего стола → " +
                "«Виджеты» → «Заряд». Двигайте и растягивайте его пальцем, как любой виджет. " +
                "Ниже настройки внешнего вида, они применяются сразу."
        root.addView(how, lp())

        addSeek("Размер числа: %d", Prefs.W_SIZE, 20, 120, 60)
        addSpinner("Цвет числа", Prefs.COLOR_NAMES, Prefs.W_COLOR, 0)
        addSpinner("Выравнивание", Prefs.ALIGN_NAMES, Prefs.W_ALIGN, 1)
        addSpinner("Цвет фона", Prefs.BG_NAMES, Prefs.W_BG_COLOR, 0)
        addSeek("Непрозрачность фона: %d%%", Prefs.W_BG_OPACITY, 0, 100, 60)
        addSwitch("Показывать подпись «Заряжается»", Prefs.W_STATUS, true) { updateWidget() }

        // ----- оповещения -----
        addTitle("Звуковые оповещения")
        addSwitch("Оповещения включены", Prefs.ALERTS_ON, true) { }

        addLabel("Когда телефон заряжается — сигнал «снять с зарядки» на уровнях:")
        addChecks(Prefs.HIGH_LEVELS, Prefs.HIGH_SET, setOf("80"))
        addSpinner("Звук", Sounder.NAMES, Prefs.HIGH_SOUND, 0)
        addButton("Проверить этот звук") {
            Sounder.play(this, sp.getInt(Prefs.HIGH_SOUND, 0))
        }

        addLabel("Когда телефон разряжается — сигнал «поставить на зарядку» на уровнях:")
        addChecks(Prefs.LOW_LEVELS, Prefs.LOW_SET, setOf("20"))
        addSpinner("Звук", Sounder.NAMES, Prefs.LOW_SOUND, 1)
        addButton("Проверить этот звук") {
            Sounder.play(this, sp.getInt(Prefs.LOW_SOUND, 1))
        }

        val note = TextView(this)
        note.textSize = 16f
        note.text = "Сигнал звучит один раз. Он идёт через громкость будильника: " +
                "если она на нуле, звука не будет."
        root.addView(note, lp())
    }

    // ---------- помощники для элементов ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun lp(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        p.topMargin = dp(8)
        return p
    }

    private fun addTitle(text: String) {
        val t = TextView(this)
        t.text = text
        t.textSize = 22f
        t.typeface = Typeface.DEFAULT_BOLD
        val p = lp()
        p.topMargin = dp(28)
        root.addView(t, p)
    }

    private fun addLabel(text: String) {
        val t = TextView(this)
        t.text = text
        t.textSize = 18f
        val p = lp()
        p.topMargin = dp(16)
        root.addView(t, p)
    }

    private fun addButton(text: String, onClick: () -> Unit) {
        val b = Button(this)
        b.text = text
        b.textSize = 17f
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        root.addView(b, lp())
    }

    private fun addSwitch(
        text: String,
        key: String,
        def: Boolean,
        after: (Boolean) -> Unit
    ) {
        val s = Switch(this)
        s.text = text
        s.textSize = 20f
        s.isChecked = sp.getBoolean(key, def)
        s.setOnCheckedChangeListener { _, checked ->
            sp.edit().putBoolean(key, checked).apply()
            after(checked)
        }
        root.addView(s, lp())
    }

    private fun addSeek(titleFmt: String, key: String, min: Int, max: Int, def: Int) {
        val tv = TextView(this)
        tv.textSize = 18f
        val sb = SeekBar(this)
        sb.max = max - min
        val cur = sp.getInt(key, def).coerceIn(min, max)
        sb.progress = cur - min
        tv.text = titleFmt.format(cur)
        sb.setPadding(dp(16), dp(12), dp(16), dp(12))
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = p + min
                tv.text = titleFmt.format(v)
                if (fromUser) sp.edit().putInt(key, v).apply()
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}

            override fun onStopTrackingTouch(s: SeekBar?) {
                updateWidget()
            }
        })
        root.addView(tv, lp())
        root.addView(sb, lp())
    }

    private fun addSpinner(label: String, items: List<String>, key: String, def: Int) {
        addLabel(label)
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        spinner.setSelection(sp.getInt(key, def).coerceIn(0, items.size - 1))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (sp.getInt(key, def) != pos) {
                    sp.edit().putInt(key, pos).apply()
                    updateWidget()
                }
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        root.addView(spinner, lp())
    }

    private fun addChecks(levels: IntArray, key: String, def: Set<String>) {
        val cur = Prefs.getSet(this, key, def).toMutableSet()
        for (lv in levels) {
            val cb = CheckBox(this)
            cb.text = "$lv%"
            cb.textSize = 22f
            cb.isChecked = cur.contains(lv)
            cb.setOnCheckedChangeListener { _, checked ->
                if (checked) cur.add(lv) else cur.remove(lv)
                Prefs.setSet(this, key, cur)
            }
            root.addView(cb, lp())
        }
    }

    private fun updateWidget() {
        BatteryWidget.update(this)
    }

    // ---------- разрешения и служба ----------

    private fun updatePermStatus() {
        val notif = if (Build.VERSION.SDK_INT >= 33) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val pm = getSystemService(PowerManager::class.java)
        val battery = pm.isIgnoringBatteryOptimizations(packageName)
        permView.text = "Уведомления: ${mark(notif)}\n" +
                "Без ограничений батареи: ${mark(battery)}"
    }

    private fun mark(b: Boolean): String = if (b) "✅ да" else "❌ нет"

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 33) askNotificationPermission()
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        i.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        try {
            startActivity(i)
        } catch (e: Exception) {
            openAppDetails()
        }
    }

    private fun openAppDetails() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )
    }

    private fun openMiuiPermissions() {
        try {
            val i = Intent("miui.intent.action.APP_PERM_EDITOR")
            i.setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            i.putExtra("extra_pkgname", packageName)
            startActivity(i)
        } catch (e: Exception) {
            openAppDetails()
        }
    }

    private fun startSvc() {
        startForegroundService(Intent(this, BatteryService::class.java))
    }
}
