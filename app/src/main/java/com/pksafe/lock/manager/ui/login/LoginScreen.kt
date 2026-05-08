package com.pksafe.lock.manager.ui.login

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pksafe.lock.manager.ui.theme.BrandBlue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pksafe.lock.manager.CustomerStatusScreen

// Consistent Premium Theme Colors
private val SoftBg = Color(0xFFF8FAFC)
private val CardWhite = Color.White
private val TextTitle = Color(0xFF0F172A)
private val TextMuted = Color(0xFF64748B)

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignup: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)

    var showCustomerScreen by remember { mutableStateOf(false) }

    if (viewModel.isLoggedIn) {
        onLoginSuccess()
    }

    // If button clicked, show the Customer status/setup screen directly
    if (showCustomerScreen) {
        val imei = sharedPrefs.getString("device_imei", "") ?: ""
        CustomerStatusScreen(
            token = sharedPrefs.getString("fcm_token", "Fetching...") ?: "Fetching...",
            imei = imei.ifBlank { "Not Set" },
            isLocked = sharedPrefs.getBoolean("is_locked", false),
            onImeiSubmit = { newImei ->
                sharedPrefs.edit().putString("device_imei", newImei).apply()
            },
            onManualLock = {
                sharedPrefs.edit().putBoolean("is_locked", true).apply()
            },
            onReset = {
                sharedPrefs.edit().clear().apply()
                showCustomerScreen = false
            }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(SoftBg)) {
        // Subtle Background Design Element
        Box(
            modifier = Modifier
                .size(400.dp)
                .offset(y = (-200).dp, x = 100.dp)
                .background(Brush.radialGradient(listOf(BrandBlue.copy(0.08f), Color.Transparent)))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // Premium Logo Section
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(4.dp)
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.pksafe.lock.manager.R.drawable.app_logo),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "PK LOCKER",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = TextTitle,
                letterSpacing = 2.sp
            )
            Text(
                text = "SECURE DEVICE MANAGEMENT",
                fontSize = 11.sp,
                color = BrandBlue,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Modern Login Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Shopkeeper Login", fontWeight = FontWeight.Black, fontSize = 22.sp, color = TextTitle)
                    Text("Enter your phone number and password to access dashboard.", fontSize = 13.sp, color = TextMuted)
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    LoginInput(
                        value = viewModel.phone,
                        onValueChange = { viewModel.phone = it },
                        label = "PHONE NUMBER",
                        icon = Icons.Default.Phone,
                        keyboardType = KeyboardType.Phone
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LoginInput(
                        value = viewModel.password,
                        onValueChange = { viewModel.password = it },
                        label = "PASSWORD",
                        icon = Icons.Default.PhonelinkLock,
                        isPassword = true
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { viewModel.onLoginClick(context) },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        enabled = !viewModel.isLoading,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        if (viewModel.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp, color = Color.White)
                        } else {
                            Text("SIGN IN", fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.sp)
                        }
                    }
                    
                    if (viewModel.errorMessage != null) {
                        Text(
                            text = viewModel.errorMessage!!,
                            color = Color(0xFFDC2626),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Divider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(horizontal = 40.dp))
            Spacer(modifier = Modifier.height(24.dp))

            Text("NOT A SHOPKEEPER?", fontSize = 11.sp, fontWeight = FontWeight.Black, color = TextMuted, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showCustomerScreen = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1E293B)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Dns, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("SETUP CUSTOMER LOCK SCREEN", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            color = BrandBlue,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            leadingIcon = { Icon(icon, null, tint = TextMuted, modifier = Modifier.size(20.dp)) },
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else null,
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFF8FAFC),
                unfocusedContainerColor = Color(0xFFF8FAFC),
                focusedBorderColor = BrandBlue,
                unfocusedBorderColor = Color(0xFFF1F5F9),
                focusedTextColor = TextTitle,
                unfocusedTextColor = TextTitle
            ),
            singleLine = true
        )
    }
}
