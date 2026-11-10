package app.brano.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import app.brano.ui.components.albumArtUri
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.RoundedCornersTransformation

/**
 * Album art for the widget: 144 px (small enough for the RemoteViews IPC limit), software bitmap,
 * corners at 10% like the app's covers. Null when the album has no art.
 */
suspend fun loadWidgetCover(context: Context, albumId: Long): Bitmap? {
    val request = ImageRequest.Builder(context)
        .data(albumArtUri(albumId))
        .size(SIZE_PX)
        .allowHardware(false)
        .transformations(RoundedCornersTransformation(SIZE_PX * 0.1f))
        .build()
    val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
    return result.drawable.toBitmap(SIZE_PX, SIZE_PX)
}

private const val SIZE_PX = 144
