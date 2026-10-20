package com.skripsi.presensi

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import java.nio.ByteBuffer
import java.util.UUID

/** Foreground service: memancarkan paket BLE (format iBeacon) berisi ID karyawan. */
@SuppressLint("MissingPermission")
class BeaconService : Service() {

    companion object {
        @Volatile var running = false
        @Volatile var status = "Berhenti"

        // UUID aplikasi: HARUS sama dengan APP_UUID di gateway.
        val APP_UUID: UUID = UUID.fromString("5A4BCFCE-174E-4BAC-A814-092E77F6B7E5")
        private const val CHANNEL = "beacon"
        private const val APPLE_ID = 0x004C
    }

    private var advertiser: BluetoothLeAdvertiser? = null
    private var receiverRegistered = false

    private val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) { status = "Memancar" }
        override fun onStartFailure(errorCode: Int) { status = "Gagal memancar (kode $errorCode)" }
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                BluetoothAdapter.STATE_ON -> startAdv()
                BluetoothAdapter.STATE_TURNING_OFF -> { stopAdv(); status = "Bluetooth dimatikan" }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(btReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        receiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Presensi", NotificationManager.IMPORTANCE_LOW)
            )
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        val notif = builder
            .setContentTitle("Presensi aktif")
            .setContentText("Memancarkan sinyal kehadiran")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(1, notif)
        }
        running = true
        startAdv()
        return START_STICKY
    }

    private fun startAdv() {
        stopAdv()
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) { status = "Bluetooth mati"; return }
        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) { status = "HP tidak mendukung BLE advertising"; return }

        val id = getSharedPreferences("presensi", MODE_PRIVATE).getLong("emp_id", 0L)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(APPLE_ID, ibeacon(id))
            .build()
        advertiser?.startAdvertising(settings, data, callback)
    }

    private fun stopAdv() {
        try { advertiser?.stopAdvertising(callback) } catch (_: Exception) {}
    }

    /** Format iBeacon: 02 15 | UUID(16) | Major(2) | Minor(2) | TxPower@1m(1). ID karyawan = Major<<16 | Minor. */
    private fun ibeacon(id: Long): ByteArray {
        val bb = ByteBuffer.allocate(23)
        bb.put(0x02.toByte()).put(0x15.toByte())
        bb.putLong(APP_UUID.mostSignificantBits).putLong(APP_UUID.leastSignificantBits)
        bb.putShort(((id shr 16) and 0xFFFF).toShort())
        bb.putShort((id and 0xFFFF).toShort())
        bb.put((-59).toByte()) // placeholder; gateway memakai nilai kalibrasi sendiri
        return bb.array()
    }

    override fun onDestroy() {
        stopAdv()
        if (receiverRegistered) unregisterReceiver(btReceiver)
        running = false
        status = "Berhenti"
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
