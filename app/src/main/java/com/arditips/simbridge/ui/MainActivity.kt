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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.arditips.simbridge.R
import com.arditips.simbridge.data.SmsRepository
import com.arditips.simbridge.databinding.ActivityMainBinding
import com.arditips.simbridge.model.BridgeEventItem
import com.arditips.simbridge.service.ClientBridgeService
import com.arditips.simbridge.service.GatewayBridgeService
import com.arditips.simbridge.util.AppLog
import com.arditips.simbridge.util.PhoneNumberUtil
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val tag = "MainActivity"
    private lateinit var binding: ActivityMainBinding
    private val eventAdapter = EventAdapter()
    private val gson = Gson()
    private var currentTab = 0 // 0: Gateway, 1: Client, 2: Messages

    private lateinit var conversationAdapter: ConversationAdapter

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val json = intent?.getStringExtra(ClientBridgeService.EXTRA_EVENT_JSON) ?: return
            try {
                val item = gson.fromJson(json, BridgeEventItem::class.java)
                eventAdapter.addEvent(item)
                if (item.type == "SMS" || item.type == "SMS_SENT") {
                    loadConversations()
                }
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
            loadConversations()
        } else {
            AppLog.w(tag, "Some permissions were denied: $results")
        }
    }

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val contactUri: Uri? = result.data?.data
            contactUri?.let { uri ->
                extractContactPhone(uri)
            }
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

    override fun onResume() {
        super.onResume()
        updateModeUi()
        loadConversations()
    }

    private fun setupRecyclerView() {
        binding.recyclerViewEvents.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewEvents.adapter = eventAdapter

        conversationAdapter = ConversationAdapter { conversation ->
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CONTACT, conversation.contact)
            }
            startActivity(intent)
        }
        binding.recyclerViewConversations.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewConversations.adapter = conversationAdapter
    }

    private fun setupTabs() {
        binding.tabLayoutMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                updateModeUi()
                if (currentTab == 2) {
                    loadConversations()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        updateModeUi()
    }

    private fun loadConversations() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = SmsRepository.loadConversations(this@MainActivity)
            withContext(Dispatchers.Main) {
                conversationAdapter.setConversations(list)
            }
        }
    }

    private fun updateModeUi() {
        when (currentTab) {
            0 -> {
                // Gateway Mode
                binding.scrollControls.visibility = View.VISIBLE
                binding.layoutMessagesTab.visibility = View.GONE

                binding.tvStatusTitle.text = "گوشی ۱ (میزبان سیم‌کارت)"
                binding.btnSelectDevice.visibility = View.GONE

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
            }
            1 -> {
                // Client Mode
                binding.scrollControls.visibility = View.VISIBLE
                binding.layoutMessagesTab.visibility = View.GONE

                binding.tvStatusTitle.text = "گوشی ۲ (کلاینت بدون سیم‌کارت)"
                binding.btnSelectDevice.visibility = View.VISIBLE

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
                binding.btnTetheringSettings.text = "🌐 اتصال به اینترنت بلوتوثی گوشی ۱"
            }
            2 -> {
                // Dedicated Messages Tab
                binding.scrollControls.visibility = View.GONE
                binding.layoutMessagesTab.visibility = View.VISIBLE
            }
        }
    }

    private fun setupActions() {
        binding.btnViewLogs.setOnClickListener {
            showLogsDialog()
        }

        binding.btnPickContact.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            contactPickerLauncher.launch(intent)
        }

        binding.tvRefreshConversations.setOnClickListener {
            loadConversations()
            Toast.makeText(this, "پیام‌ها بروزرسانی شدند", Toast.LENGTH_SHORT).show()
        }

        binding.tvClearEvents.setOnClickListener {
            eventAdapter.clearEvents()
            Toast.makeText(this, "لیست رویدادها پاک شد", Toast.LENGTH_SHORT).show()
        }

        binding.btnToggleService.setOnClickListener {
            try {
                if (currentTab == 0) {
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

        binding.btnTetheringSettings.setOnClickListener {
            try {
                if (currentTab == 0) {
                    // Host / Gateway Phone: Open Tethering settings (to share Internet)
                    val intent = Intent().apply {
                        action = "android.settings.TETHER_SETTINGS"
                    }
                    startActivity(intent)
                } else {
                    // Client Phone (No SIM): Open Bluetooth paired devices settings (to connect to Internet Access profile)
                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                    startActivity(intent)
                    Toast.makeText(this, "روی نام گوشی ۱ بزنید و تیک «Internet access» را فعال کنید", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                    startActivity(intent)
                } catch (e2: Exception) {
                    Toast.makeText(this, "امکان باز کردن تنظیمات میسر نشد", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnSendSms.setOnClickListener {
            try {
                val rawRecipient = binding.etRecipient.text?.toString()?.trim() ?: ""
                val body = binding.etMessageBody.text?.toString()?.trim() ?: ""

                if (rawRecipient.isEmpty() || body.isEmpty()) {
                    Toast.makeText(this, "شماره مقصد و متن پیام را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val recipient = PhoneNumberUtil.normalize(rawRecipient)
                val client = ClientBridgeService.instance
                if (client == null) {
                    Toast.makeText(this, "ابتدا در تب گوشی ۲ به گوشی اول متصل شوید", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val ok = client.sendSms(recipient, body)
                if (ok) {
                    binding.etMessageBody.setText("")
                    Toast.makeText(this, "پیام برای ارسال به سیم‌کارت اول منتقل شد ✓", Toast.LENGTH_SHORT).show()
                    binding.root.postDelayed({ loadConversations() }, 500)
                } else {
                    Toast.makeText(this, "ارسال ناموفق بود - وضعیت اتصال را چک کنید", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                AppLog.e(tag, "Error sending SMS", e)
            }
        }
    }

    @SuppressLint("Range")
    private fun extractContactPhone(contactUri: Uri) {
        var cursor = contentResolver.query(contactUri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val rawNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                binding.etRecipient.setText(PhoneNumberUtil.normalize(rawNumber))
            }
        } catch (e: Exception) {
            AppLog.e(tag, "Failed to read contact phone: ${e.message}")
        } finally {
            cursor?.close()
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
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
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
