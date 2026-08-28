package dev.merlin.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Markiert den prozessweiten, App-überlebenden [CoroutineScope].
 *
 * **Warum nötig:** Das Speichern + der Server-Push der Scroll-Position werden
 * beim Verlassen des Readers (`onDispose`) angestoßen. Würden sie im
 * `viewModelScope` laufen, bräche das fast zeitgleiche `onCleared()` des
 * `ArticleReaderViewModel` den noch laufenden, asynchronen DataStore-Write und
 * den Netzwerk-Request ab – die Position ginge verloren. (Auf iOS kein Thema,
 * da `UserDefaults`-Writes synchron sind.) Ein an die Application gebundener
 * Scope überlebt das Schließen des Screens und stellt den Abschluss sicher.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        // SupervisorJob: ein fehlgeschlagener Push darf andere Saves nicht abbrechen.
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
