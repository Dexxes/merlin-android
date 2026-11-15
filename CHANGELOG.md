# Changelog

All notable changes to Merlin for Android are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed

- PDF articles: a saved link to a PDF (server category `PDF`, or a URL ending
  in `.pdf`) opens in the reader as the document itself. The server stores only
  the URL; the app downloads the PDF from the source when the article is
  opened (own OkHttp client without the Merlin login), renders it page by page
  with the framework `PdfRenderer` and keeps a local copy for offline reading
  (`PdfCacheService`, pruned by the cache retention, on delete and by "Clear
  cache"). Reading progress and position restore work as for text articles.
  Limitations: no pinch zoom, text selection, highlights or appearance
  settings for PDFs; password-protected PDFs show a notice with "Im Browser
  öffnen". Not compiled or run on a device yet.

- Article list matches the iOS app: a side menu with Text/Video/Audio tabs,
  each with Continue, Unread, Favorites and Archive views and their counts,
  plus tags, list/card view, reminders and settings; a search button next to
  the menu button shows the search field; older articles load while scrolling; rows and cards show reading
  progress, a lock for paywalled articles and an "Archived" marker in mixed
  lists; actions are on long press (and swipe in the list view) instead of the
  star and ⋮ buttons; the default view syncs with the same values as iOS and
  the web app

- Support box in the reader: a note between two paragraphs ("Enjoying this
  article from …? Consider a subscription or a donation") linking to the
  source's subscription/donation page, in the user's accent color and with the
  icon of the article page as its own column on the left (2.5 em wide), spanning
  the full height of the box, with the text beside it vertically centred. Hidden when a paywall subscription login is
  active; not shown offline
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
- Native ARD/ZDF/Arte-Mediathek video playback in the reader (Media3/
  ExoPlayer), resolving the `/video-stream` endpoint with variant and
  subtitle-track selection, matching the iOS implementation
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
