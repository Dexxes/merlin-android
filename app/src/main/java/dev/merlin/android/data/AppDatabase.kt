package dev.merlin.android.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room-Datenbank für Abschnitt 4 (Persistenz/Offline-First). Bündelt
 * Artikel-Cache, Bild-Cache-Index und Offline-Mutation-Queue in einer Datei,
 * analog zu den separaten aber gemeinsam im App-Sandbox liegenden
 * `article-cache.json`/`img-index.json`/`merlin_pending_mutations_v1` auf iOS.
 */
@Database(
    entities = [
        ArticleEntity::class,
        ImageCacheIndexEntity::class,
        PendingMutationEntity::class,
        ReminderEntity::class,
        HighlightEntity::class,
        PendingHighlightMutationEntity::class,
    ],
    // v3 → v4: ArticleEntity.cachedAt ergänzt (Offline-Retention-Einstellung),
    // destruktiver Fallback wie bei v1 → v2 – reiner Cache, verlustfrei neu befüllbar.
    // v4 → v5: PendingMutationEntity.tagIdsJson (volles Ziel-Tag-Set) durch
    // addTagIdsJson/removeTagIdsJson (Delta) ersetzt, um einen Tag-Overwrite-Bug
    // zu fixen (siehe Doc-Kommentar dort). Destruktiver Fallback: verlorene
    // Zeilen sind höchstens einzelne noch nicht synchronisierte Offline-Edits.
    version = 5,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun imageCacheIndexDao(): ImageCacheIndexDao
    abstract fun mutationDao(): MutationDao
    abstract fun reminderDao(): ReminderDao
    abstract fun highlightDao(): HighlightDao
    abstract fun pendingHighlightMutationDao(): PendingHighlightMutationDao
}
