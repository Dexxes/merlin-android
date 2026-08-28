# Changelog

All notable changes to Merlin for Android are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Support for connecting to a standalone `merlin-server` backend in addition
  to Nextcloud, selectable in onboarding and Settings
  (`CredentialsStore.BackendKind`)
- Onboarding flow with server URL entry and Nextcloud/`merlin-server` Login
  Flow v2 via Custom Tabs
- Offline-first article cache, image cache, and mutation queue (Room +
  WorkManager), with automatic drain on reconnect
- Article list with unread/favorite/archived filters, search, tag filtering,
  and swipe actions (archive, favorite, delete, share)
- Reader with adjustable appearance (theme, font, size, line height, accent
  color, progress bar edge), text highlighting, image lightbox, and article
  reporting
- Reminders for articles via `AlarmManager`, with a dedicated overview screen
- Cross-device reading-position sync with last-write-wins conflict resolution
- Share-to-Merlin support via the Android share sheet (`ShareActivity`)
- Settings screen: account management, connection test, reading preferences,
  cache management, and a developer mode
- Haptic feedback for key interactions
- Localization pipeline: string resources (`res/values(-de)/strings_i18n.xml`)
  generated from the shared `merlin-translations` source of truth; onboarding
  and settings screens migrated to `stringResource()`/`pluralStringResource()`
- Settings sync with offline retry (`SettingsSyncQueue`, `SettingsSyncWorker`):
  a failed settings push is marked dirty and retried via WorkManager once
  connectivity returns, instead of being silently dropped

### Known limitations

- Text-to-speech playback (Media3/ExoPlayer) is not yet implemented
- Onboarding tour is not yet implemented
- Real-time article updates use polling instead of an SSE stream
- Reading-position sync is fire-and-forget with no offline retry
- Most screens (article list/reader, onboarding tour, reminders, share,
  sheets) still have hardcoded strings and are not yet migrated to string
  resources
