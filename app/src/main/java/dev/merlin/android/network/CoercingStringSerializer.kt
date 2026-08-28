package dev.merlin.android.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Dekodiert ein beliebiges JSON-Primitive (String, Number, Boolean, null) in einen
 * String – Kotlin-Pendant zu `SettingValue` in `MerlinAPI.swift`.
 *
 * Hintergrund: `GET /api/settings` liefert seit `SettingsController::castForResponse()`
 * bewusst gemischte Typen – `fontSize` als Number, `lineHeight` als Float,
 * `saveProgress`/`resumeOnOpen` als Boolean, den Rest als String. Die
 * [Settings]-Felder sind aber durchgehend `String`, weil [PreferencesStore]
 * sie so parst (`toIntOrNull()`, `== "1"`).
 *
 * Ohne diesen Serializer hängt das Decoding allein an `isLenient = true` in
 * [NetworkModule] – nur dieses Flag lässt kotlinx.serialization unquoted Literale
 * in String-Felder schreiben. Wird das Flag je entfernt oder zurückgesetzt,
 * scheitert der gesamte Settings-Abruf schlagartig mit einem Typfehler und damit
 * auch die Report-Funktion, die die Backend-URL daraus liest (siehe ReportService).
 * Der explizite Serializer macht die Konvertierung unabhängig von der Json-Config.
 */
object CoercingStringSerializer : KSerializer<String> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CoercedString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        // Nicht-JSON-Decoder (theoretisch, z.B. in Tests) bekommen das Standardverhalten.
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        val primitive = jsonDecoder.decodeJsonElement().jsonPrimitive

        if (primitive is JsonNull) return ""

        // Booleans auf "1"/"0" normalisieren statt auf "true"/"false": das ist exakt
        // das Format, das `PreferencesStore.toServerSettings()` beim PUT zurückschickt
        // und das IConfig serverseitig persistiert – Hin- und Rückweg bleiben symmetrisch.
        // `isString` schützt davor, einen echten String-Wert "true" mitzukonvertieren.
        if (!primitive.isString) {
            primitive.booleanOrNull?.let { return if (it) "1" else "0" }
        }

        return primitive.content
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}
