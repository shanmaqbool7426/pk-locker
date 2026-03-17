package com.example.pklocker.ui.registration

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pklocker.ui.theme.*
import com.example.pklocker.util.LockManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    viewModel: RegistrationViewModel = viewModel()
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val lockManager = LockManager(context)

    // Notification Permission State (For Android 13+)
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Device Provisioning", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = BackgroundGray
                )
            )
        },
        containerColor = BackgroundGray
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- SECURITY SECTION ---
            SectionHeader("Security Permissions")
            
            PermissionCard(
                title = "Device Admin",
                isActive = lockManager.isAdminActive(),
                onClick = { lockManager.requestAdminPermission() }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            PermissionCard(
                title = "Screen Overlay",
                isActive = lockManager.canDrawOverlays(),
                onClick = { lockManager.requestOverlayPermission() },
                icon = Icons.Default.Warning
            )

            // Show Notification Permission Card only for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(modifier = Modifier.height(8.dp))
                PermissionCard(
                    title = "Push Notifications",
                    isActive = hasNotificationPermission,
                    onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    icon = Icons.Default.NotificationsActive
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            PermissionCard(
                title = "Anti-Uninstall",
                isActive = false, 
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                icon = Icons.Default.Shield
            )

            // --- DEVICE INFO SECTION ---
            SectionHeader("Device Hardware Info")
            
            CustomTextField(
                value = viewModel.imei,
                onValueChange = { viewModel.imei = it },
                label = "Primary IMEI",
                icon = Icons.Default.QrCodeScanner,
                onIconClick = { viewModel.startScanner(context) },
                keyboardType = KeyboardType.Number
            )
            CustomTextField(
                value = viewModel.imei2,
                onValueChange = { viewModel.imei2 = it },
                label = "Secondary IMEI (Optional)",
                icon = Icons.Default.Smartphone
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    CustomTextField(value = viewModel.brand, onValueChange = { viewModel.brand = it }, label = "Brand")
                }
                Box(modifier = Modifier.weight(1f)) {
                    CustomTextField(value = viewModel.model, onValueChange = { viewModel.model = it }, label = "Model")
                }
            }

            // --- CUSTOMER SECTION ---
            SectionHeader("Customer Information")
            
            CustomTextField(value = viewModel.name, onValueChange = { viewModel.name = it }, label = "Customer Name", icon = Icons.Default.Person)
            CustomTextField(value = viewModel.cnic, onValueChange = { viewModel.cnic = it }, label = "CNIC Number", keyboardType = KeyboardType.Number)
            CustomTextField(value = viewModel.phone, onValueChange = { viewModel.phone = it }, label = "Phone Number", icon = Icons.Default.Phone, keyboardType = KeyboardType.Phone)

            // --- EMI SECTION ---
            SectionHeader("Financial Details (EMI)")
            
            CustomTextField(value = viewModel.productName, onValueChange = { viewModel.productName = it }, label = "Product Name")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    CustomTextField(value = viewModel.totalPrice, onValueChange = { viewModel.totalPrice = it }, label = "Price", keyboardType = KeyboardType.Decimal)
                }
                Box(modifier = Modifier.weight(1f)) {
                    CustomTextField(value = viewModel.downPayment, onValueChange = { viewModel.downPayment = it }, label = "Advance", keyboardType = KeyboardType.Decimal)
                }
            }
            CustomTextField(value = viewModel.emiTenure, onValueChange = { viewModel.emiTenure = it }, label = "EMI Tenure (Months)", keyboardType = KeyboardType.Number)

            Spacer(modifier = Modifier.height(32.dp))

            // Action Buttons
            Button(
                onClick = { 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.registerDevice(context)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark),
                enabled = !viewModel.isLoading
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("REGISTER & SECURE DEVICE", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            
            TextButton(onClick = { viewModel.testUnlock(context) }) {
                Text("Emergency Unlock (Test Only)", color = Color.Gray)
            }

            // Status Message
            viewModel.message?.let {
                Card(
                    modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (viewModel.isSuccess) SuccessGreen.copy(alpha = 0.1f) else ErrorRed.copy(alpha = 0.1f))
                ) {
                    Text(it, modifier = Modifier.padding(16.dp), color = if (viewModel.isSuccess) SuccessGreen else ErrorRed, fontWeight = FontWeight.Medium)
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = AccentOrange,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun PermissionCard(
    title: String,
    isActive: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon ?: Icons.Default.Security,
                    contentDescription = null,
                    tint = if (isActive) SuccessGreen else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
            }
            
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isActive) SuccessGreen.copy(alpha = 0.1f) else ErrorRed.copy(alpha = 0.1f)
            ) {
                Text(
                    text = if (isActive) "ACTIVE" else "REQUIRED",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = if (isActive) SuccessGreen else ErrorRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onIconClick: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        leadingIcon = if (icon != null) { { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) } } else null,
        trailingIcon = if (onIconClick != null && icon != null) {
            { IconButton(onClick = onIconClick) { Icon(icon, contentDescription = null) } }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryDark,
            unfocusedBorderColor = Color.LightGray,
            cursorColor = PrimaryDark
        )
    )
}
