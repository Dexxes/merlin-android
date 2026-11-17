package dev.merlin.android.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.ConfigurationCompat
import dev.merlin.android.R
import java.util.Locale

/**
 * Locale für Datums-/Zeit-/Zahlenformate, passend zur Sprache, in der die App ihre Texte
 * tatsächlich anzeigt.
 *
 * Die App gibt es auf Deutsch und Englisch; jede andere Gerätesprache fällt über den
 * Default-Ordner `res/values/` auf Englisch zurück. Formatierte man Datumsangaben dann mit der
 * Gerätesprache (z. B. Französisch), stünde ein französischer Monatsname mitten in einem
 * englischen Satz. Deshalb liefert jede Sprachdatei in `android.localeTag` ihr eigenes
 * Sprachkürzel (`en`/`de`): Spricht das Gerät dieselbe Sprache, bleibt dessen Locale samt Region
 * erhalten (en-GB → 17 Nov 2026, de-AT …), sonst wird die Sprache der angezeigten Texte genutzt.
 */
object AppLocale {
    fun of(context: Context): Locale {
        val uiLanguage = context.getString(R.string.android_localeTag)
        val device = ConfigurationCompat.getLocales(context.resources.configuration)[0]
        return if (device != null && device.language == uiLanguage) device else Locale.forLanguageTag(uiLanguage)
    }
}

/** [AppLocale.of] für Compose; liest bei einem Sprachwechsel automatisch neu. */
@Composable
@ReadOnlyComposable
fun appLocale(): Locale {
    LocalConfiguration.current
    return AppLocale.of(LocalContext.current)
}
