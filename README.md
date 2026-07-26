# 🚀 PK Locker - Complete Setup & Operation Guide

Welcome to the **PK Locker** project. This documentation provides a step-by-step walkthrough for building the APK, provisioning customer phones, registering devices, locking/unlocking devices, and troubleshooting.

---

## 📌 Table of Contents
1. [Build APK](#1-build-apk)
2. [APK Download URLs](#2-apk-download-urls)
3. [Customer Phone Setup (Provisioning)](#3-customer-phone-setup-provisioning)
4. [Device Registration in Admin Portal](#4-device-registration-in-admin-portal)
5. [Locking & Unlocking Flow](#5-locking--unlocking-flow)
6. [Auto-Update Configuration](#6-auto-update-configuration)
7. [Troubleshooting & FAQs](#7-troubleshooting--faqs)

---

## 🛠️ 1. Build APK

To build a fresh signed release APK of PK Locker:

1. Open terminal in `PKlocker` directory:
   ```cmd
   cd D:\personal-projects\pk-locker\PKlocker
   ```
2. Run the Gradle build command:
   ```cmd
   set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot
   .\gradlew.bat assembleRelease
   ```
3. Compiled APK file location:
   `app/build/outputs/apk/release/app-release.apk`

--- c

## 🌐 2. APK Download URLs

Once uploaded to the backend server (`locker-api`), the APK is accessible at:

- **Public Download URL:**  
  `https://pk-locker-api.vercel.app/apk/update.apk`  
  *(or `https://pk-locker-api.vercel.app/apk/v7_app.apk`)*

- **Local Development URL:**  
  `http://localhost:5000/apk/update.apk`

---

## 📱 3. Customer Phone Setup (Provisioning)

### **Method A: QR Code Setup (Recommended - Device Owner)**
This method configures PK Locker as **Device Owner** (unremovable lock, factory reset blocked).

1. Take a **Factory Reset / Brand New** Android phone.
2. Turn on the phone and stay on the initial **Welcome Screen**.
3. **Tap 6 times** on any blank space on the screen → The camera QR Scanner will open.
4. Connect to Wi-Fi.
5. Scan the **PK Locker Admin Provisioning QR Code**.
6. The Android OS will automatically:
   - Download the APK from `https://pk-locker-api.vercel.app/apk/update.apk`.
   - Install the app silently in background.
   - Set PK Locker as **Device Owner**.
   - Auto-fetch the phone's IMEI in background (no manual popup required).

---

### **Method B: Manual APK Installation**
If setting up manually without factory reset:

1. Open Chrome browser on the customer phone.
2. Go to `https://pk-locker-api.vercel.app/apk/update.apk` and download the file.
3. Open the APK file and tap **Install**.
4. Launch **PK Locker** app and grant all mandatory permissions:
   - **Device Admin:** Tap *Activate Device Admin*.
   - **Display Over Other Apps (Overlay):** Go to *Settings > Apps > PKLocker > Allow display over other apps → ON*.
   - **SMS & Location Permissions:** Tap *Allow*.
5. If prompted, enter the **IMEI 1** manually when asked.

---

## 🏬 4. Device Registration in Admin Portal

1. On customer phone, dial `*#06#` to get **IMEI 1**.
2. Log into **PK Locker Admin Dashboard** (`locker-api` web portal).
3. Go to **Add New Device / Register Device**:
   - Enter **IMEI 1** (must match exactly).
   - Enter **Customer Name**, **Phone Number**, **CNIC**.
   - Enter **Total Loan Amount**, **Monthly EMI**, **Due Date**.
4. Click **Save / Register Device**.

---

## 🔒 5. Locking & Unlocking Flow

### **A. Remote Lock (Online / Internet)**
- **When EMI is overdue:** Admin goes to Dashboard → clicks **Lock Device**.
- **Server Action:** Backend sends an FCM Push Notification to the customer phone.
- **App Behavior:** Screen immediately locks with payment details and shopkeeper contact button. Hardware buttons & settings are restricted.

### **B. Remote Unlock (Online / Internet)**
- **When customer pays EMI:** Admin goes to Dashboard → clicks **Mark Paid / Unlock**.
- **App Behavior:** Screen unlocks instantly and returns customer to home screen.

### **C. Offline SMS Lock/Unlock (No Internet)**
- If customer turns off Wi-Fi/Mobile Data, Admin sends secret **SMS Lock Code** to customer SIM.
- PK Locker `SmsReceiver` intercepts the SMS offline and triggers lock/unlock automatically.

---

## 🔄 6. Auto-Update Configuration

In `locker-api/index.js`, the `/api/version` endpoint handles force updates:

- **Normal State (Disabled):**  
  `versionCode: 3`, `success: false`, `forceUpdate: false`  
  *Prevents unwanted background APK downloads while testing.*

- **Pushing a New Release:**  
  Update `versionCode` to a higher number (e.g. `versionCode: 8`) and set `success: true` in `index.js` when pushing a mandatory update.

---

## ❓ 7. Troubleshooting & FAQs

### Q1: Why didn't the IMEI input popup appear on the customer phone?
> **Answer:** When provisioned via QR Code (Device Owner), the app **automatically fetches the IMEI in the background** without interrupting the user. Manual IMEI entry popup only appears in standard non-Device Owner mode.

### Q2: Why didn't the phone lock when I clicked "Lock Device" in Admin Dashboard?
> Check the following 3 items:
> 1. **Overlay Permission:** Ensure *Display over other apps* is turned **ON** in phone settings.
> 2. **IMEI Match:** Verify the IMEI in Admin Dashboard matches `*#06#` on the phone.
> 3. **Internet Connection:** Ensure customer phone has active Wi-Fi / Data to receive FCM push messages.

---
*Created by PK Locker Team.*
