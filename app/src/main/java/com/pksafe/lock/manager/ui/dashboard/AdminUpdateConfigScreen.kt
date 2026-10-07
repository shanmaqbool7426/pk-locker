package com.pksafe.lock.manager.ui.dashboard

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
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
import com.pksafe.lock.manager.data.ApiService
import com.pksafe.lock.manager.data.AppUpdateConfig
import com.pksafe.lock.manager.ui.theme.*
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AdminUpdateViewModel : ViewModel() {
    private val retrofit = Retrofit.Builder()
        .baseUrl(com.pksafe.lock.manager.util.Constants.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(ApiService::class.java)

    var config by mutableStateOf<AppUpdateConfig?>(null)
    var isLoading by mutableStateOf(false)
    var isSaving by mutableStateOf(false)

    fun fetchConfig() {
        viewModelScope.launch {
            isLoading = true
            try {
                val response = apiService.getUpdateConfig()
                if (response.isSuccessful) {
                    config = response.body()?.data
                }
            } catch (e: Exception) { }
            finally { isLoading = false }
        }
    }

    fun saveConfig(context: android.content.Context, newConfig: AppUpdateConfig, onComplete: () -> Unit) {
        val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", android.content.Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("auth_token", "") ?: ""
        
        viewModelScope.launch {
            isSaving = true
            try {
                val response = apiService.postUpdateConfig("Bearer $token", newConfig)
                if (response.isSuccessful) {
                    Toast.makeText(context, "\u2705 Update pushed! All shopkeeper apps will see the update popup.", Toast.LENGTH_LONG).show()
                    onComplete()
                } else {
                    Toast.makeText(context, "Failed: ${response.message()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Network Error", Toast.LENGTH_SHORT).show()
            }
            finally { isSaving = false }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUpdateConfigScreen(onBack: () -> Unit) {
    val viewModel: AdminUpdateViewModel = viewModel()
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        viewModel.fetchConfig()
    }

    val currentConfig = viewModel.config

    var vCode by remember(currentConfig) { mutableStateOf(currentConfig?.versionCode?.toString() ?: "") }
    var vName by remember(currentConfig) { mutableStateOf(currentConfig?.versionName ?: "") }
    var uUrl by remember(currentConfig) { mutableStateOf(currentConfig?.updateUrl ?: "https://pk-locker-api.vercel.app/apk/update.apk") }
    var uTitle by remember(currentConfig) { mutableStateOf(currentConfig?.updateTitle ?: "") }
    var uMessage by remember(currentConfig) { mutableStateOf(currentConfig?.updateMessage ?: "") }
    var isForce by remember(currentConfig) { mutableStateOf(currentConfig?.isForceUpdate ?: false) }
    var isEnabled by remember(currentConfig) { mutableStateOf(currentConfig?.isUpdateEnabled ?: false) }

    val currentVersionCode = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode
    } catch (e: Exception) { 1 }

    // Will this config trigger an update on shopkeeper devices?
    val willPushUpdate = isEnabled && (currentConfig?.versionCode ?: 0) > currentVersionCode

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Push Update to Shopkeepers", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (vCode.isBlank() || (vCode.toIntOrNull() ?: 0) <= currentVersionCode) {
                        Toast.makeText(context, "Version code must be greater than $currentVersionCode", Toast.LENGTH_LONG).show()
                        return@ExtendedFloatingActionButton
                    }
                    if (!isEnabled) {
                        Toast.makeText(context, "Turn ON 'Update Service' first", Toast.LENGTH_SHORT).show()
                        return@ExtendedFloatingActionButton
                    }
                    val newConfig = AppUpdateConfig(
                        versionCode = vCode.toIntOrNull() ?: 0,
                        versionName = vName,
                        updateUrl = uUrl,
                        updateTitle = uTitle.ifBlank { "Update Available" },
                        updateMessage = uMessage.ifBlank { "A new version of PK Locker is available. Please update now." },
                        isForceUpdate = isForce,
                        isUpdateEnabled = isEnabled
                    )
                    viewModel.saveConfig(context, newConfig, onBack)
                },
                containerColor = if (willPushUpdate) Success else BrandBlue,
                contentColor = Color.White
            ) {
                if (viewModel.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Icon(Icons.Default.Devices, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PUSH UPDATE", fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                }
            }
        }
    ) { padding ->
        if (viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // ── HOW TO PUSH UPDATE — Instruction Card ───────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BrandBlueSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, null, tint = BrandBlue, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("How to push update to all shopkeepers", fontWeight = FontWeight.Bold, color = BrandBlue, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("1. Upload new APK to: ${uUrl.ifBlank { "pk-locker-api.vercel.app/apk/update.apk" }}", fontSize = 12.sp, color = TextBody)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text("2. Turn ON 'Update Service' below", fontSize = 12.sp, color = TextBody)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text("3. Set Version Code higher than $currentVersionCode (current app)", fontSize = 12.sp, color = TextBody)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text("4. Tap PUSH UPDATE — all shopkeeper apps will show update popup", fontSize = 12.sp, color = TextBody)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text("5. Shopkeeper taps UPDATE → app updates → auto-logout → re-login required", fontSize = 12.sp, color = TextBody, fontWeight = FontWeight.Bold)
                    }
                }

                // ── READINESS STATUS ──────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (willPushUpdate) SuccessSurface else SurfaceGray
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (willPushUpdate) {
                                Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ready to push!", fontWeight = FontWeight.Bold, color = Success, fontSize = 14.sp)
                            } else {
                                Icon(Icons.Default.Warning, null, tint = Danger, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Not ready yet", fontWeight = FontWeight.Bold, color = Danger, fontSize = 14.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Current app version: ", fontSize = 13.sp, color = TextMuted)
                            Text("$currentVersionCode", fontWeight = FontWeight.Bold, color = TextTitle, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Server version: ", fontSize = 13.sp, color = TextMuted)
                            val serverVCode = currentConfig?.versionCode ?: 0
                            Text(
                                "$serverVCode",
                                fontWeight = FontWeight.Bold,
                                color = if (serverVCode > currentVersionCode) Success else TextTitle,
                                fontSize = 13.sp
                            )
                            if (serverVCode > currentVersionCode) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("(newer)", fontSize = 11.sp, color = Success, fontWeight = FontWeight.Bold)
                            } else {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("(set > $currentVersionCode to push)", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Update Service: ", fontSize = 13.sp, color = TextMuted)
                            Text(
                                if (isEnabled) "ON" else "OFF",
                                fontWeight = FontWeight.Bold,
                                color = if (isEnabled) Success else Danger,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                HorizontalDivider()

                // ── CONFIGURATION ─────────────────────────────────────────────
                Text("Configuration", fontWeight = FontWeight.Bold, color = TextTitle, fontSize = 15.sp)
                
                // Update Service Toggle
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isEnabled) SuccessSurface else SurfaceGray)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Update Service", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextTitle)
                            Text(
                                if (isEnabled) "Shopkeepers will receive update popup" else "No updates will be sent to shopkeepers",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                    }
                }

                // Force Update Toggle
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceGray)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Force Update", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextTitle)
                            Text(
                                if (isForce) "Shopkeeper CANNOT skip — must update to continue" else "Shopkeeper can tap 'Later' to skip",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        Switch(checked = isForce, onCheckedChange = { isForce = it })
                    }
                }

                // Version Code
                OutlinedTextField(
                    value = vCode,
                    onValueChange = { vCode = it },
                    label = { Text("Version Code (must be > $currentVersionCode)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Current app = $currentVersionCode. Set to ${currentVersionCode + 1} or higher.") }
                )

                OutlinedTextField(
                    value = vName,
                    onValueChange = { vName = it },
                    label = { Text("Version Name (e.g. 1.3)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uUrl,
                    onValueChange = { uUrl = it },
                    label = { Text("APK Download URL") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Upload new APK to this location on server") }
                )

                OutlinedTextField(
                    value = uTitle,
                    onValueChange = { uTitle = it },
                    label = { Text("Popup Title (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Update Available") }
                )

                OutlinedTextField(
                    value = uMessage,
                    onValueChange = { uMessage = it },
                    label = { Text("Popup Message (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    placeholder = { Text("A new version is available...") }
                )
                
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}
