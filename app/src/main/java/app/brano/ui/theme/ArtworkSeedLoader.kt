package app.brano.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import app.brano.ui.components.albumArtUri
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Extracts the theme seed color from album art, cached per albumId. */
@Singleton
class ArtworkSeedLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val cache = ConcurrentHashMap<Long, Optional>()

    private class Optional(val color: Color?)

    suspend fun seedFor(albumId: Long): Color? {
        cache[albumId]?.let { return it.color }
        val color = withContext(Dispatchers.Default) {
            val request = ImageRequest.Builder(context)
                .data(albumArtUri(albumId))
                .allowHardware(false)
                .size(128)
                .build()
            val result = context.imageLoader.execute(request) as? SuccessResult ?: return@withContext null
            seedFromPalette(Palette.from(result.drawable.toBitmap()).generate())
        }
        cache[albumId] = Optional(color)
        return color
    }
}
