package com.example.pklocker.ui.login

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
import androidx.lifecycle.viewmodel.compose.viewModel

// Consistent Premium Theme Colors
private val SoftBg = Color(0xFFF8FAFC)
private val CardWhite = Color.White
private val BrandBlue = Color(0xFF2563EB)
private val TextTitle = Color(0xFF0F172A)
private val TextMuted = Color(0xFF64748B)

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)

    var showCustomerDialog by remember { mutableStateOf(false) }
    var tempImei by remember { mutableStateOf("") }

    if (viewModel.isLoggedIn) {
        onLoginSuccess()
    }

    if (showCustomerDialog) {
        AlertDialog(
            onDismissRequest = { showCustomerDialog = false },
            title = { Text("Setup Individual Terminal", fontWeight = FontWeight.Black, color = TextTitle) },
            text = {
                Column {
                    Text("Enter the designated IMEI recorded in your administrative dashboard for this terminal.", fontSize = 13.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(16.dp))
                    LoginInput(
                        value = tempImei,
                        onValueChange = { tempImei = it },
                        label = "Terminal IMEI",
                        icon = Icons.Default.QrCodeScanner,
                        keyboardType = KeyboardType.Number
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(color = Color(0xFFFEF2F2), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            "CRITICAL: System permissions must be granted for security protocols to activate.", 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 11.sp, 
                            color = Color(0xFFDC2626),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempImei.length >= 10) {
                            sharedPrefs.edit().apply {
                                putBoolean("is_customer", true)
                                putString("device_imei", tempImei)
                                apply()
                            }
                            showCustomerDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    shape = RoundedCornerShape(12.dp),
                    enabled = tempImei.length >= 10
                ) {
                    Text("ACTIVATE PROTOCOL", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomerDialog = false }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = CardWhite,
            shape = RoundedCornerShape(24.dp)
        )
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
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF3B82F6), BrandBlue))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "PK LOCKER",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = TextTitle,
                letterSpacing = 4.sp
            )
            Text(
                text = "SECURE ADMINISTRATIVE TERMINAL",
                fontSize = 10.sp,
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
                    Text("Administrative Access", fontWeight = FontWeight.Black, fontSize = 20.sp, color = TextTitle)
                    Text("Enter valid clearance credentials.", fontSize = 13.sp, color = TextMuted)
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    LoginInput(
                        value = viewModel.email,
                        onValueChange = { viewModel.email = it },
                        label = "ACCESS EMAIL",
                        icon = Icons.Default.Email
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LoginInput(
                        value = viewModel.password,
                        onValueChange = { viewModel.password = it },
                        label = "SECURITY PASSWORD",
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
                            Text("SIGN IN TO TERMINAL", fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.sp)
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

            Spacer(modifier = Modifier.height(60.dp))
            
            // Subtle Setup Area
            Text("NOT AN ADMINISTRATOR?", fontSize = 11.sp, fontWeight = FontWeight.Black, color = TextMuted, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showCustomerDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1E293B)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Dns, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("SETUP INDIVIDUAL TERMINAL", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
