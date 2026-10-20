package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.network.WhirlpoolApi
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val api: WhirlpoolApi,
    private val settings: SettingsRepository,
    private val db: WhirlDatabase,
) {
    val account = settings.account

    /**
     * Checks [key] with one `get=user` call and stores it with its account. Throws `WhirlpoolError.BadKey` for a
     * rejected key; nothing is stored then. Switching accounts clears the previous account's cached lists.
     */
    suspend fun signIn(key: String): Account {
        val trimmed = key.trim()
        val user = api.user(trimmed)
        val account = Account(user.id, user.name)
        val previous = settings.account.first()
        if (previous != null && previous.userId != account.userId) db.clearAccountData()
        settings.signIn(trimmed, account)
        return account
    }

    suspend fun signOut() {
        settings.signOut()
        db.clearAccountData()
    }
}
