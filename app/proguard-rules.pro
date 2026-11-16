# R8-/ProGuard-Regeln für den Release-Build (isMinifyEnabled = true).
# Die meisten Bibliotheken (Retrofit 3, OkHttp 5, kotlinx.serialization, Hilt/Dagger,
# Room, WorkManager, Media3, Coil, Compose) bringen eigene Consumer-Regeln mit.
# Hier steht nur, was die App selbst zusätzlich braucht.

# Lesbare Stacktraces in Crash-Reports (Zeilennummern behalten, Dateinamen verbergen)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WebView-JS-Bridges (ReaderJsBridge, PdfReaderView): Methoden werden per Name
# aus JavaScript aufgerufen und dürfen weder entfernt noch umbenannt werden.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# Retrofit: Generics und Annotationen der API-Interfaces für die Reflection erhalten
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-keep,allowobfuscation,allowshrinking interface dev.merlin.android.network.MerlinApi

# kotlinx.serialization: Modelle samt generierter Serializer behalten (zusätzlich
# zu den Regeln der Bibliothek, damit Server-Feldnamen/Enums sicher erhalten bleiben)
-keep,includedescriptorclasses class dev.merlin.android.models.**$$serializer { *; }
-keepclassmembers class dev.merlin.android.models.** {
    *** Companion;
}
-keepclasseswithmembers class dev.merlin.android.models.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Optionale Abhängigkeiten von OkHttp ohne Laufzeitbedarf
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
