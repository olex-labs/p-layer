package app.brano.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Top bar in the "Espressivo" style. Top-level screens ([large]) get a big heavy title over a soft
 * album glow; with a [scrollBehavior] it collapses into a normal bar while the content scrolls.
 * Detail screens get a heavy regular-size title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranoTopBar(
    large: Boolean,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val transparent = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
    )
    if (large) {
        val glow = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        // Expanded title uses headlineMedium (Black in BranoTypography), collapsed titleLarge.
        LargeTopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            expandedHeight = BranoTopBarDefaults.LargeExpandedHeight,
            colors = transparent,
            scrollBehavior = scrollBehavior,
            // Static gradient: no blur, no shader.
            modifier = Modifier.drawBehind { drawRect(Brush.verticalGradient(listOf(glow, Color.Transparent))) },
        )
    } else {
        TopAppBar(
            title = { ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), title) },
            navigationIcon = navigationIcon,
            actions = actions,
            colors = transparent,
        )
    }
}

object BranoTopBarDefaults {
    /** Height of the big title bar when fully expanded. */
    val LargeExpandedHeight = 104.dp
}
