package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.network.WhirlpoolApi
import au.howesthat.whirl.core.network.WhirlpoolHttp
import au.howesthat.whirl.core.network.WhirlpoolWeb
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient
import java.time.Clock
import javax.inject.Singleton

/** Provided by the app module. */
data class AppInfo(val versionName: String) {
    val userAgent: String get() = "Whirl/$versionName (Android; +https://github.com/dalbo/whirl)"
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun okHttp(appInfo: AppInfo): OkHttpClient = WhirlpoolHttp.client(appInfo.userAgent)

    @Provides
    @Singleton
    fun api(client: OkHttpClient, settings: SettingsRepository, @IoDispatcher io: CoroutineDispatcher): WhirlpoolApi =
        WhirlpoolApi(client, settings::apiKey, io = io)

    @Provides
    @Singleton
    fun web(client: OkHttpClient, @IoDispatcher io: CoroutineDispatcher): WhirlpoolWeb = WhirlpoolWeb(client, io = io)

    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()
}
