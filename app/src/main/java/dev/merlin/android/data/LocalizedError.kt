package dev.merlin.android.data

import android.content.Context
import androidx.annotation.StringRes

/**
 * Fehler mit einer für Nutzer bestimmten Meldung als String-Ressource. Die Klassen, die so einen
 * Fehler werfen (Interceptor, Services), haben oft keinen passenden Context oder laufen, bevor
 * die Sprache feststeht; aufgelöst wird deshalb erst in der Oberfläche über [userMessage].
 * `Exception.message` bleibt ein englischer Text für Logs.
 */
interface LocalizedError {
    @get:StringRes
    val messageRes: Int
    val messageArgs: Array<out Any>
        get() = emptyArray()
}

/** Meldung für die Oberfläche: übersetzt bei [LocalizedError], sonst [Throwable.message]. */
fun Throwable.userMessage(context: Context): String? =
    if (this is LocalizedError) context.getString(messageRes, *messageArgs) else message
