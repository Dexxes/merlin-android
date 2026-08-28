package dev.merlin.android.data

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Äquivalent zu `UIImpactFeedbackGenerator+Haptics.swift`. iOS triggert Haptik direkt
 * über ein System-API ohne View-Bezug (`UIImpactFeedbackGenerator`); das Android-Äquivalent
 * dafür ist der [Vibrator]-Systemdienst (statt z.B. `View.performHapticFeedback`, das einen
 * Compose-`View`-Kontext bräuchte und damit aus dem ViewModel nicht aufrufbar wäre).
 * `EFFECT_TICK`/`EFFECT_CLICK`/`EFFECT_HEAVY_CLICK` (API 29+) bilden light/medium/heavy-Tap
 * am genauesten ab; für API 26–28 fällt das auf `createOneShot` mit steigender Dauer/Amplitude zurück.
 * Für minSdk 23–25 (vor `VibrationEffect`, API 26) gibt es eine weitere Stufe auf das alte
 * `Vibrator.vibrate(long)` ohne Amplitudensteuerung (Geräte dort unterstützen i.d.R. ohnehin
 * keine Amplitude).
 */
@Singleton
class HapticUtil @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val vibrator: Vibrator
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    /** Leichte Vibration für Bestätigungen (z.B. Favorit) – Äquivalent `HapticFeedback.lightTap()`. */
    fun lightTap() = vibrate(effect = VibrationEffect.EFFECT_TICK, fallbackMs = 15L, fallbackAmplitude = 80)

    /** Mittlere Vibration für Standard-Aktionen (z.B. Archivieren) – Äquivalent `HapticFeedback.mediumTap()`. */
    fun mediumTap() = vibrate(effect = VibrationEffect.EFFECT_CLICK, fallbackMs = 25L, fallbackAmplitude = 150)

    /** Schwere Vibration für destruktive Aktionen (z.B. Löschen) – Äquivalent `HapticFeedback.heavyTap()`. */
    fun heavyTap() = vibrate(effect = VibrationEffect.EFFECT_HEAVY_CLICK, fallbackMs = 40L, fallbackAmplitude = 255)

    private fun vibrate(effect: Int, fallbackMs: Long, fallbackAmplitude: Int) {
        // Lautlos/Nicht-Stören-Modus o.ä. kann den Vibrator-Zugriff werfen lassen –
        // Haptik ist rein kosmetisch, ein Fehler hier darf die eigentliche Mutation nie stören.
        runCatching {
            val v = vibrator
            if (!v.hasVibrator()) return
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    v.vibrate(VibrationEffect.createPredefined(effect))
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                    v.vibrate(VibrationEffect.createOneShot(fallbackMs, fallbackAmplitude))
                else -> {
                    // Vor API 26 existiert VibrationEffect nicht – einzige Option ist das alte,
                    // amplitudenlose vibrate(long) (deprecated, aber bis minSdk 23 die einzige API).
                    @Suppress("DEPRECATION")
                    v.vibrate(fallbackMs)
                }
            }
        }
    }
}
