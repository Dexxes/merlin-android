package dev.merlin.android.data

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "merlin.db")
            // Pre-Release, keine produktiven Installationen mit altem Schema –
            // destruktiver Fallback statt Migrationscode für v1 → v2 (Reminders).
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideArticleDao(db: AppDatabase): ArticleDao = db.articleDao()

    @Provides
    fun provideImageCacheIndexDao(db: AppDatabase): ImageCacheIndexDao = db.imageCacheIndexDao()

    @Provides
    fun provideMutationDao(db: AppDatabase): MutationDao = db.mutationDao()

    @Provides
    fun provideReminderDao(db: AppDatabase): ReminderDao = db.reminderDao()

    @Provides
    fun provideHighlightDao(db: AppDatabase): HighlightDao = db.highlightDao()

    @Provides
    fun providePendingHighlightMutationDao(db: AppDatabase): PendingHighlightMutationDao = db.pendingHighlightMutationDao()
}
