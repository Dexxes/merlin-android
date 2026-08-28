# Merlin for Android

Merlin is a read-it-later client for Android, built with Kotlin and Jetpack
Compose. It connects to either a self-hosted [Nextcloud](https://nextcloud.com/)
instance running the Merlin app, or to the standalone `merlin-server`
backend — no Nextcloud installation required.

This is the Android counterpart to [`merlin-ios`](../merlin-ios), sharing the
same data model and API surface.

## Features

- **Article list** with filters (unread/favorites/archived), search, tag
  chips, and swipe actions (archive, favorite, delete, share)
- **Reader** with adjustable appearance (theme, font, size, line height,
  accent color, progress bar position), text highlighting, image lightbox,
  and article reporting
- **Offline-first**: articles, images, and highlights are cached locally
  (Room + Coil) and mutations queue up via WorkManager when offline, then
  drain automatically once connectivity returns
- **Reminders** for articles, scheduled via `AlarmManager` with local
  notifications
- **Reading-position sync** across devices (Android/iOS/Web) via the server,
  with last-write-wins conflict resolution
- **Share-to-Merlin**: save articles from any app via the Android share sheet
- **Dual backend support**: connect to a Nextcloud instance or to a
  standalone `merlin-server`, selectable during onboarding or later in
  Settings. Nextcloud-only features (TTS, SSE, settings sync, public share,
  YouTube embed proxy) are automatically hidden when using the standalone
  backend
- Login via Nextcloud/`merlin-server` Login Flow v2 (opened in a Custom Tab)
  — Merlin never sees your password directly

## Tech stack

- Kotlin, Jetpack Compose, Navigation Compose
- Hilt (dependency injection)
- Retrofit + OkHttp + kotlinx.serialization (networking)
- Room + DataStore (persistence)
- WorkManager (offline mutation queue, background drain)
- Coil (image loading/caching)
- Media3/ExoPlayer (text-to-speech playback, in progress)

See [`Structure.md`](Structure.md) for a detailed breakdown of the project
layout.

## Requirements

- Android Studio (current stable)
- JDK 17
- Android SDK with `compileSdk 37` / `targetSdk 34` platforms installed
- A running Nextcloud instance with the Merlin app, or a `merlin-server`
  instance to connect to

## Getting started

1. Clone the repository:

   ```bash
   git clone https://github.com/Dexxes/merlin-android.git
   cd merlin-android
   ```

2. Open the project in Android Studio and let it sync (it will offer to
   generate the Gradle wrapper JAR if missing).

3. Build and run from the command line instead, if preferred:

   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

4. On first launch, choose "Nextcloud" or "Standalone-Server" and enter your
   server URL to start the login flow.

## Project status

Onboarding/login, offline-first persistence, reminders, the article
list/reader UI, share extension, and settings are complete. The TTS panel
and onboarding tour are still outstanding. See [`CHANGELOG.md`](CHANGELOG.md)
for details.

## License

Merlin for Android is licensed under the [GNU Affero General Public License
v3.0](LICENSE) (AGPL-3.0-or-later).
