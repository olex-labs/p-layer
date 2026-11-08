package app.brano.data.library

import kotlinx.coroutines.flow.Flow

interface TrackSource {
    suspend fun loadAll(): List<Track>
    fun changes(): Flow<Unit>
}
