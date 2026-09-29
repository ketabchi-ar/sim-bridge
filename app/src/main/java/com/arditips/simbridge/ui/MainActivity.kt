package com.arditips.simbridge.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
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
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson

class MainActivity : AppCompatActivity() {

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
            } catch (_: Exception) {}
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "لطفاً تمام دسترسی‌ها را برای کارکرد کامل تایید کنید", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            binding.tvStatusTitle.text = "وضعیت میزبان (گوشی اول)"
            binding.layoutClientControls.visibility = View.GONE
            binding.cardSendSms.visibility = View.GONE
            val isRunning = GatewayBridgeService.instance != null
            binding.tvStatusMessage.text = if (isRunning) "میزبان فعال است ✓" else "میزبان متوقف است"
            binding.btnToggleService.text = if (isRunning) getString(R.string.stop_server) else getString(R.string.start_server)
        } else {
            binding.tvStatusTitle.text = "وضعیت کلاینت (گوشی دوم)"
            binding.layoutClientControls.visibility = View.VISIBLE
            binding.cardSendSms.visibility = View.VISIBLE
            val isRunning = ClientBridgeService.instance != null
            binding.tvStatusMessage.text = if (isRunning) getString(R.string.client_connected) else getString(R.string.client_disconnected)
            binding.btnToggleService.text = if (isRunning) getString(R.string.disconnect) else getString(R.string.connect)
        }
    }

    private fun setupActions() {
        binding.btnToggleService.setOnClickListener {
            if (isGatewayMode) {
                toggleGatewayService()
            } else {
                toggleClientService()
            }
        }

        binding.btnSelectDevice.setOnClickListener {
            showPairedDevicesDialog()
        }

        binding.btnSendSms.setOnClickListener {
            val recipient = binding.etRecipient.text?.toString()?.trim() ?: ""
            val body = binding.etMessageBody.text?.toString()?.trim() ?: ""

            if (recipient.isEmpty() || body.isEmpty()) {
                Toast.makeText(this, "لطفاً شماره و متن پیام را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val client = ClientBridgeService.instance
            if (client == null) {
                Toast.makeText(this, "ابتدا به میزبان متصل شوید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val ok = client.sendSms(recipient, body)
            if (ok) {
                binding.etMessageBody.setText("")
                Toast.makeText(this, "درخواست ارسال به سیم‌کارت اول منتقل شد", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "ارسال ناموفق بود - اتصال را بررسی کنید", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleGatewayService() {
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
        binding.root.postDelayed({ updateModeUi() }, 300)
    }

    private fun toggleClientService() {
        if (ClientBridgeService.instance == null) {
            showPairedDevicesDialog()
        } else {
            val intent = Intent(this, ClientBridgeService::class.java).apply {
                action = ClientBridgeService.ACTION_DISCONNECT
            }
            startService(intent)
            binding.root.postDelayed({ updateModeUi() }, 300)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showPairedDevicesDialog() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = btManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(this, "بلوتوث دستگاه را روشن کنید", Toast.LENGTH_SHORT).show()
            return
        }

        val pairedDevices = adapter.bondedDevices.toList()
        if (pairedDevices.isEmpty()) {
            Toast.makeText(this, "ابتدا گوشی اول را از تنظیمات بلوتوث Pair کنید", Toast.LENGTH_LONG).show()
            return
        }

        val deviceNames = pairedDevices.map { "${it.name ?: "Unknown"} (${it.address})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("انتخاب گوشی میزبان (دارای سیم‌کارت)")
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
        binding.root.postDelayed({ updateModeUi() }, 500)
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
