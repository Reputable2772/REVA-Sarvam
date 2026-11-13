package com.example.farmroutesaathi.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmroutesaathi.ui.theme.*

enum class SaathiVoiceState {
    IDLE,
    LISTENING,
    PROCESSING
}

@Composable
fun FloatingSaathiVoiceFab(
    isKannada: Boolean,
    voiceState: SaathiVoiceState,
    onVoiceFabClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = when (voiceState) {
            SaathiVoiceState.LISTENING -> 1.25f
            SaathiVoiceState.PROCESSING -> 1.15f
            SaathiVoiceState.IDLE -> 1.04f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(if (voiceState == SaathiVoiceState.LISTENING) 600 else 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Box(
        modifier = modifier.padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer Pulsing Glow
        if (voiceState == SaathiVoiceState.LISTENING || voiceState == SaathiVoiceState.PROCESSING) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .scale(pulseScale)
                    .background(
                        if (voiceState == SaathiVoiceState.LISTENING) SoilHarvestGold.copy(alpha = 0.40f) else SoilCropGreen.copy(alpha = 0.30f),
                        CircleShape
                    )
            )
        }

        // Main FAB Surface
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(56.dp)
                .scale(if (voiceState == SaathiVoiceState.IDLE) pulseScale else 1f)
                .clickable { onVoiceFabClick() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(listOf(SoilFreshLeaf, SoilCropGreen))),
                contentAlignment = Alignment.Center
            ) {
                when (voiceState) {
                    SaathiVoiceState.IDLE -> {
                        Text("🎙️", fontSize = 22.sp)
                    }
                    SaathiVoiceState.LISTENING -> {
                        Text("👂", fontSize = 22.sp)
                    }
                    SaathiVoiceState.PROCESSING -> {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
