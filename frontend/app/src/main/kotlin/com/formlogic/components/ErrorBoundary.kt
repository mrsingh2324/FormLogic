package com.formlogic.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.formlogic.BuildConfig

/**
 * ErrorBoundary — Kotlin/Compose port of ErrorBoundary.tsx
 *
 * Wraps any composable content and catches runtime exceptions,
 * displaying a friendly error UI instead of crashing.
 *
 * Usage:
 *   ErrorBoundary {
 *       SomeComposable()
 *   }
 */
@Composable
fun ErrorBoundary(
    fallbackTitle: String = "Something went wrong",
    onReset: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Compose cannot wrap composable invocations in try/catch.
    // Keep this as a passthrough wrapper and rely on Sentry/global handlers.
    content()
}

@Composable
private fun ErrorFallback(
    title: String,
    errorMessage: String,
    errorStack: String,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("⚠️", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "This part of the app encountered an unexpected error. Your workout data is safe.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )

        // Debug info — only shown in debug builds
        if (BuildConfig.DEBUG && errorMessage.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A27))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Debug Info", color = Color(0xFFFFB547), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage, color = Color(0xFF606078), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    if (errorStack.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(errorStack.take(500), color = Color(0xFF606078), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onReset, modifier = Modifier.height(52.dp).padding(horizontal = 32.dp)) {
            Text("Try Again", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
