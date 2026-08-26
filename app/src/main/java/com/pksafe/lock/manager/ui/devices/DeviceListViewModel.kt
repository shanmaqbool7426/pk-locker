package com.pksafe.lock.manager.ui.devices

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pksafe.lock.manager.data.AdvancedControlRequest
import com.pksafe.lock.manager.data.ApiClient
import com.pksafe.lock.manager.data.ApiService
import com.pksafe.lock.manager.data.DeregisterResponse
import com.pksafe.lock.manager.data.DeviceControls
import com.pksafe.lock.manager.data.DeviceResponse
import com.pksafe.lock.manager.data.MarkPaidRequest
import kotlinx.coroutines.launch

class DeviceListViewModel : ViewModel() {

    var devices by mutableStateOf<List<DeviceResponse>>(emptyList())
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var deregisterResult by mutableStateOf<DeregisterResponse?>(null)

    private val apiService = ApiClient.createApiService()

    fun fetchDevices(context: Context) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""

        if (token.isEmpty()) {
            errorMessage = "Authentication required"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = apiService.getAllDevices("Bearer $token")
                
                if (response.isSuccessful && response.body() != null) {
                    val responseData = response.body()!!
                    devices = responseData.data
                    Log.d("DEVICE_LIST", "Fetched ${devices.size} devices from server")
                } else {
                    errorMessage = "Server Error: ${response.code()}"
                    devices = emptyList()
                }
            } catch (e: Exception) {
                Log.e("API_ERROR", "Failed: ${e.message}")
                errorMessage = "Connection Failed"
                devices = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    // --- EMI SCHEDULE MANAGEMENT ---
    var selectedEmiSchedule by mutableStateOf<com.pksafe.lock.manager.data.EmiScheduleData?>(null)
    var isFetchingEmi by mutableStateOf(false)

    fun fetchEmiSchedule(context: Context, imei: String) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""
        if (token.isEmpty()) return

        // Clear any previously loaded schedule so the sheet doesn't show another
        // device's stale data while this device's schedule loads (or if loading fails).
        selectedEmiSchedule = null

        viewModelScope.launch {
            isFetchingEmi = true
            try {
                val response = apiService.getDeviceEmiSchedule("Bearer $token", imei)
                if (response.isSuccessful && response.body()?.success == true) {
                    selectedEmiSchedule = response.body()?.data
                } else {
                    errorMessage = "Failed to load EMI schedule"
                }
            } catch (e: Exception) {
                Log.e("EMI_FETCH_ERROR", "Error: ${e.message}")
                errorMessage = "Connection error while fetching EMIs"
            } finally {
                isFetchingEmi = false
            }
        }
    }

    // EMI currently being marked as paid — drives the per-installment spinner in the sheet
    var markingEmiId by mutableStateOf<String?>(null)

    fun markEmiAsPaid(
        context: Context,
        emiId: String,
        imei: String,
        amount: Double? = null,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""
        if (token.isEmpty()) {
            onResult(false, "Login required — payment not recorded")
            return
        }

        viewModelScope.launch {
            markingEmiId = emiId
            try {
                // amount == null → omitted from JSON → backend pays full remaining
                val response = apiService.markEmiAsPaid("Bearer $token", emiId, MarkPaidRequest(amount = amount))
                if (response.isSuccessful && response.body()?.success == true) {
                    val message = response.body()?.message ?: "EMI marked as paid"
                    // Refresh EMI schedule + device list so totals update everywhere
                    fetchEmiSchedule(context, imei)
                    fetchDevices(context)
                    onResult(true, message)
                } else {
                    Log.e("EMI_PAY_ERROR", "mark-paid failed: HTTP ${response.code()}")
                    onResult(false, parseServerErrorMessage(response))
                }
            } catch (e: Exception) {
                Log.e("EMI_PAY_ERROR", "mark-paid exception", e)
                // The request MAY have reached the server even though we never saw
                // the response (e.g. timeout after the DB write). Refresh the
                // schedule so the UI reflects reality — if the installment is
                // already Paid the user won't accidentally pay it twice.
                fetchEmiSchedule(context, imei)
                onResult(
                    false,
                    "Connection error (${e.javaClass.simpleName}): ${e.message ?: "no detail"}"
                )
            } finally {
                markingEmiId = null
            }
        }
    }

