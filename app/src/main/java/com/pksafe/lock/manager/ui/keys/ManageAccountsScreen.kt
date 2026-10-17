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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
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
import com.pksafe.lock.manager.data.AllocateKeysRequest
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

    // ─ Admin: Edit account ──
    fun editAccount(context: Context, id: String, body: Map<String, Any>, onResult: (Boolean, String) -> Unit) {
        val prefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = prefs.getString("auth_token", "") ?: ""
        if (token.isEmpty()) { onResult(false, "Not logged in"); return }

        viewModelScope.launch {
            isSubmitting = true
            try {
                val response = apiService.editAccount("Bearer $token", id, body)
                if (response.isSuccessful && response.body()?.success == true) {
                    fetchAccounts(context, prefs.getString("user_role", "admin") ?: "admin")
                    onResult(true, "Account updated")
                } else {
                    onResult(false, response.body()?.message ?: "Update failed")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "Network error")
            } finally {
                isSubmitting = false
            }
        }
    }

    // ── Admin: Delete account ──
    fun deleteAccount(context: Context, id: String, onResult: (Boolean, String) -> Unit) {
        val prefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = prefs.getString("auth_token", "") ?: ""
        if (token.isEmpty()) { onResult(false, "Not logged in"); return }

        viewModelScope.launch {
            isSubmitting = true
            try {
                val response = apiService.deleteAccount("Bearer $token", id)
                if (response.isSuccessful && response.body()?.success == true) {
                    fetchAccounts(context, prefs.getString("user_role", "admin") ?: "admin")
                    onResult(true, "Account deleted")
                } else {
                    onResult(false, response.body()?.message ?: "Delete failed")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "Network error")
            } finally {
                isSubmitting = false
            }
        }
    }

    // ── Admin: Allocate keys to user ──
    fun allocateKeys(context: Context, shopkeeperId: String, platform: String, keys: Int, onResult: (Boolean, String) -> Unit) {
        val prefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)
        val token = prefs.getString("auth_token", "") ?: ""
        if (token.isEmpty()) { onResult(false, "Not logged in"); return }

        viewModelScope.launch {
            isSubmitting = true
            try {
                val response = apiService.allocateKeys("Bearer $token", AllocateKeysRequest(shopkeeperId, platform, keys))
                if (response.isSuccessful && response.body()?.success == true) {
                    fetchAccounts(context, prefs.getString("user_role", "admin") ?: "admin")
                    onResult(true, response.body()?.message ?: "$keys $platform keys allocated")
                } else {
                    onResult(false, response.body()?.message ?: "Allocation failed")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "Network error")
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

    var role by remember { mutableStateOf(if (userRole == "admin") "all" else "shopkeeper") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var shopName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<ShopkeeperAccount?>(null) }
    var deletingAccount by remember { mutableStateOf<ShopkeeperAccount?>(null) }
    var allocateKeysAccount by remember { mutableStateOf<ShopkeeperAccount?>(null) }

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
                            selected = role == "all",
                            onClick = { role = "all" },
                            label = { Text("All Users", fontWeight = FontWeight.Bold) }
                        )
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
                    Text(if (role == "all") "All Accounts" else "Existing ${role.replaceFirstChar { it.uppercase() }}s", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 12.dp))
                }

                val filteredList = if (role == "all") viewModel.accounts else viewModel.accounts.filter { it.role == role }

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
                        AccountRow(
                            acc = acc,
                            isAdmin = userRole == "admin",
                            onEdit = {
                                editingAccount = acc
                                showEditDialog = true
                            },
                            onDelete = {
                                deletingAccount = acc
                                showDeleteDialog = true
                            },
                            onAllocateKeys = {
                                allocateKeysAccount = acc
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
                
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }

    // ── Edit Account Dialog ──
    if (showEditDialog && editingAccount != null) {
        EditAccountDialog(
            account = editingAccount!!,
            onDismiss = { showEditDialog = false; editingAccount = null },
            onSave = { fields ->
                viewModel.editAccount(context, editingAccount!!.id ?: "", fields) { ok, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (ok) {
                        showEditDialog = false
                        editingAccount = null
                    }
                }
            }
        )
    }

    // ── Delete Confirmation Dialog ──
    if (showDeleteDialog && deletingAccount != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; deletingAccount = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Account", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                }
            },
            text = {
                Column {
                    Text("Are you sure you want to delete this account?")
                    Spacer(Modifier.height(8.dp))
                    Text("Name: ${deletingAccount!!.name ?: "Unknown"}", fontWeight = FontWeight.SemiBold)
                    Text("Shop: ${deletingAccount!!.shopName ?: "N/A"}", color = Color.Gray)
                    Text("Phone: ${deletingAccount!!.phone ?: "N/A"}", color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Text("This will soft-delete the account. They won't be able to log in.", fontSize = 12.sp, color = Color(0xFFEF4444))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAccount(context, deletingAccount!!.id ?: "") { ok, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (ok) {
                                showDeleteDialog = false
                                deletingAccount = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; deletingAccount = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Allocate Keys Dialog ──
    if (allocateKeysAccount != null) {
        AllocateKeysDialog(
            account = allocateKeysAccount!!,
            onDismiss = { allocateKeysAccount = null },
            onAllocate = { platform, keys ->
                viewModel.allocateKeys(context, allocateKeysAccount!!.id ?: "", platform, keys) { ok, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (ok) {
                        allocateKeysAccount = null
                    }
                }
            }
        )
    }
}

@Composable
private fun AccountRow(
    acc: ShopkeeperAccount,
    isAdmin: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAllocateKeys: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Text(
                        (acc.role ?: "shopkeeper").replaceFirstChar { it.uppercase() },
                        fontSize = 11.sp,
                        color = if (acc.role == "admin") Color(0xFFEF4444) else if (acc.role == "dealer") Color(0xFF7C3AED) else Color(0xFF10B981),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    val keys = acc.keyStats?.android?.availableKeys ?: 0
                    Text("$keys keys", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF10B981))
                    if (acc.dealer != null) {
                        Text("via ${acc.dealer.name ?: "Dealer"}", fontSize = 9.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (isAdmin && acc.role != "admin") {
                    TextButton(
                        onClick = onAllocateKeys,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF59E0B))
                    ) {
                        Icon(Icons.Default.Key, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Keys", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(
                    onClick = onEdit,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF2563EB))
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Edit Account Dialog
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAccountDialog(
    account: ShopkeeperAccount,
    onDismiss: () -> Unit,
    onSave: (Map<String, Any>) -> Unit
) {
    var name by remember { mutableStateOf(account.name ?: "") }
    var phone by remember { mutableStateOf(account.phone ?: "") }
    var shopName by remember { mutableStateOf(account.shopName ?: "") }
    var selectedRole by remember { mutableStateOf(account.role ?: "shopkeeper") }
    var isActive by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, null, tint = Color(0xFF2563EB))
                Spacer(Modifier.width(8.dp))
                Text("Edit Account", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = shopName, onValueChange = { shopName = it },
                    label = { Text("Shop Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                Text("Role", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("shopkeeper", "dealer", "admin").forEach { r ->
                        FilterChip(
                            selected = selectedRole == r,
                            onClick = { selectedRole = r },
                            label = { Text(r.replaceFirstChar { it.uppercase() }, fontSize = 12.sp) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Active", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSaving = true
                    val fields = mutableMapOf<String, Any>()
                    if (name.isNotBlank()) fields["name"] = name
                    if (phone.isNotBlank()) fields["phone"] = phone
                    if (shopName.isNotBlank()) fields["shopName"] = shopName
                    fields["role"] = selectedRole
                    fields["isActive"] = isActive
                    onSave(fields)
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════════
//  Allocate Keys Dialog
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllocateKeysDialog(
    account: ShopkeeperAccount,
    onDismiss: () -> Unit,
    onAllocate: (platform: String, keys: Int) -> Unit
) {
    var platform by remember { mutableStateOf("android") }
    var keysText by remember { mutableStateOf("") }
    var isAllocating by remember { mutableStateOf(false) }

    val keysCount = keysText.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = { if (!isAllocating) onDismiss() },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, null, tint = Color(0xFFF59E0B))
                Spacer(Modifier.width(8.dp))
                Text("Allocate Keys", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Current user info
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("User: ${account.name ?: "Unknown"}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Shop: ${account.shopName ?: "N/A"}", fontSize = 12.sp, color = Color.Gray)
                        val androidKeys = account.keyStats?.android?.availableKeys ?: 0
                        val iosKeys = account.keyStats?.ios?.availableKeys ?: 0
                        Text("Current: Android=$androidKeys | iOS=$iosKeys", fontSize = 11.sp, color = Color(0xFF92400E))
                    }
                }

                // Platform selection
                Text("Platform", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("android" to "Android", "ios" to "iOS").forEach { (value, label) ->
                        FilterChip(
                            selected = platform == value,
                            onClick = { platform = value },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                // Keys count input
                OutlinedTextField(
                    value = keysText,
                    onValueChange = { keysText = it.filter { c -> c.isDigit() } },
                    label = { Text("Number of Keys") },
                    placeholder = { Text("e.g. 10") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (keysCount > 0) {
                        isAllocating = true
                        onAllocate(platform, keysCount)
                    }
                },
                enabled = !isAllocating && keysCount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isAllocating) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text("Allocate $keysCount Keys", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isAllocating) { Text("Cancel") }
        }
    )
}
