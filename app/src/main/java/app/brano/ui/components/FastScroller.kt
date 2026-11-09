package app.brano.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** First letter, normalized and uppercase; anything that is not a letter becomes "#". */
fun indexLetter(text: String?): String {
    val c = text?.let(Normalizer::normalize)?.firstOrNull() ?: return "#"
    return if (c in 'a'..'z') c.uppercaseChar().toString() else "#"
}

/** List item for a thumb whose top is at [thumbTop] within a travel of [range] px. */
internal fun fastScrollIndex(thumbTop: Float, range: Float, itemCount: Int): Int {
    if (range <= 0f || itemCount < 1) return 0
    return ((thumbTop / range).coerceIn(0f, 1f) * (itemCount - 1)).roundToInt()
}

/** Thumb position (0..1) for a list whose first visible item is [firstVisible]. */
internal fun fastScrollFraction(firstVisible: Int, indexOffset: Int, itemCount: Int): Float =
    if (itemCount < 2) 0f
    else ((firstVisible - indexOffset).coerceAtLeast(0) / (itemCount - 1).toFloat()).coerceIn(0f, 1f)

/**
 * A fast-scroll thumb on the right edge, shown while the list scrolls (and briefly after) or while
 * dragged. Only the thumb itself (48 dp square) takes touches, so row actions under the right edge
 * stay tappable. Dragging it scrolls [state]; the bubble shows the letter of the item under it.
 * [itemCount] counts the data items; [indexOffset] is the number of leading non-data items.
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
    var dragTop by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }
    val thumbHeightPx = with(LocalDensity.current) { THUMB_SIZE.toPx() }
    val active = state.isScrollInProgress || dragIndex != null
    LaunchedEffect(active) {
        if (active) {
            visible = true
        } else {
            delay(HIDE_DELAY_MS)
            visible = false
        }
    }

    Box(modifier.fillMaxSize().onSizeChanged { heightPx = it.height }) {
        if (!visible) return@Box
        val range = (heightPx - thumbHeightPx).coerceAtLeast(0f)
        val dragging = dragIndex
        val top = if (dragging != null) {
            dragTop
        } else {
            fastScrollFraction(state.firstVisibleItemIndex, indexOffset, itemCount) * range
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, top.roundToInt()) }
                .size(THUMB_SIZE)
                .pointerInput(itemCount, indexOffset) {
                    fun moveTo(y: Float) {
                        val r = (heightPx - thumbHeightPx).coerceAtLeast(0f)
                        dragTop = y.coerceIn(0f, r)
                        val idx = fastScrollIndex(dragTop, r, itemCount)
                        dragIndex = idx
                        scope.launch { state.scrollToItem(idx + indexOffset) }
                    }
                    detectVerticalDragGestures(
                        onDragStart = {
                            val r = (heightPx - thumbHeightPx).coerceAtLeast(0f)
                            moveTo(fastScrollFraction(state.firstVisibleItemIndex, indexOffset, itemCount) * r)
                        },
                        onDragEnd = { dragIndex = null },
                        onDragCancel = { dragIndex = null },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            moveTo(dragTop + dy)
                        },
                    )
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
                    .size(width = 4.dp, height = THUMB_SIZE)
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

private val THUMB_SIZE = 48.dp
private const val HIDE_DELAY_MS = 1_500L
