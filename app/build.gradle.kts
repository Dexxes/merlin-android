plugins {
    id("com.android.application")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0"
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.merlin.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "merlin.android"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    // Splash-Screen (Android 12+ SplashScreen API, Compat zurück bis minSdk 23)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // Networking (Äquivalent zu MerlinAPI.swift)
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // DI
    implementation("com.google.dagger:hilt-android:2.60")
    ksp("com.google.dagger:hilt-compiler:2.60")
    // Dagger/Hilt-generierter Code (DaggerMerlinApp_HiltComponents_SingletonC) referenziert seit
    // Dagger 2.56+ direkt @CanIgnoreReturnValue aus error-prone; Dagger zieht die Annotation selbst
    // nur als `compileOnly`, wodurch sie für den javac-Lauf dieses Moduls fehlt – ohne diese explizite
    // `implementation`-Abhängigkeit bricht `hiltJavaCompileDebug` mit "Package ... ist nicht vorhanden" ab.
    implementation("com.google.errorprone:error_prone_annotations:2.36.0")

    // Persistenz (Offline-Cache, Reminder, Mutation-Queue)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("androidx.hilt:hilt-work:1.3.0")
    ksp("androidx.hilt:hilt-compiler:1.3.0")
    implementation("androidx.security:security-crypto:1.1.0")

    // Login Flow v2 (Custom Tabs zum Öffnen der Server-Login-URL)
    implementation("androidx.browser:browser:1.10.0")

    // Bilder / TTS
    implementation("io.coil-kt:coil:2.7.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    // PlayerView (ARD/ZDF/Arte-Wiedergabe, siehe NativeVideoPlayerCard.kt)
    implementation("androidx.media3:media3-ui:1.10.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
