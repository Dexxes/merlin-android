package dev.merlin.android.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import androidx.annotation.StringRes
import androidx.exifinterface.media.ExifInterface
import dev.merlin.android.R
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/** Komprimierungsstufen, die das Share-Sheet für geteilte Fotos anbietet. */
enum class ImageCompression(
    /** Längste Seite in Pixeln, `null` = Original hochladen. */
    val maxPixelSize: Int?,
    /** JPEG-Qualität 0…100. */
    val quality: Int,
    /** Beschriftung der Kachel (String-Ressource, siehe merlin-translations `share.files.compression.*`). */
    @StringRes val titleRes: Int,
) {
    STRONG(1600, 55, R.string.share_files_compression_strong),
    LIGHT(4096, 80, R.string.share_files_compression_light),
    ORIGINAL(null, 100, R.string.share_files_compression_original),
}

/**
 * Die geteilten Dateien in einer Stufe: Fotos als JPEG neu kodiert, alles andere
 * (Videos, PDFs, GIFs …) unverändert. [preview] ist ein mittiger Ausschnitt des
 * ersten Fotos in dieser Stufe, damit die Kacheln den tatsächlichen
 * Detailverlust zeigen (ein Vorschaubild des ganzen Bildes sähe in allen drei
 * Stufen gleich aus).
 */
data class CompressionOption(
    val level: ImageCompression,
    val files: List<SharedFile>,
    val totalSize: Long,
    val preview: Bitmap?,
)

/**
 * Portierung von `ImageCompressor.swift` (MerlinShare). Arbeitet mit Dateien und
 * hält höchstens ein dekodiertes Foto gleichzeitig im Speicher.
 *
 * Anders als ImageIO auf iOS kopiert Android nur die EXIF-Daten (über
 * [ExifInterface], inkl. Ort), IPTC und XMP gehen beim Komprimieren verloren.
 * Wie auf iOS bleiben die Pixel in der Ausrichtung des Originals, damit dessen
 * Orientation-Tag weiter stimmt.
 */
object ImageCompressor {
    /** Dekodier-Grenze für Vorschauen. */
    private const val PREVIEW_DECODE_SIZE = 4096
    /** Seite des Vorschau-Ausschnitts relativ zur längeren Bildseite. */
    private const val PREVIEW_CROP_FRACTION = 0.15
    private const val PREVIEW_PIXELS = 360

    fun isCompressible(file: SharedFile): Boolean {
        val type = file.mimeType.lowercase()
        // Animierte GIFs verlören ihre Animation, SVG ist keine Bitmap.
        return type.startsWith("image/") && type != "image/gif" && type != "image/svg+xml"
    }

    suspend fun option(files: List<SharedFile>, level: ImageCompression): CompressionOption {
        // Abgebrochen, wenn das Share-Sheet schließt: übrige Fotos überspringen.
        val converted = files.map { file ->
            if (isCompressible(file) && currentCoroutineContext().isActive) compress(file, level) ?: file else file
        }
        val firstImage = files.zip(converted).firstOrNull { isCompressible(it.first) }?.second
        return CompressionOption(
            level = level,
            files = converted,
            totalSize = converted.sumOf { it.size },
            preview = firstImage?.let { runCatching { previewCrop(it) }.getOrNull() },
        )
    }

