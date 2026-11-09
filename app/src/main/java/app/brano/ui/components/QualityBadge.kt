package app.brano.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.brano.ui.theme.PillShape

/** Small outlined pill with the file quality ("FLAC", "MP3 · 320 kbps"). */
@Composable
fun QualityBadge(text: String, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current.copy(alpha = 0.8f)
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
        color = color,
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.5f), PillShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
