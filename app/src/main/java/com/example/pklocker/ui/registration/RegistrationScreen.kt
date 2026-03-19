package com.example.pklocker.ui.registration

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pklocker.ui.theme.*
import com.example.pklocker.util.LockManager

// Local color constants for consistent premium look
private val SurfaceWhite = Color.White
private val SoftBg = Color(0xFFF8FAFC)
private val BrandAccent = Color(0xFF2563EB)
private val TextDark = Color(0xFF0F172A)
private val TextMuted = Color(0xFF64748B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    viewModel: RegistrationViewModel = viewModel()
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val lockManager = LockManager(context)

    // Image Picker Launcher - Customer
    val customerImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val base64 = viewModel.convertUriToBase64(context, it)
            if (base64 != null) viewModel.customerCnicImage = base64
            else Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    // Image Picker Launcher - Guarantor
    val guarantorImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val base64 = viewModel.convertUriToBase64(context, it)
            if (base64 != null) viewModel.guarantorCnicImage = base64
            else Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    // Notification Permission Launcher
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasNotificationPermission = granted
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Device Registration", fontWeight = FontWeight.ExtraBold, color = TextDark) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = SurfaceWhite)
            )
        },
        containerColor = SoftBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            
            // --- SECURITY PERMISSIONS ---
            SectionHeader("Security & Permissions", Icons.Default.VerifiedUser)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    CompactPermissionRow("Device Admin", lockManager.isAdminActive(), Icons.Default.Shield) { lockManager.requestAdminPermission() }
                    HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 40.dp))
                    CompactPermissionRow("Overlay Screens", lockManager.canDrawOverlays(), Icons.Default.Layers) { lockManager.requestOverlayPermission() }
                    HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 40.dp))
                    CompactPermissionRow("Anti-Uninstall", false, Icons.Default.Security) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 40.dp))
                        CompactPermissionRow("Notifications", hasNotificationPermission, Icons.Default.Notifications) { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    }
                }
            }

            // --- DEVICE HARDWARE ---
            SectionHeader("Device Identity", Icons.Default.Smartphone)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ModernTextField(
                        value = viewModel.imei,
                        onValueChange = { viewModel.imei = it },
                        label = "Primary IMEI / Serial",
                        icon = Icons.Default.QrCodeScanner,
                        trailingIcon = true,
                        onIconClick = { viewModel.startScanner(context) }
                    )
                    ModernTextField(value = viewModel.imei2, onValueChange = { viewModel.imei2 = it }, label = "Secondary IMEI (Optional)")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) { ModernTextField(viewModel.brand, { viewModel.brand = it }, "Brand") }
                        Box(modifier = Modifier.weight(1f)) { ModernTextField(viewModel.model, { viewModel.model = it }, "Model") }
                    }
                }
            }

            // --- CUSTOMER DETAILS ---
            SectionHeader("Customer Information", Icons.Default.Face)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ModernTextField(viewModel.name, { viewModel.name = it }, "Full Name", Icons.Default.Badge)
                    ModernTextField(viewModel.cnic, { viewModel.cnic = it }, "CNIC Number", keyboardType = KeyboardType.Number)
                    ModernTextField(viewModel.phone, { viewModel.phone = it }, "Phone Number", Icons.Default.Call, keyboardType = KeyboardType.Phone)
                    
                    Text("Identity Proof (CNIC)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                    ImagePickerButton(imagePath = viewModel.customerCnicImage, label = "Capture Customer CNIC") { customerImageLauncher.launch("image/*") }
                }
            }

            // --- GUARANTOR DETAILS ---
            SectionHeader("Guarantor Verification", Icons.Default.Group)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ModernTextField(viewModel.guarantorName, { viewModel.guarantorName = it }, "Guarantor Name", Icons.Default.PersonSearch)
                    ModernTextField(viewModel.guarantorPhone, { viewModel.guarantorPhone = it }, "Guarantor Phone", keyboardType = KeyboardType.Phone)
                    ModernTextField(viewModel.guarantorAddress, { viewModel.guarantorAddress = it }, "Address")
                    
                    Text("Guarantor ID Proof", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                    ImagePickerButton(imagePath = viewModel.guarantorCnicImage, label = "Capture Guarantor CNIC") { guarantorImageLauncher.launch("image/*") }
                }
            }

            // --- FINANCE / EMI ---
            SectionHeader("Payment & EMI Terms", Icons.Default.AccountBalanceWallet)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ModernTextField(viewModel.productName, { viewModel.productName = it }, "Product / Model Name")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) { ModernTextField(viewModel.totalPrice, { viewModel.totalPrice = it }, "Total Price", keyboardType = KeyboardType.Decimal) }
                        Box(modifier = Modifier.weight(1f)) { ModernTextField(viewModel.downPayment, { viewModel.downPayment = it }, "Down Payment", keyboardType = KeyboardType.Decimal) }
                    }
                    ModernTextField(viewModel.emiTenure, { viewModel.emiTenure = it }, "Tenure (Months)", keyboardType = KeyboardType.Number)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Professional Action Button
            Button(
                onClick = { 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else viewModel.registerDevice(context)
                },
                modifier = Modifier.fillMaxWidth().height(64.dp), // Slightly taller for premium feel
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandAccent), 
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                enabled = !viewModel.isLoading
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, null, modifier = Modifier.size(20.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("FINALIZE REGISTRATION", fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.sp, color = Color.White)
                    }
                }
            }

            // Result Messages
            viewModel.message?.let { msg ->
                Card(
                    modifier = Modifier.padding(top = 20.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (viewModel.isSuccess) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
                    )
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if(viewModel.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            null,
                            tint = if(viewModel.isSuccess) Color(0xFF16A34A) else Color(0xFFDC2626)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(msg, color = if(viewModel.isSuccess) Color(0xFF16A34A) else Color(0xFFDC2626), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 10.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = BrandAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Black, color = BrandAccent, letterSpacing = 1.5.sp)
    }
}

@Composable
fun CompactPermissionRow(title: String, isActive: Boolean, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (isActive) Color(0xFF16A34A) else Color(0xFF94A3B8), modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (isActive) TextDark else TextMuted)
        }
        Text(
            if (isActive) "ACTIVE" else "GRANT",
            color = if (isActive) Color(0xFF16A34A) else Color(0xFFEF4444),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun ImagePickerButton(imagePath: String?, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF1F5F9))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (imagePath != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF16A34A), modifier = Modifier.size(32.dp))
                Text("DOCUMENT CAPTURED", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF16A34A))
                Text("Tap to change", fontSize = 10.sp, color = TextMuted)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AddAPhoto, null, tint = TextMuted, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextMuted)
            }
        }
    }
}

@Composable
fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector? = null,
    trailingIcon: Boolean = false,
    onIconClick: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF475569)) }, // Darker Slate for Label
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        leadingIcon = icon?.let { { Icon(it, null, modifier = Modifier.size(20.dp), tint = Color(0xFF1E293B)) } }, // Darker icon
        trailingIcon = if (trailingIcon) { { IconButton(onClick = { onIconClick?.invoke() }) { Icon(icon!!, null, tint = BrandAccent) } } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandAccent,
            unfocusedBorderColor = Color(0xFFCBD5E1), // Proper border color
            focusedContainerColor = Color(0xFFF8FAFC), // Slight tint
            unfocusedContainerColor = Color(0xFFF8FAFC),
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black,
            focusedLabelColor = BrandAccent,
            unfocusedLabelColor = Color(0xFF475569) // Clearly visible unfocused label
        ),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold) // Bolder input text
    )
}
