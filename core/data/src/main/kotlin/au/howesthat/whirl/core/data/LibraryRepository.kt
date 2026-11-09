package au.howesthat.whirl.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import au.howesthat.whirl.core.database.ForumDao
import dagger.hilt.android.qualifiers.ApplicationContext
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

private object LibraryKeys {
    /** Forum ids, as strings (a Preferences set holds strings only). */
    val favouriteForums = stringSetPreferencesKey("favourite_forums")
    /** Set once v1.8's favourites (Room's `favourite_forums`) have been copied in. */
    val movedFromRoom = booleanPreferencesKey("favourites_moved_from_room")
}

/**
 * The user's own lists, kept apart from the cache: favourite forums. Up to v1.8 favourites were in Room; they are
 * copied in once, before the first read or change.
 */
@Singleton
class LibraryRepository internal constructor(private val store: DataStore<Preferences>, private val forums: ForumDao) {

    @Inject constructor(@ApplicationContext context: Context, forums: ForumDao) : this(context.libraryStore, forums)

    val favouriteForums: Flow<Set<Int>> = flow {
        moveFromRoomOnce()
        emitAll(store.data.map { p -> p[LibraryKeys.favouriteForums].orEmpty().mapNotNullTo(mutableSetOf()) { it.toIntOrNull() } })
    }

    suspend fun setFavourite(forumId: Int, favourite: Boolean) {
        moveFromRoomOnce()
        store.edit { p ->
            val ids = p[LibraryKeys.favouriteForums].orEmpty()
            p[LibraryKeys.favouriteForums] = if (favourite) ids + forumId.toString() else ids - forumId.toString()
        }
    }

    /**
     * Copies Room's favourites in, in one edit with its marker: a failure copies nothing and the next read tries
     * again, and once done it never runs again (so a favourite removed since cannot come back). The table stays until
     * a later version drops it.
     */
    private suspend fun moveFromRoomOnce() {
        if (store.data.first()[LibraryKeys.movedFromRoom] == true) return
        store.edit { p ->
            if (p[LibraryKeys.movedFromRoom] == true) return@edit
            p[LibraryKeys.favouriteForums] = p[LibraryKeys.favouriteForums].orEmpty() + forums.favouriteIds().map(Int::toString)
            p[LibraryKeys.movedFromRoom] = true
        }
    }
}
