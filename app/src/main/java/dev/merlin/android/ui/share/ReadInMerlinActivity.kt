package dev.merlin.android.ui.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import dev.merlin.android.MainActivity
import dev.merlin.android.R
import dev.merlin.android.share.ReadInMerlinLink

/**
 * „Jetzt in Merlin lesen“: eigener Eintrag im Teilen-Menü (`ACTION_SEND`, z. B. nach langem
 * Drücken auf einen Link → „Link teilen“) und im Menü einer Textauswahl (`ACTION_PROCESS_TEXT`).
 * Äquivalent zur iOS-Action-Extension `MerlinRead`.
 *
 * Ohne eigene Oberfläche: holt den Link aus dem Intent, reicht ihn an [MainActivity] weiter
 * ([MainActivity.EXTRA_READ_URL]) und schließt sich sofort. Die App speichert ihn dort wie einen
 * geteilten Link (oder findet ihn in der Liste) und öffnet den Artikel direkt im Reader.
 */
class ReadInMerlinActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = ReadInMerlinLink.firstWebUrl(
            intent.getStringExtra(Intent.EXTRA_TEXT),
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString(),
            intent.getStringExtra(Intent.EXTRA_SUBJECT),
            intent.dataString,
        )
        if (url == null) {
            Toast.makeText(this, R.string.readInMerlin_errorNoUrl, Toast.LENGTH_LONG).show()
        } else {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_READ_URL, url)
                    // Eigene Aufgabe statt der der teilenden App; eine laufende Merlin-Instanz
                    // bekommt den Link per onNewIntent (launchMode="singleTop").
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
        finish()
    }
}
