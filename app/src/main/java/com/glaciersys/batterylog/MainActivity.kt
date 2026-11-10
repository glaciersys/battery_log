package com.glaciersys.batterylog

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var pct: TextView
    private lateinit var state: TextView
    private lateinit var empty: TextView
    private var items: List<Store.Entry> = emptyList()

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("d MMM yyyy", Locale("th", "TH"))
    private val up = Color.parseColor("#0a8a4a")
    private val down = Color.parseColor("#c2410c")
    private val muted = Color.parseColor("#5d6b63")
    private val ink = Color.parseColor("#14201a")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            updateHeader()
            if (i.action == Store.ACTION_UPDATED) reload()
        }
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(p: Int) = items[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, cv: View?, parent: ViewGroup): View {
            val e = items[p]
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            val left = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            left.addView(TextView(this@MainActivity).apply {
                text = timeFmt.format(e.ts); textSize = 17f
                typeface = Typeface.DEFAULT_BOLD; setTextColor(ink)
            })
            left.addView(TextView(this@MainActivity).apply {
                text = dateFmt.format(e.ts); textSize = 12f; setTextColor(muted)
            })
            val mid = TextView(this@MainActivity).apply {
                text = if (e.to == e.from) "เริ่มที่ ${e.to}%" else "${e.from}% → ${e.to}%" + if (e.charging) "  ⚡" else ""
                textSize = 16f; setTextColor(muted); gravity = Gravity.CENTER
            }
            val d = e.to - e.from
            val right = TextView(this@MainActivity).apply {
                text = when { d > 0 -> "▲ +$d"; d < 0 -> "▼ $d"; else -> "•" }
                textSize = 17f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(when { d > 0 -> up; d < 0 -> down; else -> muted })
                gravity = Gravity.END
            }
            row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f))
            row.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f))
            row.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f))
            return row
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#f3f6f4"))
            setPadding(dp(16), dp(24), dp(16), dp(8))
        }
        pct = TextView(this).apply { text = "--%"; textSize = 64f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ink) }
        state = TextView(this).apply { textSize = 15f; setTextColor(muted) }
        root.addView(pct)
        root.addView(state)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "ล้างประวัติ"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage("ล้างประวัติทั้งหมด?")
                    .setPositiveButton("ล้าง") { _, _ -> Store.clear(this@MainActivity); updateHeader(); reload() }
                    .setNegativeButton("ยกเลิก", null).show()
            }
        })
        buttons.addView(Button(this).apply {
            text = "ตั้งค่าประหยัดแบต"
            setOnClickListener {
                try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) {}
            }
        })
        root.addView(buttons)

        empty = TextView(this).apply {
            text = "ยังไม่มีข้อมูล ระบบจะบันทึกทุกครั้งที่แบตเปลี่ยน"
            setTextColor(muted); setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        val list = ListView(this).apply {
            adapter = this@MainActivity.adapter
            emptyView = empty
            divider = null
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(empty)
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        startForegroundService(Intent(this, BatteryService::class.java))
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter().apply {
            addAction(Store.ACTION_UPDATED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(rx, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(rx, f)
        updateHeader()
        reload()
    }

    override fun onStop() {
        try { unregisterReceiver(rx) } catch (_: Exception) {}
        super.onStop()
    }

    private fun reload() {
        items = Store.readAll(this).take(1000)
        adapter.notifyDataSetChanged()
    }

    private fun updateHeader() {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (l >= 0 && s > 0) pct.text = "${l * 100 / s}%"
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        state.text = if (st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL)
            "กำลังชาร์จ" else "ไม่ได้ชาร์จ"
    }
}
