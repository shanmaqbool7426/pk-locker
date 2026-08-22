package com.pksafe.lock.manager.ui.registration

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.pksafe.lock.manager.ui.theme.*
import com.pksafe.lock.manager.util.LockManager

// ═════════════════════════════════════════════════════════════════════════════
//  PK LOCKER — Registration Screen (Professional Redesign)
//  All business logic preserved. UI completely refreshed.
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    viewModel: RegistrationViewModel = viewModel(),
    onRegistrationSuccess: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val lockManager = LockManager(context)

    LaunchedEffect(viewModel.isSuccess) {
        if (viewModel.isSuccess) {
            kotlinx.coroutines.delay(1500)
            onRegistrationSuccess()
        }
    }

    // ─── Image Pickers ──────────────────────────────────────────────────────
    val customerImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val base64 = viewModel.convertUriToBase64(context, it)
            if (base64 != null) viewModel.customerCnicImage = base64
            else Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }
    val guarantorImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val base64 = viewModel.convertUriToBase64(context, it)
            if (base64 != null) viewModel.guarantorCnicImage = base64
            else Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }
    val profileImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val base64 = viewModel.convertUriToBase64(context, it)
            if (base64 != null) viewModel.profilePicture = base64
            else Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Notification Permission ────────────────────────────────────────────
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
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Register Device", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextTitle)
                        Text("Add new customer to your portfolio", fontSize = 11.sp, color = TextMuted)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CardWhite)
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

            // ═══ SECTION 1: DEVICE IDENTITY ════════════════════════════════════
            FormSectionHeader(
                step = "1",
                title = "Device Identity",
                icon = Icons.Default.Smartphone,
                subtitle = "Enter the device hardware details"
            )
            FormCard {
                FormField(
                    value = viewModel.imei,
                    onValueChange = { viewModel.imei = it },
                    label = "Primary IMEI / Serial",
                    icon = Icons.Default.QrCodeScanner,
                    trailingAction = {
                        IconButton(onClick = { viewModel.startScanner(context) }) {
                            Icon(Icons.Default.QrCodeScanner, "Scan IMEI", tint = BrandBlue, modifier = Modifier.size(20.dp))
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                FormField(
                    value = viewModel.imei2,
                    onValueChange = { viewModel.imei2 = it },
                    label = "Secondary IMEI (Optional)",
                    icon = Icons.Default.SimCard
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        FormField(viewModel.brand, { viewModel.brand = it }, "Brand", Icons.Default.Brush)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        FormField(viewModel.model, { viewModel.model = it }, "Model", Icons.Default.ModelTraining)
                    }
                }
            }

            // ═══ SECTION 2: CUSTOMER INFO ══════════════════════════════════════
            FormSectionHeader(
                step = "2",
                title = "Customer Information",
                icon = Icons.Default.Face,
                subtitle = "Personal details & identification"
            )
            FormCard {
                FormField(viewModel.name, { viewModel.name = it }, "Full Name", Icons.Default.Badge)
                Spacer(modifier = Modifier.height(8.dp))
                FormField(viewModel.cnic, { viewModel.cnic = it }, "CNIC Number", Icons.Default.CreditCard, keyboardType = KeyboardType.Number)
                Spacer(modifier = Modifier.height(8.dp))
                FormField(viewModel.phone, { viewModel.phone = it }, "Phone Number", Icons.Default.Call, keyboardType = KeyboardType.Phone)

                // Photos
                Spacer(modifier = Modifier.height(16.dp))
                Text("Photos", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextTitle)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        PhotoPicker(
                            image = viewModel.profilePicture,
                            label = "Customer Photo",
                            icon = Icons.Default.Person
                        ) { profileImageLauncher.launch("image/*") }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        PhotoPicker(
                            image = viewModel.customerCnicImage,
                            label = "CNIC Photo",
                            icon = Icons.Default.CreditCard
                        ) { customerImageLauncher.launch("image/*") }
                    }
                }
            }

            // ═══ SECTION 3: GUARANTOR ══════════════════════════════════════════
            FormSectionHeader(
                step = "3",
                title = "Guarantor Details",
                icon = Icons.Default.Group,
                subtitle = "Guarantor verification for security"
            )
            FormCard {
                FormField(viewModel.guarantorName, { viewModel.guarantorName = it }, "Guarantor Name", Icons.Default.PersonSearch)
                Spacer(modifier = Modifier.height(8.dp))
                FormField(viewModel.guarantorPhone, { viewModel.guarantorPhone = it }, "Phone Number", Icons.Default.Call, keyboardType = KeyboardType.Phone)
                Spacer(modifier = Modifier.height(8.dp))
                FormField(viewModel.guarantorAddress, { viewModel.guarantorAddress = it }, "Address", Icons.Default.LocationOn)

                Spacer(modifier = Modifier.height(12.dp))
                PhotoPicker(
                    image = viewModel.guarantorCnicImage,
                    label = "Guarantor CNIC",
                    icon = Icons.Default.CreditCard,
                    fullWidth = true
                ) { guarantorImageLauncher.launch("image/*") }
            }

            // ═══ SECTION 4: PAYMENT & EMI ═════════════════════════════════════
            FormSectionHeader(
                step = "4",
                title = "Payment & EMI Terms",
                icon = Icons.Default.AccountBalanceWallet,
                subtitle = "Define loan amount and installment plan"
            )
            FormCard {
                FormField(viewModel.productName, { viewModel.productName = it }, "Product / Model Name", Icons.Default.Inventory2)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        FormField(viewModel.totalPrice, { viewModel.totalPrice = it }, "Total Price (PKR)", Icons.Default.AttachMoney, keyboardType = KeyboardType.Decimal)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        FormField(viewModel.downPayment, { viewModel.downPayment = it }, "Down Payment", Icons.Default.PriceCheck, keyboardType = KeyboardType.Decimal)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                FormField(viewModel.emiTenure, { viewModel.emiTenure = it }, "Tenure (Months)", Icons.Default.Schedule, keyboardType = KeyboardType.Number)

                // ── Auto-Computed Summary ───────────────────────────────────
                val total = viewModel.totalPrice.toDoubleOrNull() ?: 0.0
                val down = viewModel.downPayment.toDoubleOrNull() ?: 0.0
                val balance = (total - down).coerceAtLeast(0.0)
                val tenure = viewModel.emiTenure.toIntOrNull() ?: 0
                val emiAmount = if (tenure > 0) balance / tenure else 0.0

                if (total > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    EmiSummaryCard(balance = balance, emiAmount = emiAmount, tenure = tenure)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ═══ SUBMIT BUTTON ═════════════════════════════════════════════════
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else viewModel.registerDevice(context)
                },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                enabled = !viewModel.isLoading
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                } else {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("REGISTER DEVICE", fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.5.sp)
                }
            }

            // ── Result Messages ────────────────────────────────────────────
            viewModel.message?.let { msg ->
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (viewModel.isSuccess) SuccessSurface else DangerSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (viewModel.isSuccess) Success.copy(alpha = 0.2f) else Danger.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (viewModel.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            null,
                            tint = if (viewModel.isSuccess) Success else Danger,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            msg,
                            color = if (viewModel.isSuccess) Success else Danger,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  FORM COMPONENTS
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun FormSectionHeader(step: String, title: String, icon: ImageVector, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Step number badge
        Surface(
            shape = CircleShape,
            color = BrandBlue,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(step, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextTitle
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun FormCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        leadingIcon = icon?.let {
            { Icon(it, null, modifier = Modifier.size(20.dp), tint = TextMuted) }
        },
        trailingIcon = trailingAction,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandBlue,
            unfocusedBorderColor = BorderLight,
            focusedContainerColor = SurfaceGray.copy(alpha = 0.5f),
            unfocusedContainerColor = SurfaceGray.copy(alpha = 0.3f),
            focusedTextColor = TextTitle,
            unfocusedTextColor = TextTitle,
            focusedLabelColor = BrandBlue,
            unfocusedLabelColor = TextMuted
        ),
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    )
}

@Composable
private fun PhotoPicker(
    image: String?,
    label: String,
    icon: ImageVector = Icons.Default.AddAPhoto,
    fullWidth: Boolean = false,
    onClick: () -> Unit
) {
    val hasImage = image != null
    Box(
        modifier = Modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier.fillMaxWidth())
            .height(100.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (hasImage) SuccessSurface else SurfaceGray)
            .border(
                1.dp,
                if (hasImage) Success.copy(alpha = 0.3f) else BorderLight,
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (hasImage) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Captured", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Success)
                    Text("Tap to change", fontSize = 10.sp, color = TextMuted)
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, tint = TextSubtle, modifier = Modifier.size(26.dp))
                Spacer(modifier = Modifier.height(6.dp))
                Text(label, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = TextMuted)
            }
        }
    }
}

@Composable
private fun EmiSummaryCard(balance: Double, emiAmount: Double, tenure: Int) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BrandBlueSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandBlue.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Calculate, null, tint = BrandBlue, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("AUTO-CALCULATED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BrandBlue, letterSpacing = 1.sp)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Balance", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Rs. ${balance.toInt()}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextTitle
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Monthly EMI", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Rs. ${emiAmount.toInt()} /mo",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Success
                    )
                }
            }
            if (tenure > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BrandBlue.copy(alpha = 0.08f)
                ) {
                    Text(
                        "$tenure monthly installments",
                        fontSize = 11.sp,
                        color = BrandBlue,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
