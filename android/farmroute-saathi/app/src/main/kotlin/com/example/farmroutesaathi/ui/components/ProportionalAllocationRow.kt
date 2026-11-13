package com.example.farmroutesaathi.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmroutesaathi.ui.theme.FreshLeafGreen

@Composable
fun ProportionalAllocationRow(
    farmerName: String,
    declaredKg: Double,
    allocatedKg: Double,
    poolTotalKg: Double,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    val ratio = (allocatedKg / declaredKg).toFloat().coerceIn(0f, 1f)

    LaunchedEffect(allocatedKg) {
        progress.animateTo(
            targetValue = ratio,
            animationSpec = tween(durationMillis = 600)
        )
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = farmerName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${"%.1f".format(allocatedKg)} kg / ${"%.0f".format(declaredKg)} kg",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.value)
                    .clip(RoundedCornerShape(3.dp))
                    .background(FreshLeafGreen)
            )
        }
    }
}
