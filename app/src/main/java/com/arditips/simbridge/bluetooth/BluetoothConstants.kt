package com.arditips.simbridge.bluetooth

import java.util.UUID

object BluetoothConstants {
    const val SERVICE_NAME = "SimBridgeData"
    // Standard RFCOMM SPP UUID or custom SIM Bridge UUID
    val DATA_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    val AUDIO_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a67")

    const val BUFFER_SIZE = 4096
    const val SAMPLE_RATE = 16000 // 16kHz audio sample rate for clean voice transfer
}
