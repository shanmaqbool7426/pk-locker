package com.pksafe.lock.manager.ui.keys

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pksafe.lock.manager.data.AllocateKeysRequest
import com.pksafe.lock.manager.data.ApiClient
import com.pksafe.lock.manager.data.ShopkeeperAccount
import kotlinx.coroutines.launch

class AllocateKeysViewModel : ViewModel() {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<ShopkeeperAccount>>(emptyList())
    var isSearching by mutableStateOf(false)
    var isAllocating by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    private val apiService = ApiClient.createApiService()

    fun search(context: Context) {
        val token = authToken(context)
        if (token.isEmpty()) return

        viewModelScope.launch {
            isSearching = true
            try {
                val response = apiService.getAdminShopkeepers("Bearer $token", query.ifBlank { null })
                if (response.isSuccessful && response.body()?.success == true) {
                    results = response.body()?.data ?: emptyList()
                } else {
                    message = "Search failed"
                }
            } catch (e: Exception) {
                message = "Connection error"
            } finally {
                isSearching = false
            }
        }
    }

    fun allocate(context: Context, shopkeeperId: String, platform: String, keys: Int, onDone: (Boolean) -> Unit) {
        val token = authToken(context)
        if (token.isEmpty()) { onDone(false); return }

        viewModelScope.launch {
            isAllocating = true
            try {
                val response = apiService.allocateKeys("Bearer $token", AllocateKeysRequest(shopkeeperId, platform, keys))
                if (response.isSuccessful && response.body()?.success == true) {
                    message = response.body()?.message ?: "Keys allocated"
                    search(context)
                    onDone(true)
                } else {
                    message = response.body()?.message ?: "Allocation failed"
                    onDone(false)
                }
            } catch (e: Exception) {
                message = "Connection error: ${e.message}"
                onDone(false)
            } finally {
                isAllocating = false
            }
        }
    }

    private fun authToken(context: Context): String {
        val prefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        return prefs.getString("auth_token", "") ?: ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllocateKeysScreen(onBack: () -> Unit) {
    val viewModel: AllocateKeysViewModel = viewModel()
    val context = LocalContext.current
    var selectedShopkeeper by remember { mutableStateOf<ShopkeeperAccount?>(null) }

    LaunchedEffect(Unit) {
        viewModel.search(context)
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
                title = { Text("Allocate Keys", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = viewModel.query,
                    onValueChange = { viewModel.query = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Search name or phone") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { viewModel.search(context) }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (viewModel.isSearching && viewModel.results.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (viewModel.results.isEmpty()) {
                    Text(
                        "No accounts found",
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(viewModel.results) { sk ->
                            SearchResultRow(sk, onClick = { selectedShopkeeper = sk })
                        }
                    }
                }
            }
        }
    }

    if (selectedShopkeeper != null) {
        val sk = selectedShopkeeper!!
        AllocateDialog(
            shopkeeper = sk,
            isAllocating = viewModel.isAllocating,
            onDismiss = { selectedShopkeeper = null },
            onAllocate = { platform, keys ->
                viewModel.allocate(context, sk.id ?: "", platform, keys) { ok ->
                    if (ok) selectedShopkeeper = null
                }
            }
        )
    }
}

@Composable
private fun SearchResultRow(sk: ShopkeeperAccount, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFF2563EB).copy(alpha = 0.1f),
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = sk.name?.take(1)?.uppercase() ?: "",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(sk.shopName ?: sk.name ?: "", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(sk.phone ?: "", fontSize = 11.sp, color = Color.Gray)
                if (sk.dealer != null) {
                    Text(
                        "Via dealer: ${sk.dealer.shopName ?: sk.dealer.name ?: "Unknown"}",
                        fontSize = 11.sp,
                        color = Color(0xFF7C3AED),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            val available = sk.keyStats?.android?.availableKeys ?: 0
            Text("$available keys", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
        }
    }
}

@Composable
private fun AllocateDialog(
    shopkeeper: ShopkeeperAccount,
    isAllocating: Boolean,
    onDismiss: () -> Unit,
    onAllocate: (platform: String, keys: Int) -> Unit
) {
    var platform by remember { mutableStateOf("android") }
    var keysText by remember { mutableStateOf("") }
    val keys = keysText.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allocate Keys", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("${shopkeeper.shopName ?: shopkeeper.name} — ${shopkeeper.phone}", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = platform == "android",
                        onClick = { platform = "android" },
                        label = { Text("Android") }
                    )
                    FilterChip(
                        selected = platform == "ios",
                        onClick = { platform = "ios" },
                        label = { Text("iOS") }
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = keysText,
                    onValueChange = { if (it.all { it.isDigit() }) keysText = it },
                    label = { Text("Number of keys") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isAllocating && keys > 0,
                onClick = { onAllocate(platform, keys) }
            ) {
                if (isAllocating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Allocate")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
