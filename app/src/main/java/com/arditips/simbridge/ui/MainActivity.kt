package com.arditips.simbridge.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.arditips.simbridge.R
import com.arditips.simbridge.databinding.ActivityMainBinding
import com.arditips.simbridge.model.BridgeEventItem
import com.arditips.simbridge.service.ClientBridgeService
import com.arditips.simbridge.service.GatewayBridgeService
import com.arditips.simbridge.util.AppLog
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson

class MainActivity : AppCompatActivity() {

    private val tag = "MainActivity"
    private lateinit var binding: ActivityMainBinding
    private val eventAdapter = EventAdapter()
    private val gson = Gson()
    private var isGatewayMode = true

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val json = intent?.getStringExtra(ClientBridgeService.EXTRA_EVENT_JSON) ?: return
            try {
                val item = gson.fromJson(json, BridgeEventItem::class.java)
                eventAdapter.addEvent(item)
            } catch (e: Exception) {
                AppLog.e(tag, "Failed to parse event JSON", e)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            AppLog.i(tag, "All permissions granted by user")
            Toast.makeText(this, "دسترسی‌ها با موفقیت تایید شدند ✓", Toast.LENGTH_SHORT).show()
        } else {
            AppLog.w(tag, "Some permissions were denied: $results")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            setupRecyclerView()
            setupTabs()
            setupActions()
            requestAppPermissions()

            val filter = IntentFilter(ClientBridgeService.BROADCAST_EVENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(eventReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(eventReceiver, filter)
            }
            AppLog.i(tag, "MainActivity UI loaded")
        } catch (e: Exception) {
            AppLog.e(tag, "Error in onCreate", e)
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerViewEvents.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewEvents.adapter = eventAdapter
    }

    private fun setupTabs() {
        binding.tabLayoutMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                isGatewayMode = tab?.position == 0
                updateModeUi()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        updateModeUi()
    }

    private fun updateModeUi() {
        if (isGatewayMode) {
            binding.tvStatusTitle.text = "گوشی ۱ (میزبان سیم‌کارت)"
            binding.btnSelectDevice.visibility = View.GONE
            binding.cardSendSms.visibility = View.GONE

            val isRunning = GatewayBridgeService.instance != null
            if (isRunning) {
                binding.tvStatusMessage.text = "میزبان فعال است - در حال دریافت تماس و پیامک"
                binding.viewStatusDot.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_light)
                binding.btnToggleService.text = "توقف سرویس میزبان"
            } else {
                binding.tvStatusMessage.text = "سرویس میزبان متوقف است"
                binding.viewStatusDot.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_light)
                binding.btnToggleService.text = "شروع به کار میزبان (گوشی ۱)"
            }
        } else {
            binding.tvStatusTitle.text = "گوشی ۲ (کلاینت بدون سیم‌کارت)"
            binding.btnSelectDevice.visibility = View.VISIBLE
            binding.cardSendSms.visibility = View.VISIBLE

            val isRunning = ClientBridgeService.instance != null
            if (isRunning) {
                binding.tvStatusMessage.text = "متصل به گوشی ۱ ✓"
                binding.viewStatusDot.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_light)
                binding.btnToggleService.text = "قطع اتصال از گوشی ۱"
                binding.btnSelectDevice.visibility = View.GONE
            } else {
                binding.tvStatusMessage.text = "در انتظار اتصال به گوشی ۱"
                binding.viewStatusDot.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_orange_light)
                binding.btnToggleService.text = "انتخاب و اتصال سریع به گوشی ۱"
                binding.btnSelectDevice.visibility = View.VISIBLE
            }
        }
    }

    private fun setupActions() {
        binding.btnViewLogs.setOnClickListener {
            showLogsDialog()
        }

        binding.tvClearEvents.setOnClickListener {
            eventAdapter.clearEvents()
            Toast.makeText(this, "لیست رویدادها پاک شد", Toast.LENGTH_SHORT).show()
        }

        binding.btnToggleService.setOnClickListener {
            try {
                if (isGatewayMode) {
                    toggleGatewayService()
                } else {
                    if (ClientBridgeService.instance == null) {
                        showPairedDevicesDialog()
                    } else {
                        disconnectClientService()
                    }
                }
            } catch (e: Exception) {
                AppLog.e(tag, "Error toggling service", e)
                Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSelectDevice.setOnClickListener {
            try {
                showPairedDevicesDialog()
            } catch (e: Exception) {
                AppLog.e(tag, "Error selecting paired device", e)
                Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnSendSms.setOnClickListener {
            try {
                val recipient = binding.etRecipient.text?.toString()?.trim() ?: ""
                val body = binding.etMessageBody.text?.toString()?.trim() ?: ""

                if (recipient.isEmpty() || body.isEmpty()) {
                    Toast.makeText(this, "شماره مقصد و متن پیام را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val client = ClientBridgeService.instance
                if (client == null) {
                    Toast.makeText(this, "ابتدا به گوشی اول متصل شوید", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val ok = client.sendSms(recipient, body)
                if (ok) {
                    binding.etMessageBody.setText("")
                    Toast.makeText(this, "پیام برای ارسال به سیم‌کارت اول منتقل شد ✓", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "ارسال ناموفق بود - وضعیت اتصال را چک کنید", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                AppLog.e(tag, "Error sending SMS", e)
            }
        }
    }

    private fun toggleGatewayService() {
        if (!hasBluetoothConnectPermission()) {
            Toast.makeText(this, "تایید دسترسی بلوتوث لازم است", Toast.LENGTH_SHORT).show()
            requestAppPermissions()
            return
        }

        if (GatewayBridgeService.instance == null) {
            val intent = Intent(this, GatewayBridgeService::class.java).apply {
                action = GatewayBridgeService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            val intent = Intent(this, GatewayBridgeService::class.java).apply {
                action = GatewayBridgeService.ACTION_STOP
            }
            startService(intent)
        }
        binding.root.postDelayed({ updateModeUi() }, 400)
    }

    private fun disconnectClientService() {
        val intent = Intent(this, ClientBridgeService::class.java).apply {
            action = ClientBridgeService.ACTION_DISCONNECT
        }
        startService(intent)
        binding.root.postDelayed({ updateModeUi() }, 400)
    }

    @SuppressLint("MissingPermission")
    private fun showPairedDevicesDialog() {
        if (!hasBluetoothConnectPermission()) {
            Toast.makeText(this, "دسترسی بلوتوث داده نشده است", Toast.LENGTH_SHORT).show()
            requestAppPermissions()
            return
        }

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = btManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(this, "لطفاً ابتدا بلوتوث را روشن کنید", Toast.LENGTH_SHORT).show()
            return
        }

        val pairedDevices = adapter.bondedDevices?.toList() ?: emptyList()
        if (pairedDevices.isEmpty()) {
            Toast.makeText(this, "ابتدا گوشی اول را از تنظیمات بلوتوث اندروید Pair کنید", Toast.LENGTH_LONG).show()
            return
        }

        val deviceNames = pairedDevices.map { "${it.name ?: "دستگاه"} (${it.address})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("انتخاب گوشی ۱ (دارای سیم‌کارت)")
            .setItems(deviceNames) { _, which ->
                val selectedDevice = pairedDevices[which]
                connectClientToDevice(selectedDevice.address)
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun connectClientToDevice(deviceAddress: String) {
        val intent = Intent(this, ClientBridgeService::class.java).apply {
            action = ClientBridgeService.ACTION_CONNECT
            putExtra(ClientBridgeService.EXTRA_DEVICE_ADDRESS, deviceAddress)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        binding.root.postDelayed({ updateModeUi() }, 600)
    }

    private fun showLogsDialog() {
        val logs = AppLog.getAllLogs()
        val textView = TextView(this).apply {
            text = if (logs.isBlank()) "هنوز لاگی ثبت نشده است." else logs
            setPadding(32, 24, 32, 24)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            setTextIsSelectable(true)
            textSize = 12f
        }
        val scrollView = ScrollView(this).apply {
            addView(textView)
        }

        AlertDialog.Builder(this)
            .setTitle("لاگ‌های زنده برنامه")
            .setView(scrollView)
            .setPositiveButton("بستن", null)
            .setNeutralButton("کپی کامل") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("SIM Bridge Logs", logs)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "لاگ در کلیپ‌بورد کپی شد", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("پاک کردن") { _, _ ->
                AppLog.clear()
                Toast.makeText(this, "لاگ‌ها پاک شدند", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            AppLog.i(tag, "Requesting missing permissions: $missing")
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(eventReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
