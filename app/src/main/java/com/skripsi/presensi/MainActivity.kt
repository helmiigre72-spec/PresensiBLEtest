package com.skripsi.presensi

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var etId: EditText
    private lateinit var btn: Button
    private lateinit var tvStatus: TextView
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("presensi", MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()

        etId = EditText(this).apply {
            hint = "ID Karyawan (angka)"
            inputType = InputType.TYPE_CLASS_NUMBER
            val saved = prefs.getLong("emp_id", 0L)
            if (saved > 0) setText(saved.toString())
        }
        btn = Button(this)
        tvStatus = TextView(this).apply { textSize = 16f; setPadding(0, pad, 0, 0) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
            addView(TextView(context).apply { text = "Presensi BLE"; textSize = 22f })
            addView(etId)
            addView(btn)
            addView(tvStatus)
        }
        setContentView(root)
        btn.setOnClickListener { if (BeaconService.running) stopBeacon() else startBeacon() }
    }

    override fun onResume() { super.onResume(); refreshUi() }

    private fun refreshUi() {
        btn.text = if (BeaconService.running) "BERHENTI" else "MULAI"
        tvStatus.text = "Status: ${BeaconService.status}"
        ui.postDelayed({ if (!isFinishing) refreshUi() }, 1000)
    }

    private fun startBeacon() {
        val id = etId.text.toString().toLongOrNull()
        if (id == null || id < 1 || id > 4294967295L) {
            tvStatus.text = "Isi ID karyawan (1 - 4294967295)"; return
        }
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            tvStatus.text = "HP tidak mendukung Bluetooth Low Energy"; return
        }
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            tvStatus.text = "Aktifkan Bluetooth dulu"; return
        }
        getSharedPreferences("presensi", MODE_PRIVATE).edit().putLong("emp_id", id).apply()

        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) need += Manifest.permission.BLUETOOTH_ADVERTISE
        if (Build.VERSION.SDK_INT >= 33) need += Manifest.permission.POST_NOTIFICATIONS
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 1)
        } else {
            launchService()
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        // Notifikasi boleh ditolak; izin Bluetooth wajib.
        val btOk = perms.indices.none {
            perms[it] == Manifest.permission.BLUETOOTH_ADVERTISE &&
                res[it] != PackageManager.PERMISSION_GRANTED
        }
        if (btOk) launchService() else tvStatus.text = "Izin Bluetooth ditolak"
    }

    private fun launchService() {
        askIgnoreBatteryOptimization()
        val i = Intent(this, BeaconService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        ui.postDelayed({ refreshUi() }, 600)
    }

    private fun stopBeacon() {
        stopService(Intent(this, BeaconService::class.java))
        ui.postDelayed({ refreshUi() }, 600)
    }

    private fun askIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT < 23) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$packageName"))
                )
            } catch (_: Exception) {}
        }
    }
}
