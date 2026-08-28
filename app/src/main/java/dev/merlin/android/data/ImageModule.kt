package dev.merlin.android.data

import android.content.Context
import coil.ImageLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Hilt-Modul für den Coil-`ImageLoader`, der die UI (Abschnitt 9) UND
 * [ImageCacheService] (Vorwärmen/Eviction) gemeinsam nutzen, damit beide auf
 * denselben Disk-Cache zugreifen. Eigener `OkHttpClient` statt des
 * Retrofit-Clients aus `NetworkModule`, weil Bild-Requests weder
 * Basic-Auth- noch Base-URL-Rewriting brauchen, aber den [RefererInterceptor]
 * brauchen, den Artikel-API-Requests nicht brauchen.
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageModule {

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor(RefererInterceptor())
                    .build()
            }
            .build()
}