    /**
     * JPEG-Fassung von [file] daneben (`<ordner>/<stufe>/<name>.jpg`). Das Original,
     * wenn es schon kleiner ist, `null`, wenn es sich nicht lesen lässt.
     */
    fun compress(file: SharedFile, level: ImageCompression): SharedFile? {
        val maxPixelSize = level.maxPixelSize ?: return file
        val decoded: Bitmap? = try {
            decode(file.file, maxPixelSize)
        } catch (e: OutOfMemoryError) {
            null
        }
        val bitmap = decoded ?: return null

        val name = file.name.substringBeforeLast('.', file.name) + ".jpg"
        val folder = File(file.file.parentFile, level.name.lowercase())
        val target = File(folder, name)
        try {
            folder.mkdirs()
            target.outputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, level.quality, out)) return null
            }
            copyExif(from = file.file, to = target, width = bitmap.width, height = bitmap.height)
        } catch (e: Exception) {
            target.delete()
            return null
        } finally {
            bitmap.recycle()
        }

        val size = target.length()
        if (size <= 0 || size >= file.size) {
            target.delete()
            return file
        }
        return SharedFile(file = target, name = name, mimeType = "image/jpeg", size = size)
    }

    /**
     * Dekodiert das Bild verkleinert auf höchstens [maxPixelSize] an der längeren
     * Seite (nie vergrößert), in der gespeicherten Ausrichtung. `inSampleSize` hält
     * den Speicher klein; der Rest wird genau skaliert.
     */
    private fun decode(file: File, maxPixelSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val longSide = max(bounds.outWidth, bounds.outHeight)
        if (longSide <= 0) return null

        var sample = 1
        while (longSide / (sample * 2) >= maxPixelSize * 3 / 4) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null

        val decodedLong = max(decoded.width, decoded.height)
        if (decodedLong <= maxPixelSize) return decoded
        val scale = maxPixelSize.toDouble() / decodedLong
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            max(1, (decoded.width * scale).roundToInt()),
            max(1, (decoded.height * scale).roundToInt()),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    /** Übernimmt die EXIF-Daten des Originals und trägt die neue Pixelgröße ein. */
    private fun copyExif(from: File, to: File, width: Int, height: Int) {
        val source = runCatching { ExifInterface(from.path) }.getOrNull() ?: return
        val target = ExifInterface(to.path)
        for (tag in EXIF_TAGS) {
            source.getAttribute(tag)?.let { target.setAttribute(tag, it) }
        }
        target.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, width.toString())
        target.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, height.toString())
        target.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width.toString())
        target.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height.toString())
        target.saveAttributes()
    }

    /**
     * Quadratischer Ausschnitt aus der Bildmitte, aufrecht gedreht und in eine kleine
     * Bitmap gezeichnet, damit das große dekodierte Bild gleich wieder frei wird.
     */
    private fun previewCrop(file: SharedFile): Bitmap? {
        val image = decode(file.file, PREVIEW_DECODE_SIZE) ?: return null
        try {
            val longSide = max(image.width, image.height).toDouble()
            val side = min(min(image.width, image.height), max(1, (longSide * PREVIEW_CROP_FRACTION).roundToInt()))
            val left = (image.width - side) / 2
            val top = (image.height - side) / 2

            val preview = Bitmap.createBitmap(PREVIEW_PIXELS, PREVIEW_PIXELS, Bitmap.Config.ARGB_8888)
            // Vergrößern (starke Komprimierung) ohne Glättung, damit die Pixel sichtbar bleiben.
            val paint = Paint().apply { isFilterBitmap = side >= PREVIEW_PIXELS }
            Canvas(preview).drawBitmap(
                image,
                Rect(left, top, left + side, top + side),
                Rect(0, 0, PREVIEW_PIXELS, PREVIEW_PIXELS),
                paint,
            )
            return upright(preview, file.file)
        } finally {
            image.recycle()
        }
    }

    /** Dreht/spiegelt die (quadratische) Vorschau nach dem Orientation-Tag. */
    private fun upright(bitmap: Bitmap, source: File): Bitmap {
        val exif = runCatching { ExifInterface(source.path) }.getOrNull() ?: return bitmap
        val matrix = Matrix()
        val degrees = exif.rotationDegrees
        if (exif.isFlipped) matrix.postScale(-1f, 1f)
        if (degrees != 0) matrix.postRotate(degrees.toFloat())
        if (matrix.isIdentity) return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    /** EXIF-Felder, die beim Komprimieren mitgenommen werden (Pixelmaße setzt [copyExif] neu). */
    private val EXIF_TAGS = listOf(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_LENS_SPECIFICATION,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_APERTURE_VALUE,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_SHUTTER_SPEED_VALUE,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_EXPOSURE_PROGRAM,
        ExifInterface.TAG_EXPOSURE_MODE,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_SCENE_CAPTURE_TYPE,
        ExifInterface.TAG_BRIGHTNESS_VALUE,
        ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
        ExifInterface.TAG_SUBJECT_AREA,
        ExifInterface.TAG_COLOR_SPACE,
        ExifInterface.TAG_GPS_VERSION_ID,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_SPEED,
        ExifInterface.TAG_GPS_SPEED_REF,
        ExifInterface.TAG_GPS_IMG_DIRECTION,
        ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
        ExifInterface.TAG_GPS_DEST_BEARING,
        ExifInterface.TAG_GPS_DEST_BEARING_REF,
        ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
    )
}
