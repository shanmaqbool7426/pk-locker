package com.pksafe.lock.manager.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    onBackToLogin: () -> Unit,
    viewModel: SignupViewModel = viewModel()
) {
    val scrollState = rememberScrollState()

    if (viewModel.isSignupSuccess) {
        AlertDialog(
            onDismissRequest = { viewModel.isSignupSuccess = false; onBackToLogin() },
            title = { Text("Success", fontWeight = FontWeight.Bold) },
            text = { Text(viewModel.message ?: "Account created successfully") },
            confirmButton = {
                Button(onClick = { viewModel.isSignupSuccess = false; onBackToLogin() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))) {
                    Text("Go to Login", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Subtle Background Design Element
        Box(
            modifier = Modifier
                .size(400.dp)
                .offset(y = (-200).dp, x = 100.dp)
                .background(Brush.radialGradient(listOf(Color(0xFF2563EB).copy(0.08f), Color.Transparent)))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            Text(
                text = "PK LOCKER",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF0F172A),
                letterSpacing = 2.sp
            )
            Text(
                text = "SHOPKEEPER REGISTRATION",
                fontSize = 11.sp,
                color = Color(0xFF2563EB),
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // --- FREE KEYS BANNER ---
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Sign Up Bonus!", fontWeight = FontWeight.ExtraBold, color = Color(0xFFB45309), fontSize = 16.sp)
                                Text("Get 5 FREE keys instantly.", color = Color(0xFF92400E), fontSize = 13.sp)
                            }
                        }
                    }

                    LoginInput(
                        value = viewModel.name,
                        onValueChange = { viewModel.name = it },
                        label = "FULL NAME",
                        icon = Icons.Default.Person
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LoginInput(
                        value = viewModel.shopName,
                        onValueChange = { viewModel.shopName = it },
                        label = "SHOP NAME",
                        icon = Icons.Default.Storefront
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LoginInput(
                        value = viewModel.email,
                        onValueChange = { viewModel.email = it },
                        label = "EMAIL ADDRESS",
                        icon = Icons.Default.Email,
                        keyboardType = KeyboardType.Email
                    )

                    Spacer(modifier = Modifier.height(16.dp))

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
                        icon = Icons.Default.Lock,
                        isPassword = true
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    LoginInput(
                        value = viewModel.referredByPhone,
                        onValueChange = { viewModel.referredByPhone = it },
                        label = "REFERRER'S PHONE (OPTIONAL)",
                        icon = Icons.Default.GroupAdd,
                        keyboardType = KeyboardType.Phone
                    )
                    
                    Text("Enter the phone number of the person who referred you.", fontSize = 10.sp, color = Color(0xFF64748B), modifier = Modifier.padding(top = 4.dp).align(Alignment.Start))

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { viewModel.onSignupClick() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        enabled = !viewModel.isLoading,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        if (viewModel.isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                        } else {
                            Text("CREATE ACCOUNT", fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                    }

                    viewModel.message?.let {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = it, 
                            color = if (viewModel.isSignupSuccess) Color(0xFF059669) else Color(0xFFDC2626),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // BACK TO LOGIN SECTION
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Already have an account?", fontSize = 13.sp, color = Color(0xFF64748B))
                TextButton(onClick = onBackToLogin) {
                    Text("Sign In", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
