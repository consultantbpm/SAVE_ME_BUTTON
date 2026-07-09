package com.savemebutton.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.savemebutton.shared.SosState

private val SaveMeRed = Color(0xFFFF1744)

@Composable
fun SosScreen(viewModel: SosViewModel, onTap: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bg = if (state is SosState.Countdown) Color(0xFFB00020) else Color.Black
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            },
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state) {
            is SosState.Idle -> IdleView(holdSeconds = viewModel.configHoldSeconds())
            is SosState.Countdown -> CountdownView(s.secondsRemaining, viewModel.configCancelTaps())
            is SosState.Canceled -> CenterText("Canceled", color = Color(0xFF4CAF50))
            is SosState.AcquiringLocation -> CenterText("Locating…")
            is SosState.SendingSms -> CenterText("SMS → ${s.contactName}")
            is SosState.Calling -> CenterText("Calling ${s.contactName}")
            is SosState.CallActive -> CallActiveView(
                contactName = s.contactName,
                showSkip = viewModel.voicemailTrapEscapeEnabled(),
                onSkip = { viewModel.requestSkip() },
            )
            is SosState.Stopped -> CenterText("Stopped", color = Color(0xFFFFB300))
            is SosState.Done -> DoneView(s)
        }
    }
}

@Composable
private fun IdleView(holdSeconds: Int) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(8.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "SAVE ME",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    color = SaveMeRed,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "HOLD MIN $holdSeconds SECONDS",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CountdownView(seconds: Int, cancelTaps: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize().padding(6.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("$seconds", fontSize = 78.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text(
                    text = "TAP $cancelTaps TIMES TO ABORT",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CenterText(text: String, color: Color = Color.White) {
    Text(
        text = text,
        color = color,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(12.dp),
    )
}

@Composable
private fun CallActiveView(contactName: String, showSkip: Boolean, onSkip: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Connected",
            color = Color(0xFF4CAF50),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = contactName,
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (showSkip) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSkip,
                colors = ButtonDefaults.primaryButtonColors(
                    backgroundColor = Color(0xFFFF1744),
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("SKIP", fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun DoneView(s: SosState.Done) {
    val msg = when {
        s.answered -> "Help reached"
        s.reachedIndex < 0 -> "No contacts configured"
        else -> "Tried ${s.reachedIndex + 1} contact(s)"
    }
    CenterText(msg, color = if (s.answered) Color(0xFF4CAF50) else Color(0xFFFFB300))
}

@Composable
private fun previewTheme() = MaterialTheme.colors
