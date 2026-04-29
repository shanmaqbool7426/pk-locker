package com.example.pklocker.util

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.NfcEvent
import java.util.Properties

/**
 * NFC Provisioning Helper: Allows a Master/Shopkeeper phone to setup a target phone
 * by just bumping them together on the "Welcome" screen.
 */
class NfcProvisioner(private val context: Context) : NfcAdapter.CreateNdefMessageCallback {

    private val packageName = context.packageName
    private val adminReceiver = "$packageName/com.example.pklocker.receiver.AdminReceiver"
    private val apkDownloadUrl = "https://pk-locker-api.vercel.app/dl/v6_app.apk"
    private val signatureChecksum = "REPLACE_WITH_DYNAMIC_SHA256"

    override fun createNdefMessage(event: NfcEvent?): NdefMessage {
        val props = Properties()
        
        // 1. Mandatory Enrollment Properties
        props[DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME] = packageName
        props[DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME] = adminReceiver
        props[DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION] = apkDownloadUrl
        
        // 2. Extra flags for professional UX
        props[DevicePolicyManager.EXTRA_PROVISIONING_LOCALE] = "en_US"
        props[DevicePolicyManager.EXTRA_PROVISIONING_TIME_ZONE] = "GMT"
        props[DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED] = "true"
        
        // 3. Optional: WiFi credentials (If you want to auto-connect WiFi)
        // props[DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SSID] = "Shop_WiFi"
        // props[DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PASSWORD] = "shop123456"
        
        val byteStream = java.io.ByteArrayOutputStream()
        props.store(byteStream, "Enterprise Provisioning")
        
        val record = NdefRecord.createMime(
            DevicePolicyManager.MIME_TYPE_PROVISIONING_NFC, 
            byteStream.toByteArray()
        )
        
        return NdefMessage(arrayOf(record))
    }
}
