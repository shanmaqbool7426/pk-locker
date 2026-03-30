package com.example.pklocker.ui.keys

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pklocker.data.*
import com.example.pklocker.util.Constants
import com.example.pklocker.ui.dashboard.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyKeysScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE) }
    val authToken = sharedPrefs.getString("auth_token", "") ?: ""

    var numKeys by remember { mutableStateOf("10") }
    // Wallet number and PIN are no longer needed for Safepay
    
    var isLoading by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<KeyOrderData>>(emptyList()) }
    
    // Safepay Session
    var safepayTracker by remember { mutableStateOf("") }
    var safepayOrderId by remember { mutableStateOf("") }

    val unitPrice = 300
    val totalAmount = (numKeys.toIntOrNull() ?: 0) * unitPrice

    val retrofit: Retrofit = remember {
        Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    val apiService: ApiService = remember { retrofit.create(ApiService::class.java) }

    fun fetchHistory() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getKeyHistory("Bearer $authToken")
                if (response.isSuccessful) history = response.body()?.data ?: emptyList()
            } catch (e: Exception) { }
        }
    }

    fun startSafepayCheckout() {
        if ((numKeys.toIntOrNull() ?: 0) <= 0) return
        
        isLoading = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.checkoutKeys(
                    "Bearer $authToken",
                    mapOf("numKeys" to numKeys, "platform" to "android")
                )
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        val checkoutData: SafepayData? = response.body()?.data
                        if (checkoutData != null) {
                            safepayTracker = checkoutData.tracker
                            safepayOrderId = checkoutData.orderId
                            
                            // Open Browser
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(checkoutData.checkoutUrl))
                            context.startActivity(intent)
                            
                            Toast.makeText(context, "Redirecting to Safepay...", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Checkout Error: ${response.message()}", Toast.LENGTH_LONG).show()
                    }
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Network Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    isLoading = false
                }
            }
        }
    }

    fun verifySafepayPayment(orderIdOverride: String? = null) {
        val targetOrderId = orderIdOverride ?: safepayOrderId
        if (targetOrderId.isEmpty()) {
            fetchHistory()
            return
        }
        
        isLoading = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.verifyPayment(
                    "Bearer $authToken",
                    mapOf("tracker" to safepayTracker, "orderId" to targetOrderId)
                )
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Payment Verified! Keys Added.", Toast.LENGTH_LONG).show()
                        safepayTracker = ""
                        safepayOrderId = ""
                        sharedPrefs.edit().remove("last_payment_status").remove("last_payment_order_id").apply()
                        fetchHistory()
                    } else {
                        Toast.makeText(context, "Verification Pending...", Toast.LENGTH_SHORT).show()
                    }
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    fun allocateFreeKeysTest() {
        val keys = numKeys.toIntOrNull() ?: 0
        if (keys <= 0 || keys > 10) {
            Toast.makeText(context, "Max 10 keys allowed for free test", Toast.LENGTH_SHORT).show()
            return
        }
        
        isLoading = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.allocateFreeKeys(
                    "Bearer $authToken",
                    mapOf("numKeys" to numKeys, "platform" to "android")
                )
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Test Keys Added Successfully!", Toast.LENGTH_LONG).show()
                        fetchHistory()
                    } else {
                        Toast.makeText(context, "Failed to add test keys", Toast.LENGTH_SHORT).show()
                    }
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { 
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    isLoading = false 
                }
            }
        }
    }

    LaunchedEffect(Unit) { fetchHistory() }

    // Listen for deep link results in SharedPreferences
    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "last_payment_status") {
                val status = prefs.getString("last_payment_status", "")
                val orderId = prefs.getString("last_payment_order_id", "")
                if (status == "success" && !orderId.isNullOrEmpty()) {
                    verifySafepayPayment(orderId)
                }
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Direct Key Purchase", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = TextTitle) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(8.dp)
                            .background(Color(0xFFF3F4F6), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp), tint = TextTitle)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(AppBg)
                .verticalScroll(rememberScrollState())
        ) {
            // --- Info Banner ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(0.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryBlue)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF1E40AF), Color(0xFF2563EB))))
                ) {
                    Icon(
                        Icons.Default.Receipt,
                        null,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(120.dp)
                            .offset(x = 20.dp, y = 20.dp),
                        tint = Color.White.copy(alpha = 0.05f)
                    )

                    Column(modifier = Modifier.padding(24.dp)) {
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "INSTANT ALLOCATION",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Direct Wallet Payment",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "No manual approval or screenshot needed.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Current Price: PKR $unitPrice / Key", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            // --- Form Section ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = borderStroke()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Order Details", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextTitle)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Number of Keys", fontSize = 13.sp, color = TextSubtitle, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = numKeys,
                        onValueChange = { if (it.all { char -> char.isDigit() }) numKeys = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = Color(0xFF94A3B8),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color(0xFFF1F5F9),
                            focusedLabelColor = PrimaryBlue,
                            unfocusedLabelColor = Color(0xFF64748B)
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (safepayTracker.isNotEmpty()) {
                        Button(
                            onClick = { verifySafepayPayment() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                        ) {
                            Icon(Icons.Default.History, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("VERIFY ACTIVE PAYMENT", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // --- FREE TEST KEY BUTTON ---
                    OutlinedButton(
                        onClick = { allocateFreeKeysTest() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = !isLoading && (numKeys.toIntOrNull() ?: 0) <= 10 && (numKeys.toIntOrNull() ?: 0) > 0,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF16A34A)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF16A34A))
                    ) {
                        Icon(Icons.Default.Payments, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("FREE TEST ALLOCATION (MAX 10)", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Spacer(modifier = Modifier.height(28.dp))

                    // --- Total Amount Section ---
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(16.dp),
                        border = borderStroke()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("TOTAL AMOUNT", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = TextSubtitle, letterSpacing = 0.5.sp)
                                Text("$numKeys Keys x PKR $unitPrice", fontSize = 12.sp, color = TextSubtitle)
                            }
                            Text("PKR $totalAmount", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = PrimaryBlue)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { startSafepayCheckout() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isLoading && (numKeys.toIntOrNull() ?: 0) > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlue, 
                            disabledContainerColor = PrimaryBlue.copy(alpha = 0.5f)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Payments, null, tint = Color.White)
                            Spacer(Modifier.width(12.dp))
                            Text("PAY NOW & GET KEYS", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- History Section ---
            Text(
                "Transaction History",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = TextTitle,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No recent transactions", color = TextSubtitle, fontSize = 14.sp)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    history.forEach { order -> KeyOrderRow(order) }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun MethodPill(label: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .clickable { onClick() }
            .height(54.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) TextTitle else Color.White,
        border = if (isSelected) null else borderStroke()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label, 
                color = if (isSelected) Color.White else TextTitle, 
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun KeyOrderRow(order: KeyOrderData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = borderStroke(),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF3F4F6),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Receipt, 
                        null, 
                        tint = TextSubtitle, 
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("${order.numKeys} Keys Purchased", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextTitle)
                    Text("PKR ${order.totalAmount}", fontSize = 13.sp, color = TextSubtitle)
                }
            }
            
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (order.status.lowercase() == "success" || order.status.lowercase() == "completed") 
                    Color(0xFFECFDF5) else Color(0xFFFEF2F2)
            ) {
                Text(
                    order.status.uppercase(),
                    color = if (order.status.lowercase() == "success" || order.status.lowercase() == "completed") 
                        Color(0xFF059669) else Color(0xFFDC2626),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

