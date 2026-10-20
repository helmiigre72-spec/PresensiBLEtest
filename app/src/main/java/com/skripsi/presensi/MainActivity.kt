package com.skripsi.presensi

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/**
 * 3 halaman: Login, Beranda, Pengaturan.
 * Gaya (warna, ukuran) ada di Ui.kt. Susunan tiap halaman ada di
 * showLogin(), buildHome(), dan buildSettings() di bawah.
 */
class MainActivity : Activity() {

    companion object {
        const val DEFAULT_SERVER = "http://192.168.1.2:8000"
    }

    private lateinit var prefs: SharedPreferences
    private val ui = Handler(Looper.getMainLooper())
    private var inMain = false
    private var tab = 0

    private lateinit var content: FrameLayout
    private lateinit var navHome: TextView
    private lateinit var navSet: TextView

    // View halaman Beranda (null jika halaman lain yang tampil)
    private var btn: Button? = null
    private var dot: View? = null
    private var tvStatus: TextView? = null
    private var tvMsg: TextView? = null
    private var tvIn: TextView? = null
    private var tvOut: TextView? = null
    private var tvDur: TextView? = null
    private var historyBox: LinearLayout? = null

    private var lastCheckIn: String? = null
    private var loadedOnce = false
    private var ticks = 0
    private val tick = object : Runnable {
        override fun run() {
            refreshUi()
            if (ticks % 15 == 0) loadData()
            ticks++
            ui.postDelayed(this, 1000)
        }
    }

    // ---------- util ----------
    private fun <T> bg(work: () -> T, done: (Result<T>) -> Unit) {
        Thread {
            val r: Result<T> = try { Result.success(work()) } catch (e: Exception) { Result.failure(e) }
            runOnUiThread { if (!isFinishing) done(r) }
        }.start()
    }

    private fun errText(e: Throwable): String =
        if (e is Api.ApiError) (e.message ?: "Gagal") else "Tidak dapat terhubung ke server"

    private fun deviceId(): String =
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    private fun fmtDur(m: Int) = "${m / 60}j ${m % 60}m"

