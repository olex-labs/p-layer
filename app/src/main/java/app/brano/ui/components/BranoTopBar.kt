package app.brano.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Modifier
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Top bar in the "Espressivo" style: top-level screens ([large]) get a very large heavy title,
 * detail screens a heavy regular-size one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranoTopBar(
    large: Boolean,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val style = if (large) {
        MaterialTheme.typography.headlineLarge
    } else {
        MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
    }
    val glow = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    TopAppBar(
        title = { ProvideTextStyle(style, title) },
        navigationIcon = navigationIcon,
        actions = actions,
        expandedHeight = if (large) 80.dp else TopAppBarDefaults.TopAppBarExpandedHeight,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        modifier = if (large) {
            // Static gradient, drawn once per frame of the bar only: no blur, no shader.
            Modifier.drawBehind { drawRect(Brush.verticalGradient(listOf(glow, Color.Transparent))) }
        } else {
            Modifier
        },
    )
}
