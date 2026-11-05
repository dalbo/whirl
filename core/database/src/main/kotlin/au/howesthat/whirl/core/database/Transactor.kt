package au.howesthat.whirl.core.database

import androidx.room.withTransaction
import javax.inject.Inject

/**
 * Runs several writes as one database transaction, so observers never see the state between them. An interface so
 * tests can watch what runs inside one.
 */
interface Transactor {
    suspend fun <R> run(block: suspend () -> R): R
}

class RoomTransactor @Inject constructor(private val db: WhirlDatabase) : Transactor {
    override suspend fun <R> run(block: suspend () -> R): R = db.withTransaction(block)
}