    // ---------- siklus hidup ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("presensi", MODE_PRIVATE)
        window.statusBarColor = Ui.PRIMARY
        val loggedIn = prefs.getBoolean("demo", false) || !prefs.getString("token", "").isNullOrEmpty()
        if (loggedIn) showMain() else showLogin()
    }

    override fun onResume() { super.onResume(); if (inMain && tab == 0) startTick() }
    override fun onPause() { super.onPause(); stopTick() }

    private fun startTick() { ticks = 0; ui.removeCallbacks(tick); ui.post(tick) }
    private fun stopTick() { ui.removeCallbacks(tick) }

    // =====================================================
    // HALAMAN 1: LOGIN
    // =====================================================
    private fun showLogin() {
        inMain = false
        stopTick()
        val c = this
        val etCode = Ui.input(c, "ID Karyawan / NIK")
        val etPass = Ui.input(c, "Kata sandi", password = true)
        val etServer = Ui.input(c, DEFAULT_SERVER, uri = true)
        etServer.setText(prefs.getString("server", DEFAULT_SERVER))
        val msg = Ui.text(c, "", 13f, Ui.RED).apply { visibility = View.GONE }
        val btnLogin = Ui.button(c, "MASUK", Ui.PRIMARY)
        val demo = Ui.text(c, "Coba mode demo (tanpa server)", 14f, Ui.PRIMARY, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8))
        }

        fun fail(t: String) { msg.text = t; msg.visibility = View.VISIBLE }

        btnLogin.setOnClickListener {
            val code = etCode.text.toString().trim()
            val pass = etPass.text.toString()
            val server = etServer.text.toString().trim()
            if (code.isEmpty() || pass.isEmpty() || server.isEmpty()) {
                fail("Lengkapi ID, kata sandi, dan alamat server")
                return@setOnClickListener
            }
            msg.visibility = View.GONE
            btnLogin.isEnabled = false
            btnLogin.text = "Memproses..."
            prefs.edit().putString("server", server).apply()
            bg({ Api.login(server, code, pass, deviceId()) }) { r ->
                btnLogin.isEnabled = true
                btnLogin.text = "MASUK"
                r.onSuccess { j ->
                    prefs.edit()
                        .putString("token", j.getString("token"))
                        .putString("name", j.getString("name"))
                        .putString("code", code)
                        .putLong("emp_id", j.getLong("beacon_id"))
                        .putBoolean("demo", false)
                        .apply()
                    showMain()
                }.onFailure { e -> fail(errText(e)) }
            }
        }
        demo.setOnClickListener {
            prefs.edit()
                .putBoolean("demo", true)
                .putString("token", "")
                .putString("name", "Pengguna Demo")
                .putString("code", "DEMO")
                .putLong("emp_id", 1L)
                .apply()
            showMain()
        }

        val header = Ui.header(c, Ui.APP_NAME, "Masuk untuk mulai mencatat kehadiran")
        val card = Ui.card(
            c, "Masuk",
            listOf(
                etCode, etPass,
                Ui.text(c, "Alamat server (diisi tim IT)", 12f, Ui.MUTED), etServer,
                msg, btnLogin, demo
            )
        )
        setContentView(Ui.page(c, header, listOf(card)))
    }

    // =====================================================
    // KERANGKA: konten + navigasi bawah (Beranda | Pengaturan)
    // =====================================================
    private fun showMain() {
        inMain = true
        val c = this
        content = FrameLayout(c)
        navHome = navItem("Beranda") { selectTab(0) }
        navSet = navItem("Pengaturan") { selectTab(1) }
        val nav = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            elevation = Ui.dp(c, 8).toFloat()
            addView(navHome, LinearLayout.LayoutParams(0, Ui.dp(c, 56), 1f))
            addView(navSet, LinearLayout.LayoutParams(0, Ui.dp(c, 56), 1f))
        }
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(
                nav,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        setContentView(root)
        selectTab(tab)
    }

    private fun navItem(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
    }

    private fun selectTab(i: Int) {
        tab = i
        stopTick()
        navHome.setTextColor(if (i == 0) Ui.PRIMARY else Ui.MUTED)
        navHome.typeface = if (i == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        navSet.setTextColor(if (i == 1) Ui.PRIMARY else Ui.MUTED)
        navSet.typeface = if (i == 1) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        content.removeAllViews()
        if (i == 0) {
            content.addView(buildHome())
            startTick()
        } else {
            btn = null; dot = null; tvStatus = null; tvMsg = null
            tvIn = null; tvOut = null; tvDur = null; historyBox = null
            content.addView(buildSettings())
        }
    }

    // =====================================================
    // HALAMAN 2: BERANDA
    // =====================================================
    private fun buildHome(): View {
        val c = this
        val name = prefs.getString("name", "") ?: ""
        val code = prefs.getString("code", "") ?: ""

        val d = View(c)
        val st = Ui.text(c, "", 15f, Ui.TEXT)
        val statusRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(d, LinearLayout.LayoutParams(Ui.dp(c, 12), Ui.dp(c, 12)).apply { rightMargin = Ui.dp(c, 10) })
            addView(st)
        }
        val b = Ui.button(c, "MULAI", Ui.GREEN)
        b.setOnClickListener { if (BeaconService.running) stopBeacon() else startBeacon() }
        val m = Ui.text(c, "", 13f, Ui.RED).apply { visibility = View.GONE }
        dot = d; tvStatus = st; btn = b; tvMsg = m

        val (rIn, vIn) = Ui.kv(c, "Jam masuk")
        val (rOut, vOut) = Ui.kv(c, "Jam pulang")
        val (rDur, vDur) = Ui.kv(c, "Durasi")
        tvIn = vIn; tvOut = vOut; tvDur = vDur
        val hist = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        historyBox = hist

        val header = Ui.header(c, "Halo, $name", "ID: $code")
        refreshUi()
        return Ui.page(
            c, header,
            listOf(
                Ui.card(c, "Pemancar sinyal", listOf(statusRow, b, m)),
                Ui.card(c, "Kehadiran hari ini", listOf(rIn, rOut, rDur)),
                Ui.card(c, "Riwayat 7 hari", listOf(hist))
            )
        )
    }

    private fun refreshUi() {
        val b = btn ?: return
        val on = BeaconService.running
        b.text = if (on) "BERHENTI" else "MULAI"
        b.background = Ui.rounded(this, if (on) Ui.RED else Ui.GREEN, 12)
        val s = BeaconService.status
        tvStatus?.text = s
        dot?.background = Ui.oval(
            when (s) {
                "Memancar" -> Ui.GREEN
                "Berhenti" -> Ui.GRAY
                else -> Ui.ORANGE
            }
        )
    }

    private fun showMsg(t: String) {
        tvMsg?.let {
            it.text = t
            it.visibility = if (t.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun loadData() {
        if (prefs.getBoolean("demo", false)) {
            applyToday(
                JSONObject().put("check_in", "07:58")
                    .put("check_out", JSONObject.NULL)
                    .put("duration_minutes", JSONObject.NULL)
            )
            applyHistory(demoHistory())
            return
        }
        val server = prefs.getString("server", "") ?: ""
        val token = prefs.getString("token", "") ?: ""
        bg({ Pair(Api.today(server, token), Api.history(server, token)) }) { r ->
            r.onSuccess { (t, h) ->
                applyToday(t)
                applyHistory(h)
                showMsg("")
            }.onFailure { e ->
                if (e is Api.ApiError && e.code == 401) logout()
                else showMsg("Data belum dimuat: " + errText(e))
            }
        }
    }

    private fun demoHistory(): JSONArray {
        val a = JSONArray()
        val rows = listOf(
            Triple("20 Okt", "07:55", "17:03"),
            Triple("19 Okt", "08:02", "17:10"),
            Triple("18 Okt", "07:49", "16:58")
        )
        for ((d, i, o) in rows) {
            a.put(JSONObject().put("date", d).put("check_in", i).put("check_out", o).put("duration_minutes", 548))
        }
        return a
    }

    private fun applyToday(t: JSONObject) {
        tvIn?.text = t.str("check_in")
        tvOut?.text = t.str("check_out")
        tvDur?.text = if (t.isNull("duration_minutes")) "-" else fmtDur(t.getInt("duration_minutes"))
        val ci = if (t.isNull("check_in")) null else t.getString("check_in")
        if (loadedOnce && lastCheckIn == null && ci != null) notifyCheckIn(ci)
        lastCheckIn = ci
        loadedOnce = true
    }

    private fun applyHistory(h: JSONArray) {
        val box = historyBox ?: return
        box.removeAllViews()
        if (h.length() == 0) {
            box.addView(Ui.text(this, "Belum ada data", 14f, Ui.MUTED))
            return
        }
        for (i in 0 until h.length()) {
            val o = h.getJSONObject(i)
            val dur = if (o.isNull("duration_minutes")) "" else " (" + fmtDur(o.getInt("duration_minutes")) + ")"
            box.addView(Ui.kv(this, o.str("date"), o.str("check_in") + " - " + o.str("check_out") + dur).first)
        }
    }

    private fun notifyCheckIn(time: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("info", "Info kehadiran", NotificationManager.IMPORTANCE_DEFAULT)
            )
            Notification.Builder(this, "info")
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        nm.notify(
            2,
            builder.setContentTitle("Kehadiran tercatat")
                .setContentText("Jam masuk $time")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .build()
        )
    }

    // ---------- kontrol pemancar ----------
    private fun startBeacon() {
        val id = prefs.getLong("emp_id", 0L)
        if (id < 1) { showMsg("ID beacon belum tersedia, silakan masuk ulang"); return }
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            showMsg("HP tidak mendukung Bluetooth Low Energy"); return
        }
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) { showMsg("Aktifkan Bluetooth dulu"); return }
        showMsg("")

        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) need += Manifest.permission.BLUETOOTH_ADVERTISE
        if (Build.VERSION.SDK_INT >= 33) need += Manifest.permission.POST_NOTIFICATIONS
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1) else launchService()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        // Notifikasi boleh ditolak; izin Bluetooth wajib.
        val btOk = perms.indices.none {
            perms[it] == Manifest.permission.BLUETOOTH_ADVERTISE &&
                res[it] != PackageManager.PERMISSION_GRANTED
        }
        if (btOk) launchService() else showMsg("Izin Bluetooth ditolak")
    }

    private fun launchService() {
        askIgnoreBatteryOptimization(false)
        val i = Intent(this, BeaconService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        ui.postDelayed({ refreshUi() }, 600)
    }

    private fun stopBeacon() {
        stopService(Intent(this, BeaconService::class.java))
        ui.postDelayed({ refreshUi() }, 600)
    }

    private fun askIgnoreBatteryOptimization(manual: Boolean) {
        if (Build.VERSION.SDK_INT < 23) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            if (manual) Toast.makeText(this, "Sudah dikecualikan dari penghemat baterai", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {}
    }

    // =====================================================
    // HALAMAN 3: PENGATURAN
    // =====================================================
    private fun buildSettings(): View {
        val c = this
        val demo = prefs.getBoolean("demo", false)
        val rows: List<View> = listOf(
            Ui.kv(c, "Nama", prefs.getString("name", "-") ?: "-").first,
            Ui.kv(c, "ID Karyawan", prefs.getString("code", "-") ?: "-").first,
            Ui.kv(c, "ID Perangkat", deviceId().take(8)).first,
            Ui.kv(c, "Mode", if (demo) "Demo" else "Terhubung server").first
        )
        val btnOut = Ui.button(c, "KELUAR", Ui.RED)
        btnOut.setOnClickListener { logout() }

        val etServer = Ui.input(c, DEFAULT_SERVER, uri = true)
        etServer.setText(prefs.getString("server", DEFAULT_SERVER))
        val srvMsg = Ui.text(c, "", 13f, Ui.MUTED).apply { visibility = View.GONE }
        fun say(t: String, color: Int) {
            srvMsg.text = t
            srvMsg.setTextColor(color)
            srvMsg.visibility = View.VISIBLE
        }
        val btnSave = Ui.button(c, "SIMPAN", Ui.PRIMARY)
        btnSave.setOnClickListener {
            prefs.edit().putString("server", etServer.text.toString().trim()).apply()
            say("Tersimpan", Ui.GREEN)
        }
        val btnTest = Ui.button(c, "TES KONEKSI", Ui.MUTED)
        btnTest.setOnClickListener {
            val s = etServer.text.toString().trim()
            say("Menghubungi server...", Ui.MUTED)
            bg({ Api.ping(s) }) { r ->
                r.onSuccess { say("Terhubung ke server", Ui.GREEN) }
                    .onFailure { e -> say(errText(e), Ui.RED) }
            }
        }

        val help = Ui.text(
            c,
            "Jika pancaran sering berhenti, izinkan aplikasi berjalan di latar belakang dan aktifkan Autostart (Xiaomi/Oppo/Vivo).",
            13f, Ui.MUTED
        )
        val btnBatt = Ui.button(c, "ATUR PENGHEMAT BATERAI", Ui.PRIMARY)
        btnBatt.setOnClickListener { askIgnoreBatteryOptimization(true) }
        val btnInfo = Ui.button(c, "BUKA INFO APLIKASI", Ui.PRIMARY)
        btnInfo.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName"))
            )
        }
        val ver = Ui.text(c, "Versi 0.2", 12f, Ui.MUTED).apply { gravity = Gravity.CENTER }

        val header = Ui.header(c, "Pengaturan", "Akun, server, dan perangkat")
        return Ui.page(
            c, header,
            listOf(
                Ui.card(c, "Akun", rows + btnOut),
                Ui.card(c, "Server", listOf(etServer, btnSave, btnTest, srvMsg)),
                Ui.card(c, "Latar belakang", listOf(help, btnBatt, btnInfo)),
                ver
            )
        )
    }

    private fun logout() {
        stopService(Intent(this, BeaconService::class.java))
        prefs.edit().remove("token").putBoolean("demo", false).apply()
        lastCheckIn = null
        loadedOnce = false
        tab = 0
        showLogin()
    }
}
