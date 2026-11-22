package dev.merlin.android.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import dev.merlin.android.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Hilt-Modul für den API-Layer. Retrofit braucht eine `baseUrl` zur
 * Konstruktionszeit, die echte Nextcloud-URL ist aber erst nach Login/Onboarding
 * bekannt – deshalb bleibt [PLACEHOLDER_BASE_URL] ein nie tatsächlich angefragter
 * Platzhalter, und [BaseUrlInterceptor] schreibt jede Anfrage auf die in
 * [CredentialsStore] hinterlegte Server-URL um.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    const val PLACEHOLDER_BASE_URL = "https://example.invalid/index.php/apps/merlin/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideCredentialsProvider(impl: CredentialsStore): CredentialsProvider = impl

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        baseUrlInterceptor: BaseUrlInterceptor,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(authInterceptor)
        // Request-Zeilen (Methode + URL) nur im Debug-Build ins Logcat, im Release nicht:
        // URLs können Server-Adresse, Artikel-Links und Login-Flow-Tokens enthalten.
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideMerlinApi(retrofit: Retrofit): MerlinApi = retrofit.create(MerlinApi::class.java)
}