    /** Pulls { message } out of a failed response so the UI can show WHY it failed. */
    private fun parseServerErrorMessage(response: retrofit2.Response<*>): String {
        val serverMessage = try {
            val errorBody = response.errorBody()?.string()
            if (!errorBody.isNullOrBlank()) {
                org.json.JSONObject(errorBody).optString("message", "").ifBlank { null }
            } else null
        } catch (_: Exception) { null }
        return serverMessage ?: "Request failed (code ${response.code()})"
    }

    // True while an APPLY & RE-GENERATE request is in flight — keeps the
    // reschedule dialog open with a spinner instead of closing silently.
    var isRescheduling by mutableStateOf(false)

    fun rescheduleEmiPlan(
        context: Context,
        imei: String,
        request: com.pksafe.lock.manager.data.RescheduleEmiRequest,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""
        if (token.isEmpty()) {
            onResult(false, "Login required — plan not updated")
            return
        }

        viewModelScope.launch {
            isRescheduling = true
            try {
                val response = apiService.rescheduleEmiPlan("Bearer $token", imei, request)
                if (response.isSuccessful && response.body()?.success == true) {
                    // Unpaid installments were deleted + regenerated server-side —
                    // refresh both the sheet and the device list.
                    fetchEmiSchedule(context, imei)
                    fetchDevices(context)
                    onResult(true, response.body()?.message ?: "EMI plan updated")
                } else {
                    Log.e("EMI_RESCHEDULE_ERROR", "reschedule failed: HTTP ${response.code()}")
                    onResult(false, parseServerErrorMessage(response))
                }
            } catch (e: Exception) {
                Log.e("EMI_RESCHEDULE_ERROR", "reschedule exception", e)
                onResult(false, "Connection error (${e.javaClass.simpleName}): ${e.message ?: "no detail"}")
            } finally {
                isRescheduling = false
            }
        }
    }

    fun toggleLock(context: Context, imei: String, targetLockState: Boolean) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""
        
        if (token.isEmpty()) return

        viewModelScope.launch {
            isLoading = true
            try {
                val response = if (targetLockState) {
                    apiService.lockDevice("Bearer $token", imei)
                } else {
                    apiService.unlockDevice("Bearer $token", imei)
                }

                if (response.isSuccessful) {
                    // Fetch fresh list only after successful server update
                    fetchDevices(context)
                } else {
                    Log.e("LOCK_ERROR", "Action failed: ${response.message()}")
                }
            } catch (e: Exception) {
                fetchDevices(context)
                Log.e("LOCK_EXCEPTION", "Error: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    fun sendControl(context: Context, imei: String, action: String, state: Any) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""

        if (token.isEmpty()) return

        viewModelScope.launch {
            try {
                val response = apiService.sendAdvancedControl("Bearer $token", imei, AdvancedControlRequest(action, state))
                
                if (response.isSuccessful) {
                    Log.d("CONTROL_SUCCESS", "Action $action changed to $state")
                    // Fetch accurate DB state after control command
                    fetchDevices(context)
                } else {
                    Log.e("CONTROL_ERROR", "Action failed: ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("CONTROL_ERROR", "Command failed: ${e.message}")
                fetchDevices(context) // Rollback
            }
        }
    }

    fun unlockAllControls(context: Context, imei: String) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""

        if (token.isEmpty()) return

        viewModelScope.launch {
            isLoading = true
            try {
                val response = apiService.unlockAllControls("Bearer $token", imei)
                if (response.isSuccessful) {
                    Log.d("UNLOCK_ALL", "All controls cleared for IMEI: $imei")
                    fetchDevices(context)
                } else {
                    Log.e("UNLOCK_ALL_ERROR", "Failed: ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("UNLOCK_ALL_ERROR", "Exception: ${e.message}")
                fetchDevices(context)
            } finally {
                isLoading = false
            }
        }
    }

    fun deregisterDevice(context: Context, imei: String, onSuccess: () -> Unit) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""

        if (token.isEmpty()) return

        viewModelScope.launch {
            isLoading = true
            try {
                val response = apiService.deregisterDevice("Bearer $token", imei)
                if (response.isSuccessful) {
                    val body = response.body()
                    deregisterResult = body
                    Log.d("DEREGISTER", "Device released: $imei, FCM delivered: ${body?.fcmDelivered}")
                    onSuccess()
                } else {
                    Log.e("DEREGISTER_ERROR", "Failed: ${response.message()}")
                    errorMessage = "Deregister failed: ${response.message()}"
                }
            } catch (e: Exception) {
                Log.e("DEREGISTER_ERROR", "Exception: ${e.message}")
                errorMessage = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun clearDeregisterResult() {
        deregisterResult = null
    }
}
