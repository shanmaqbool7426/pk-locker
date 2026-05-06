package com.pksafe.lock.manager.ui.keys

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pksafe.lock.manager.data.ApiService
import com.pksafe.lock.manager.data.KeyOrder
import com.pksafe.lock.manager.ui.theme.BrandBlue
import com.pksafe.lock.manager.util.Constants
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AdminKeyViewModel : ViewModel() {
    var orders by mutableStateOf<List<KeyOrder>>(emptyList())
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    private val retrofit = Retrofit.Builder()
        .baseUrl(Constants.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    private val apiService = retrofit.create(ApiService::class.java)

    fun fetchOrders(context: Context) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""
        
        viewModelScope.launch {
            isLoading = true
            try {
                val response = apiService.getAdminKeyOrders("Bearer $token")
                if (response.isSuccessful && response.body()?.success == true) {
                    orders = response.body()?.data ?: emptyList()
                } else {
                    errorMessage = "Failed to fetch orders"
                }
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }

    fun approveOrder(context: Context, orderId: String) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""

        viewModelScope.launch {
            try {
                val response = apiService.approveKeyOrder("Bearer $token", orderId)
                if (response.isSuccessful) {
                    fetchOrders(context)
                }
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    fun rejectOrder(context: Context, orderId: String, reason: String) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""

        viewModelScope.launch {
            try {
                val response = apiService.rejectKeyOrder("Bearer $token", orderId, mapOf("notes" to reason))
                if (response.isSuccessful) {
                    fetchOrders(context)
                }
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminKeyOrdersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: AdminKeyViewModel = viewModel()
    
    LaunchedEffect(Unit) {
        viewModel.fetchOrders(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Terminal Keys", fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text("ADMIN APPROVAL HUB", fontSize = 10.sp, letterSpacing = 2.sp, color = BrandBlue, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchOrders(context) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (viewModel.isLoading && viewModel.orders.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = BrandBlue)
            } else if (viewModel.orders.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(0.3f))
                    Spacer(Modifier.height(16.dp))
                    Text("No pending key requests", color = Color.Gray, fontWeight = FontWeight.Medium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(viewModel.orders) { order ->
                        KeyOrderAdminCard(order, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun KeyOrderAdminCard(order: KeyOrder, viewModel: AdminKeyViewModel) {
    val context = LocalContext.current
    var showProof by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header: Shopkeeper Info
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = BrandBlue.copy(0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            order.shopkeeper?.name?.take(1)?.uppercase() ?: "?",
                            fontWeight = FontWeight.Black,
                            color = BrandBlue,
                            fontSize = 18.sp
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(order.shopkeeper?.shopName ?: "Unknown Shop", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(order.shopkeeper?.name ?: "N/A", fontSize = 12.sp, color = Color.Gray)
                }
                StatusBadge(order.status ?: "Pending")
            }

            Divider(Modifier.padding(vertical = 16.dp), color = Color(0xFFF1F5F9))

            // Order Details
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OrderInfoItem("Keys", "${order.numKeys}", Icons.Default.VpnKey)
                OrderInfoItem("Amount", "PKR ${order.totalAmount}", Icons.Default.Payments)
                OrderInfoItem("Platform", order.platform.uppercase(), if(order.platform == "android") Icons.Default.Android else Icons.Default.PhoneIphone)
            }

            Spacer(Modifier.height(16.dp))

            // Payment Proof Preview
            if (!order.paymentProofImage.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clickable { showProof = true },
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("View Payment Proof Screenshot", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (order.status == "Pending") {
                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                                onClick = { viewModel.rejectOrder(context, order.id, "Payment not verified") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2), contentColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Reject", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.approveOrder(context, order.id) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Approve & Add Keys", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showProof && !order.paymentProofImage.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showProof = false },
            title = { Text("Payment Proof", fontWeight = FontWeight.Black) },
            text = {
                Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                    if (order.paymentProofImage!!.startsWith("DIRECT_WALLET_") || order.paymentProofImage!!.startsWith("SAFEPAY_")) {
                        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(48.dp), tint = BrandBlue)
                            Spacer(Modifier.height(8.dp))
                            Text("Automatic Payment", fontWeight = FontWeight.Bold)
                            Text(order.paymentProofImage!!, fontSize = 11.sp, color = Color.Gray)
                        }
                    } else {
                        AsyncImage(
                            model = order.paymentProofImage,
                            contentDescription = "Proof",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProof = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun OrderInfoItem(label: String, value: String, icon: ImageVector) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(12.dp), tint = Color.Gray)
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        }
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E293B))
    }
}

@Composable
fun StatusBadge(status: String) {
    val (color, bgColor) = when (status) {
        "Approved" -> Color(0xFF10B981) to Color(0xFFD1FAE5)
        "Rejected" -> Color(0xFFEF4444) to Color(0xFFFEE2E2)
        else -> Color(0xFFF59E0B) to Color(0xFFFEF3C7)
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            status.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = color
        )
    }
}
