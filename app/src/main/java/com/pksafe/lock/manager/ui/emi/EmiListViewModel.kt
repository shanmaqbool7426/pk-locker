package com.pksafe.lock.manager.ui.emi

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pksafe.lock.manager.data.ApiClient
import com.pksafe.lock.manager.data.ApiService
import com.pksafe.lock.manager.data.MarkPaidRequest
import com.pksafe.lock.manager.data.UpcomingEmi
import kotlinx.coroutines.launch

/**
 * ViewModel for the "Upcoming EMIs" screen.
 * Fetches all unpaid/partial EMI installments for the logged-in shopkeeper
 * (GET /api/emis/upcoming) and supports marking an installment as fully paid.
 */
class EmiListViewModel : ViewModel() {

    var upcomingEmis by mutableStateOf<List<UpcomingEmi>>(emptyList())
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var actionMessage by mutableStateOf<String?>(null)

    /** EMI id currently being marked as paid (drives per-card button progress). */
    var markingPaidId by mutableStateOf<String?>(null)

    private val apiService = ApiClient.createApiService()

    fun fetchUpcomingEmis(context: Context) {
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
                val response = apiService.getUpcomingEmis("Bearer $token")
                if (response.isSuccessful && response.body()?.success == true) {
                    upcomingEmis = response.body()?.data ?: emptyList()
                    Log.d("EMI_LIST", "Fetched ${upcomingEmis.size} upcoming EMIs")
                } else {
                    errorMessage = "Failed to load EMIs (${response.code()})"
                    upcomingEmis = emptyList()
                }
            } catch (e: Exception) {
                Log.e("EMI_LIST", "Fetch failed: ${e.message}")
                errorMessage = "Connection failed"
                upcomingEmis = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Marks the given installment as fully paid (backend records the remaining
     * amount as a payment, updates device balance) and refreshes the list.
     */
    fun markEmiPaid(context: Context, emi: UpcomingEmi) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""

        if (token.isEmpty()) return

        viewModelScope.launch {
            markingPaidId = emi.id
            try {
                // Empty body → backend pays the full remaining amount
                val response = apiService.markEmiAsPaid("Bearer $token", emi.id, MarkPaidRequest())
                if (response.isSuccessful && response.body()?.success == true) {
                    actionMessage = "EMI marked as paid for ${emi.customerName}"
                    fetchUpcomingEmis(context)
                } else {
                    actionMessage = response.body()?.message ?: "Failed to mark as paid"
                }
            } catch (e: Exception) {
                Log.e("EMI_LIST", "Mark paid failed", e)
                // Refresh in case the request reached the server but the response
                // was lost — the list will then show the installment as already paid.
                fetchUpcomingEmis(context)
                actionMessage = "Connection error (${e.javaClass.simpleName}) — payment not recorded"
            } finally {
                markingPaidId = null
            }
        }
    }

    fun clearActionMessage() {
        actionMessage = null
    }
}
