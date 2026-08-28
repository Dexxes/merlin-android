package dev.merlin.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Äquivalent zum App-Entry-Point in `MerlinApp.swift`. Implementiert
 * zusätzlich [Configuration.Provider], damit WorkManager Hilt-Worker (z. B.
 * [dev.merlin.android.data.MutationDrainWorker]) per [HiltWorkerFactory]
 * statt per Reflection instanziieren kann. Der WorkManager-Default-Init über
 * `androidx.startup` ist dafür in AndroidManifest.xml deaktiviert
 * (`tools:node="remove"` auf `WorkManagerInitializer`) – sonst gäbe es zwei
 * konkurrierende WorkManager-Initialisierungen.
 */
@HiltAndroidApp
class MerlinApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
