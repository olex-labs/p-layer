package app.brano.data.db

import app.brano.data.library.Track
import app.brano.data.library.TrackRef

data class ResolvedItem(val ref: TrackRef, val track: Track?)

object TrackResolver {
    /** Returns resolved items plus path -> new MediaStore id for refs found only by path. */
    fun resolve(
        refs: List<TrackRef>,
        byId: (Long) -> Track?,
        byPath: (String) -> Track?,
    ): Pair<List<ResolvedItem>, Map<String, Long>> {
        val changes = LinkedHashMap<String, Long>()
        val items = refs.map { ref ->
            val byIdTrack = byId(ref.mediaStoreId)
            if (byIdTrack != null) {
                ResolvedItem(ref, byIdTrack)
            } else {
                val byPathTrack = byPath(ref.path)
                if (byPathTrack != null) changes[ref.path] = byPathTrack.id
                ResolvedItem(ref, byPathTrack)
            }
        }
        return items to changes
    }
}
