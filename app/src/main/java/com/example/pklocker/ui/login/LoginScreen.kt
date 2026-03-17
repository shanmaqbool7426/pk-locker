package com.example.pklocker.ui.login

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("PKLockerPrefs", Context.MODE_PRIVATE)

    // Check if device is already registered as customer on startup
    LaunchedEffect(Unit) {
        // Yahan hum server se check kar sakte hain agar IMEI registered hai
        // Filhal hum sirf UI ko ensure kar rahe hain
    }

    var showCustomerDialog by remember { mutableStateOf(false) }
    var tempImei by remember { mutableStateOf("") }

    if (viewModel.isLoggedIn) {
        onLoginSuccess()
    }

    if (showCustomerDialog) {
        AlertDialog(
            onDismissRequest = { showCustomerDialog = false },
            title = { Text("Setup Customer Device") },
            text = {
                Column {
                    Text("Enter the IMEI recorded in your dashboard for this device:", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempImei,
                        onValueChange = { tempImei = it },
                        label = { Text("Device IMEI") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Important: You must grant MUST permissions on the next screens for the locker to work.", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Red)
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
                            // MainActivity automatically will switch to CustomerStatusScreen which will handle permissions
                        }
                    },
                    enabled = tempImei.isNotBlank()
                ) {
                    Text("ACTIVATE AS CUSTOMER")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomerDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "PK Locker Admin",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Device Management System",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Shopkeeper Login", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = viewModel.email,
                    onValueChange = { viewModel.email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = viewModel.password,
                    onValueChange = { viewModel.password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.onLoginClick(context) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !viewModel.isLoading
                ) {
                    if (viewModel.isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    else Text("SIGN IN")
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
        
        HorizontalDivider(modifier = Modifier.padding(horizontal = 40.dp))
        
        Spacer(modifier = Modifier.height(24.dp))

        // Professional Customer Setup Button
        OutlinedButton(
            onClick = { showCustomerDialog = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE65100)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE65100))
        ) {
            Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("SET UP CUSTOMER DEVICE", fontWeight = FontWeight.Bold)
        }

        viewModel.errorMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
    }
}

