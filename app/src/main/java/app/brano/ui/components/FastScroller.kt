package app.brano.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.brano.domain.Normalizer
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** First letter, normalized and uppercase; anything that is not a letter becomes "#". */
fun indexLetter(text: String?): String {
    val c = text?.let(Normalizer::normalize)?.firstOrNull() ?: return "#"
    return if (c in 'a'..'z') c.uppercaseChar().toString() else "#"
}

/**
 * A thin draggable strip on the right edge. Dragging it scrolls [state]; the bubble shows the letter
 * of the item under the finger. [itemCount] counts the data items; [indexOffset] is the number of
 * leading non-data items in the list (headers). Fills its parent; only the strip takes touches.
 */
@Composable
fun FastScroller(
    state: LazyListState,
    itemCount: Int,
    indexOffset: Int,
    letterAt: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    if (itemCount < 2) return
    val scope = rememberCoroutineScope()
    var heightPx by remember { mutableIntStateOf(1) }
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    val thumbHeightPx = with(LocalDensity.current) { 48.dp.roundToPx() }

    Box(modifier.fillMaxSize()) {
        val dragging = dragIndex
        val fraction = if (dragging != null) {
            dragging / (itemCount - 1).toFloat()
        } else {
            ((state.firstVisibleItemIndex - indexOffset).coerceAtLeast(0) / (itemCount - 1).toFloat()).coerceIn(0f, 1f)
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(28.dp)
                .onSizeChanged { heightPx = it.height }
                .pointerInput(itemCount, indexOffset) {
                    fun scrollTo(y: Float) {
                        val f = (y / heightPx).coerceIn(0f, 1f)
                        val idx = (f * (itemCount - 1)).roundToInt()
                        dragIndex = idx
                        scope.launch { state.scrollToItem(idx + indexOffset) }
                    }
                    detectVerticalDragGestures(
                        onDragStart = { scrollTo(it.y) },
                        onDragEnd = { dragIndex = null },
                        onDragCancel = { dragIndex = null },
                        onVerticalDrag = { change, _ -> scrollTo(change.position.y) },
                    )
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, (fraction * (heightPx - thumbHeightPx).coerceAtLeast(0)).roundToInt()) }
                    .padding(end = 2.dp)
                    .size(width = 4.dp, height = 48.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (dragging != null) 0.9f else 0.45f),
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
        if (dragging != null) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    letterAt(dragging),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
