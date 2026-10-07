package com.pksafe.lock.manager.ui.keys

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import com.pksafe.lock.manager.data.ShopkeeperAccount
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pksafe.lock.manager.data.ApiClient
import com.pksafe.lock.manager.data.CreateAccountRequest
import kotlinx.coroutines.launch

// ═════════════════════════════════════════════════════════════════════════════
//  ADMIN — MANAGE ACCOUNTS
//  Creates a "dealer" or "shopkeeper" account directly. This is currently the
//  only in-app way to create either — previously done via raw API calls.
// ═════════════════════════════════════════════════════════════════════════════
class ManageAccountsViewModel : ViewModel() {
    var isSubmitting by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var accounts by mutableStateOf<List<ShopkeeperAccount>>(emptyList())

    private val apiService = ApiClient.createApiService()

    fun fetchAccounts(context: Context, userRole: String) {
        val prefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = prefs.getString("auth_token", "") ?: ""
        if (token.isEmpty()) return

        viewModelScope.launch {
            isLoading = true
            try {
                val response = if (userRole == "dealer") {
                    apiService.getDealerShopkeepers("Bearer $token")
                } else {
                    apiService.getAdminShopkeepers("Bearer $token")
                }
                
                if (response.isSuccessful && response.body()?.success == true) {
                    accounts = response.body()?.data ?: emptyList()
                }
            } catch (e: Exception) {
                // ignore
            } finally {
                isLoading = false
            }
        }
    }

    fun createAccount(context: Context, name: String, phone: String, password: String, shopName: String, role: String, userRole: String, onDone: (Boolean) -> Unit) {
        val prefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = prefs.getString("auth_token", "") ?: ""
        if (token.isEmpty()) { onDone(false); return }

        viewModelScope.launch {
            isSubmitting = true
            try {
                val response = if (userRole == "dealer") {
                    apiService.createDealerShopkeeper(
                        "Bearer $token",
                        CreateAccountRequest(name = name, phone = phone, password = password, shopName = shopName)
                    )
                } else {
                    apiService.createAccount(
                        "Bearer $token",
                        CreateAccountRequest(name = name, phone = phone, password = password, shopName = shopName, role = role)
                    )
                }
                
                if (response.isSuccessful && response.body()?.success == true) {
                    message = "${role.replaceFirstChar { it.uppercase() }} account created"
                    fetchAccounts(context, userRole) // Refresh list
                    onDone(true)
                } else {
                    message = response.body()?.message ?: "Failed to create account"
                    onDone(false)
                }
            } catch (e: Exception) {
                message = "Connection error: ${e.message}"
                onDone(false)
            } finally {
                isSubmitting = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAccountsScreen(onBack: () -> Unit, viewModel: ManageAccountsViewModel = viewModel()) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE) }
    val userRole = (sharedPrefs.getString("user_role", "shopkeeper") ?: "shopkeeper").lowercase().trim()

    var role by remember { mutableStateOf(if (userRole == "admin") "dealer" else "shopkeeper") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var shopName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.fetchAccounts(context, userRole)
    }

    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.message = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (userRole == "dealer") "Manage Shops" else "Manage Accounts", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // ─── TABS SECTION ───────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Admin manages both roles; a dealer only ever manages shopkeepers
                    if (userRole == "admin") {
                        FilterChip(
                            selected = role == "dealer",
                            onClick = { role = "dealer" },
                            label = { Text("Dealers", fontWeight = FontWeight.Bold) }
                        )
                    }
                    FilterChip(
                        selected = role == "shopkeeper",
                        onClick = { role = "shopkeeper" },
                        label = { Text("Shopkeepers", fontWeight = FontWeight.Bold) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                // ─── CREATION FORM ──────────────────────────────────────────
                item {
                    Text("Create New Account", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Owner Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(value = shopName, onValueChange = { shopName = it }, label = { Text("Shop / Business Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = phone, onValueChange = { phone = it },
                                label = { Text("Phone") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = password, onValueChange = { password = it },
                                label = { Text("Password") }, singleLine = true,
                                visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(imageVector = image, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    viewModel.createAccount(context, name.trim(), phone.trim(), password, shopName.trim(), role, userRole) { ok ->
                                        if (ok) {
                                            name = ""; phone = ""; password = ""; shopName = ""
                                        }
                                    }
                                },
                                enabled = !viewModel.isSubmitting && name.isNotBlank() && phone.isNotBlank() && password.length >= 4,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (viewModel.isSubmitting) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                                } else {
                                    Text("Create ${role.replaceFirstChar { it.uppercase() }} Account", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }

                // ─── LISTING SECTION ────────────────────────────────────────
                item {
                    Text("Existing ${role.replaceFirstChar { it.uppercase() }}s", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 12.dp))
                }

                val filteredList = viewModel.accounts.filter { it.role == role }

                if (viewModel.isLoading && viewModel.accounts.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                } else if (filteredList.isEmpty()) {
                    item { 
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("No ${role}s found", color = Color.Gray)
                        }
                    }
                } else {
                    items(filteredList) { acc ->
                        AccountRow(acc)
                        Spacer(Modifier.height(10.dp))
                    }
                }
                
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun AccountRow(acc: ShopkeeperAccount) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = Color(0xFF2563EB).copy(alpha = 0.1f),
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        (acc.name ?: "S").take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(acc.shopName ?: acc.name ?: "Unknown Shop", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(acc.phone ?: "No Phone", fontSize = 12.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                val keys = acc.keyStats?.android?.availableKeys ?: 0
                Text("$keys keys", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF10B981))
                if (acc.dealer != null) {
                    Text("via ${acc.dealer.name ?: "Dealer"}", fontSize = 9.sp, color = Color.Gray)
                }
            }
        }
    }
}
