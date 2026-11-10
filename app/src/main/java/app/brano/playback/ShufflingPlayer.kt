package app.brano.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlin.random.Random

/**
 * The player the media session exposes. Shuffle physically reorders the queue (as Musicolet does),
 * and doing it here means every controller gets it: the app, headphones, the lockscreen, a car.
 * On: every other item moves after the current one in random order. Off: the order from before
 * shuffling comes back, with items added meanwhile at the end. The wrapped player uses an identity
 * shuffle order, so its flag only records the mode.
 *
 * [onNewQueue] fires when a controller replaces the whole queue (a new list started playing).
 */
@OptIn(UnstableApi::class)
internal class ShufflingPlayer(
    player: Player,
    private val random: Random = Random.Default,
    private val onNewQueue: () -> Unit = {},
) : ForwardingPlayer(player) {

    /** Media ids in the order before shuffle was turned on; null when off or unknown. */
    var unshuffledIds: List<String>? = null
        private set

    /** Puts back the saved mode and original order after a restart, without reordering anything. */
    fun restoreShuffle(on: Boolean, unshuffled: List<String>?) {
        wrappedPlayer.shuffleModeEnabled = on
        unshuffledIds = if (on) unshuffled else null
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        val p = wrappedPlayer
        if (shuffleModeEnabled == p.shuffleModeEnabled) return
        val cur = p.currentMediaItemIndex
        val items = List(p.mediaItemCount) { p.getMediaItemAt(it) }
        if (cur in items.indices) {
            if (shuffleModeEnabled) {
                shuffleAround(p, items, cur)
            } else {
                unshuffledIds?.let { original ->
                    val (ids, index) = QueueShuffle.restore(items.map { it.mediaId }, cur, original)
                    val byId = items.associateBy { it.mediaId }
                    rearrange(p, ids.map { byId.getValue(it) }, index)
                }
            }
        }
        if (!shuffleModeEnabled) unshuffledIds = null
        p.shuffleModeEnabled = shuffleModeEnabled
    }

    override fun setMediaItems(mediaItems: List<MediaItem>) {
        super.setMediaItems(mediaItems)
        afterNewQueue()
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        super.setMediaItems(mediaItems, resetPosition)
        afterNewQueue()
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        super.setMediaItems(mediaItems, startIndex, startPositionMs)
        afterNewQueue()
    }

    private var keepNextOrder: List<String>? = null

    /**
     * The next [setMediaItems] puts back a queue that is already in its final order (playback
     * resumption hands the restored queue back): keep it and the saved original order, once, and
     * only if it is that same list of [ids].
     */
    fun keepOrderOnce(ids: List<String>) {
        keepNextOrder = ids
    }

    /** A new list while shuffle is on plays shuffled from the chosen track. */
    private fun afterNewQueue() {
        onNewQueue()
        val keep = keepNextOrder
        keepNextOrder = null
        if (keep != null && List(wrappedPlayer.mediaItemCount) { wrappedPlayer.getMediaItemAt(it).mediaId } == keep) return
        val p = wrappedPlayer
        if (!p.shuffleModeEnabled) {
            unshuffledIds = null
            return
        }
        val cur = p.currentMediaItemIndex
        val items = List(p.mediaItemCount) { p.getMediaItemAt(it) }
        if (cur in items.indices) shuffleAround(p, items, cur)
    }

    private fun shuffleAround(p: Player, items: List<MediaItem>, cur: Int) {
        unshuffledIds = items.map { it.mediaId }
        rearrange(p, QueueShuffle.shuffledFrom(items, cur, random), 0)
    }

    /** Replaces everything around the current item so the queue becomes [order], with the current at [currentAt]. */
    private fun rearrange(p: Player, order: List<MediaItem>, currentAt: Int) {
        val cur = p.currentMediaItemIndex
        if (cur < 0 || currentAt !in order.indices) return
        if (cur + 1 < p.mediaItemCount) p.removeMediaItems(cur + 1, p.mediaItemCount)
        if (cur > 0) p.removeMediaItems(0, cur)
        if (currentAt > 0) p.addMediaItems(0, order.subList(0, currentAt))
        if (currentAt < order.lastIndex) p.addMediaItems(order.subList(currentAt + 1, order.size))
    }
}
