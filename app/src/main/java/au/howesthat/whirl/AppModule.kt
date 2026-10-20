package au.howesthat.whirl

import au.howesthat.whirl.core.data.AppInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun appInfo(): AppInfo = AppInfo(BuildConfig.VERSION_NAME)
}
