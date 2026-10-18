package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val NexoBrandGradient = Brush.linearGradient(listOf(Color(0xFFFF4D6D), Color(0xFFC9184A)))

// Nexo wordmark drawn as text, replacing the per-icon raster wordmarks inherited from upstream.
@Composable
internal fun AppBrandWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    @Suppress("UNUSED_PARAMETER") icon: AppIconOption? = null,
) {
    Box(
        modifier = modifier.then(
            if (contentDescription != null) {
                Modifier.semantics { this.contentDescription = contentDescription }
            } else {
                Modifier
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "nexo",
            style = TextStyle(
                brush = NexoBrandGradient,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.04).em,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 96.sp),
        )
    }
}
