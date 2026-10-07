package com.pksafe.lock.manager.ui.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pksafe.lock.manager.data.AppUpdateConfig
import com.pksafe.lock.manager.ui.theme.BrandBlue
import com.pksafe.lock.manager.ui.theme.BrandBlueDark
import com.pksafe.lock.manager.ui.theme.CardWhite
import com.pksafe.lock.manager.ui.theme.TextMuted
import com.pksafe.lock.manager.ui.theme.TextTitle

@Composable
fun AppUpdateDialog(
    config: AppUpdateConfig,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = {
            if (!config.isForceUpdate) {
                onDismiss()
            }
        },
        icon = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        Brush.linearGradient(listOf(BrandBlue, BrandBlueDark)),
                        RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SystemUpdate, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        },
        title = {
            Text(
                text = config.updateTitle.ifBlank { "New Version Available" },
                fontWeight = FontWeight.Black,
                color = TextTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "v${config.versionName}",
                    fontWeight = FontWeight.Bold,
                    color = BrandBlue,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = config.updateMessage.ifBlank { "A new version of PK Locker is available. Please update to continue using all features." },
                    color = TextMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(config.updateUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback if browser not found
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
            ) {
                Text("UPDATE NOW", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        },
        dismissButton = {
            if (!config.isForceUpdate) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Later", color = TextMuted, fontWeight = FontWeight.Bold)
                }
            }
        },
        containerColor = CardWhite,
        shape = RoundedCornerShape(28.dp)
    )
}
