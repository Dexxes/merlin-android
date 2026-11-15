# Changelog

All notable changes to Merlin for Android are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Nested tags (merlin-nextcloud): the side menu shows tags as a tree whose
  parents expand and collapse (remembered across launches). Long-press a tag
  for "Umbenennen…", "Verschieben nach…" (pick a new parent or the top
  level; merlin-nextcloud) and "Tag löschen" (removes the tag and its sub-tags from all articles, articles are
  kept). Opening a tag and hiding a tag include its sub-tags, also offline.
  "Tags bearbeiten" and "Artikel hinzufügen" pick tags from the tree (a
  sub-tag selects its parents) and can create a new tag below another one.
  Not compiled or run on a device yet.
- Files tab (merlin-nextcloud): entries from "Merlin Dateien" appear under a
  new "Dateien" tab in the side menu with "Nicht geöffnet", favorites and
  archive. File entries can be renamed from the long-press menu and the
  reader menu ("Umbenennen…"); the extension stays and a taken name shows an
  error. PDF files open from the signed link in the entry instead of the
  Nextcloud Files URL. Not compiled or run on a device yet.

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
- Video playback in the reader for every video source the iOS app supports:
  native ARD Mediathek/ZDF/3sat/Arte playback (Media3/ExoPlayer), resolving
  the `/video-stream` endpoint with variant and subtitle-track selection (the
  cover image stays when no stream is available); YouTube embeds as a tappable
  preview card that starts the youtube-nocookie player in place; videos inside
  the article text (`figure.merlin-inline-media`, e.g. rbb24.de) and lead
  videos from the media marker (e.g. tagesschau.de) as an inline HTML5 player
  on the cover image, with fullscreen support. Not compiled or run on a
  device yet.
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
### Fixed

- Offline, the side menu no longer shows 0 for every count and no tags: the
  last known counts and tags are stored on the device and shown until the
  server answers again, also after a cold start without a connection. A
  failed tag refresh keeps the current tags instead of clearing them. Logout
  and "Cache leeren" remove the stored state. Not compiled or run on a device
  yet.
