// Root build.gradle.kts – nur Plugin-Versionen deklarieren, keine Module hier konfigurieren.
// Kotlin bewusst auf 2.3.20 (nicht 2.4.0) gepinnt: KSP 2.3.9 unterstützt das
// Metadata-Format von Kotlin 2.4.0 noch nicht (siehe github.com/google/ksp/issues/2965,
// offen seit 04.06.2026) – Build bricht sonst mit "Provided Metadata instance
// has version 2.4.0, while maximum supported version is 2.3.0" ab.
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
    id("com.google.dagger.hilt.android") version "2.60" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("com.google.devtools.ksp") version "2.3.9" apply false
}
