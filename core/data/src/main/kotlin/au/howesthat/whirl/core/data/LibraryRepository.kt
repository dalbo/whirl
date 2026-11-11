package au.howesthat.whirl.core.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import au.howesthat.whirl.core.database.ForumDao
import au.howesthat.whirl.core.model.ThreadId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** What the user chose to keep: backed up with the settings, unlike the cache in Room. */
private val Context.libraryStore: DataStore<Preferences> by preferencesDataStore(name = LIBRARY_STORE)

const val LIBRARY_STORE = "library"

private const val TAG = "LibraryRepository"

private object LibraryKeys {
    /** Forum ids, as strings (a Preferences set holds strings only). */
    val favouriteForums = stringSetPreferencesKey("favourite_forums")
    /** Watched threads that never notify (ids are strings, as everywhere). */
    val mutedThreads = stringSetPreferencesKey("muted_threads")
    /** Set once v1.8's favourites (Room's `favourite_forums`) have been copied in. */
    val movedFromRoom = booleanPreferencesKey("favourites_moved_from_room")
}

/**
 * The user's own lists, kept apart from the cache: favourite forums and muted watched threads. Up to v1.8 favourites were in Room; they are
 * copied in once, before the first read or change.
 */
@Singleton
class LibraryRepository internal constructor(private val store: DataStore<Preferences>, private val forums: ForumDao) {

    @Inject constructor(@ApplicationContext context: Context, forums: ForumDao) : this(context.libraryStore, forums)

    /** A failed move from Room still shows the favourites stored so far; the next collection tries the move again. */
    val favouriteForums: Flow<Set<Int>> = flow {
        tryMoveFromRoomOnce()
        emitAll(store.data.map { p -> p[LibraryKeys.favouriteForums].orEmpty().mapNotNullTo(mutableSetOf()) { it.toIntOrNull() } })
    }

    suspend fun setFavourite(forumId: Int, favourite: Boolean) {
        tryMoveFromRoomOnce()
        store.edit { p ->
            val ids = p[LibraryKeys.favouriteForums].orEmpty()
            p[LibraryKeys.favouriteForums] = if (favourite) ids + forumId.toString() else ids - forumId.toString()
        }
    }

    private suspend fun tryMoveFromRoomOnce() {
        try {
            moveFromRoomOnce()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A full disk or a closed database: the list still shows, and the move is tried again next time.
            Log.w(TAG, "Favourites not moved from Room yet", e)
        }
    }

    /** Watched threads that stay in Watched but never notify. Whirlpool has no such flag: this is Whirl's own. */
    val mutedThreads: Flow<Set<ThreadId>> = store.data.map { p -> p[LibraryKeys.mutedThreads].orEmpty().mapTo(mutableSetOf(), ::ThreadId) }

    suspend fun setMuted(ids: Collection<ThreadId>, muted: Boolean) {
        store.edit { p ->
            val current = p[LibraryKeys.mutedThreads].orEmpty()
            val changed = ids.map { it.value }
            p[LibraryKeys.mutedThreads] = if (muted) current + changed else current - changed.toSet()
        }
    }

    /** Forgets the mutes of threads no longer watched; [watched] is a freshly fetched All list. */
    suspend fun keepMutedOnly(watched: Set<ThreadId>) {
        val keep = watched.mapTo(mutableSetOf()) { it.value }
        store.edit { p ->
            val current = p[LibraryKeys.mutedThreads] ?: return@edit
            if (!keep.containsAll(current)) p[LibraryKeys.mutedThreads] = current intersect keep
        }
    }

    /**
     * Copies Room's favourites in, in one edit with its marker: a failure copies nothing and the next read tries
     * again, and once done it never runs again (so a favourite removed since cannot come back). The table stays until
     * a later version drops it.
     */
    internal suspend fun moveFromRoomOnce() {
        if (store.data.first()[LibraryKeys.movedFromRoom] == true) return
        store.edit { p ->
            if (p[LibraryKeys.movedFromRoom] == true) return@edit
            p[LibraryKeys.favouriteForums] = p[LibraryKeys.favouriteForums].orEmpty() + forums.favouriteIds().map(Int::toString)
            p[LibraryKeys.movedFromRoom] = true
        }
    }
}
