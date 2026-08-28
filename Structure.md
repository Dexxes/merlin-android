# Merlin Android – Projektstruktur

Status: Onboarding/Login-Flow, Persistenz/Offline-First (Abschnitt 4),
Reminder-System (Abschnitt 5), ViewModel-Schicht (Abschnitt 8), Artikelliste-
und Reader-UI (Abschnitt 9, Teil 1+2 – v1, in Navigation eingehängt) sowie
Share-Funktionalität (Abschnitt 10) fertig; im Reader zusätzlich
`ReminderSheet`, `ImageLightboxScreen` und `ReportArticleSheet` (Abschnitt 9
Rest) sowie die eigenständige `RemindersScreen`-Übersicht fertig. Die
`SettingsScreen` (Login/Logout, Verbindungstest, Präferenzen, Cache,
Entwicklermodus) ist ebenfalls fertig und in die Navigation eingehängt.
TTS-Panel und `OnboardingTour` fehlen noch.
Unterstützt bereits **beide** Backends (Nextcloud und den unabhängigen
`merlin-server`) über `CredentialsStore.BackendKind` (`NEXTCLOUD`/`STANDALONE`,
Äquivalent zu iOS' `CredentialsStore.BackendKind`) mit Auswahl in Onboarding-
und Settings-Screen – Details siehe Netzwerk-Layer- und Auth-Tabelle unten.
Gegenstück zu `merlin-ios/Structure.md`. Stack:
Kotlin, Jetpack Compose, Navigation-Compose, Retrofit/OkHttp, Hilt, Room,
DataStore, WorkManager, Coil.

## Konfigurationsdateien

| Datei | Zweck |
|---|---|
| `settings.gradle.kts` | Modul-Einbindung (`:app`), Repository-Deklaration |
| `build.gradle.kts` (root) | Plugin-Versionen (Android, Kotlin, Hilt) |
| `gradle.properties` | JVM-Args, AndroxX/Kotlin-Flags |
| `app/build.gradle.kts` | App-Modul: `applicationId dev.merlin.android`, minSdk 23 (siehe Begründung in todo.md, Abschnitt „Offene Fragen"), targetSdk/compileSdk 34, alle Dependencies (Compose, Retrofit, Room, Hilt, Media3, Coil) |
| `app/src/main/AndroidManifest.xml` | Permissions (Internet, Notifications, Exact Alarm), `android:icon`/`android:roundIcon` → `@mipmap/ic_launcher`/`ic_launcher_round` (siehe „App-Icon" unten), `MainActivity` als Launcher; `ShareActivity` mit `ACTION_SEND`/`text/plain`-Intent-Filter (Abschnitt 10), `excludeFromRecents`/`noHistory` da reine Overlay-Activity |

## Lokalisierung (`res/values(-de)/strings_i18n.xml`)

`merlin-translations` ist die zentrale Quelle für alle UI-Strings
(`localization/strings/<lang>.json`, siehe `schema.md` dort). `export.py
--platform android` generiert daraus `res/values/strings_i18n.xml`
(Quellsprache `en`) und `res/values-de/strings_i18n.xml` – eigene Datei statt
Einträge in der bestehenden `strings.xml`, damit die dort von Hand gepflegte
`app_name`-Ressource unangetastet bleibt. Nicht direkt editieren, Änderungen
gehen beim nächsten Export verloren – stattdessen `strings/<lang>.json` in
`merlin-translations` anpassen und neu exportieren. Wie iOS werden
`webext.*`/`nextcloudWeb.*`/`merlinServer.*`-Keys nicht mit exportiert.
Mehrfache Platzhalter in einem String werden positionell (`%1$s`, `%2$d`)
statt wie bei iOS einfach (`%@`/`%lld`) ausgegeben – Android verlangt das bei
mehr als einem Platzhalter zwingend.

**Migrationsstand:** `OnboardingScreen.kt` und `SettingsScreen.kt` (Server-
URL-Eingabe, Login-Flow, Konto-/Präferenzen-/Cache-/Über-/Entwickler-
Sektionen) sind vollständig auf `stringResource()`/`pluralStringResource()`
umgestellt. Backend-Namen (`"Nextcloud"`/`"Standalone-Server"`) und die
`ProgressEdge`-Kantenlabel (`"Links"`/`"Oben"`/…) bleiben bewusst
hartcodiert – analog zu `SettingsView.swift`, das dieselben Strings ebenso
wenig lokalisiert. Alle übrigen Screens (Artikelliste/-reader, Onboarding-
Tour, Erinnerungen, Teilen, Sheets) haben noch keine `stringResource()`-Aufrufe
und müssen schrittweise nachgezogen werden – die dafür nötigen Keys liegen
bereits vollständig in `strings_i18n.xml` (819 Keys insgesamt, siehe
`merlin-translations/localization/strings/en.json`).

## App-Icon (`res/mipmap-*`)

Aus dem iOS-Quellbild generiert: `merlin-ios/Sources/Merlin/Assets.xcassets/AppIcon.appiconset/AppIcon.png`
(1024×1024, Pixel-Art-Hund + aufgeschlagenes Buch auf transparentem Grund).
Per Pillow-Skript (einmalig, nicht Teil des Build-Prozesses) auf den Inhalts-
Bounding-Box zugeschnitten und in zwei Varianten neu gerendert:

| Datei(typ) | Zweck |
|---|---|
| `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png` | Legacy-Icon (API <26): Inhalt zu 78% auf weißem Quadrat zentriert |
| `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_round.png` | Wie oben, zusätzlich kreisrund freigestellt (Alpha-Maske) für Launcher, die `android:roundIcon` nutzen |
| `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_foreground.png` | Adaptive-Icon-Vordergrund (API 26+): Inhalt auf Transparenz, auf 62% skaliert, damit er innerhalb der von Google vorgegebenen 66%-Safe-Zone des 108dp-Layer-Canvas bleibt (verhindert Beschnitt durch launcherspezifische Masken: Kreis/Squircle/abgerundetes Quadrat/...) |
| `values/ic_launcher_background.xml` | `<color name="ic_launcher_background">#FFFFFF</color>` – Hintergrundebene des Adaptive Icons, Äquivalent zum weißen Hintergrund im iOS-Original |
| `mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | `<adaptive-icon>`-Deklaration, kombiniert Hintergrundfarbe + Vordergrund-PNG |

Größen unverändert vom Android-Standardraster: Legacy 48/72/96/144/192px,
Adaptive-Vordergrund 108/162/216/324/432px (jeweils mdpi→xxxhdpi).

## App-Einstieg

| Datei | Zweck |
|---|---|
| `MerlinApp.kt` | `Application`-Klasse mit `@HiltAndroidApp`; Äquivalent zu `MerlinApp.swift` (Splash-/Migrations-Logik fehlt noch) |
| `MainActivity.kt` | Einzige Activity, hostet Compose; zeigt `OnboardingScreen` solange `CredentialsStore.isConfigured == false`, sonst ein `NavHost` (Routen `list`/`reader/{articleId}`, Äquivalent zu `NavigationStack`) zwischen `ArticleListScreen` und `ArticleReaderScreen`. `articleId` als typisiertes Nav-Argument, damit `hiltViewModel()` die `SavedStateHandle` von `ArticleReaderViewModel` automatisch befüllt. `AppNavigator.articleIdToOpen` (Reminder-Deep-Link) wird per `LaunchedEffect` konsumiert und navigiert direkt zum Reader |
| `ui/theme/Theme.kt` | `MerlinTheme`-Composable mit Platzhalter Light/Dark-Farbschema; wird mit der Settings-Portierung um Theme-Auswahl (light/dark/auto) erweitert |

## Models (`models/`)

1:1-Übertragung von `merlin-ios/Sources/Merlin/Models/`, als `kotlinx.serialization`-`@Serializable data class`:

| Datei | Zweck |
|---|---|
| `Article.kt` | Wie iOS-Original inkl. `displayTitle`/`displaySiteName`/`faviconUrl`; eigene `equals`/`hashCode` (id + isProcessing + updatedAt + sortierte Tag-IDs) statt Default-Datenklassen-Vergleich, aus demselben Grund wie im iOS-Original (Recomposition bei Server-Updates) |
| `Tag.kt` | 1:1 |
| `Highlight.kt` | `Highlight` + `HighlightCreate` (Request-Payload ohne Server-Felder) |
| `Reminder.kt` | `triggerAt`/`createdAt` als Epoch-Millis statt `Date` (passt direkt zu `AlarmManager`/`WorkManager`); `id` als String-UUID statt `UUID`-Typ wegen Room-Kompatibilität |

## Netzwerk-Layer (`network/`)

| Datei | Zweck |
|---|---|
| `MerlinApi.kt` | Retrofit-Interface auf Basis von `merlin-api.yaml`: Artikel-CRUD/-Suche/-Counts, Read/Favorite/Archive-Toggle, Tags-CRUD, Tag-Zuordnung, Settings. SSE-Stream (`articleUpdateStream`) bewusst ausgeklammert – eigener Task |
| `AuthInterceptor.kt` | OkHttp-Interceptor für HTTP-Basic-Auth; `CredentialsProvider`-Interface als Abstraktion, jetzt von `CredentialsStore` implementiert |
| `CredentialsStore.kt` | Äquivalent zu `CredentialsStore.swift`: `nextcloudUrl`/`username`/`appPassword` verschlüsselt via `EncryptedSharedPreferences` (Android-Keystore-backed); implementiert `CredentialsProvider` direkt. Keine Shared-Access-Group nötig (anders als iOS), da Hauptapp und künftiges Share-Target dieselbe App-UID nutzen. Enthält zusätzlich `BackendKind`-Enum (`NEXTCLOUD`/`STANDALONE`) für den unabhängigen `merlin-server` als zweites Backend; `supportsNextcloudOnlyFeatures` blendet darüber TTS/SSE/Settings-Sync/Public-Share/YouTube-Embed-Proxy für `STANDALONE` aus. Default `NEXTCLOUD`, bleibt bei `clearCredentials()` bewusst erhalten (Vorbelegung für die nächste Anmeldung) |
| `BaseUrlInterceptor.kt` | Schreibt Scheme/Host/Port (und ggf. Unterpfad) jeder Anfrage zur Laufzeit auf die in `CredentialsStore.nextcloudUrl` hinterlegte Server-URL um, da Retrofits `baseUrl` nur als nie angefragter Platzhalter dient. Bei `BackendKind.STANDALONE` wird zusätzlich das Nextcloud-App-Routing-Präfix `/index.php/apps/merlin` aus dem Pfad entfernt, da `merlin-server` dieselben API-Pfade direkt unter `/api` anbietet (siehe `merlin-server/public/index.php`) |
| `NetworkModule.kt` | Hilt-Modul: `Json`, `OkHttpClient` (mit `BaseUrlInterceptor` + `AuthInterceptor`), `Retrofit`, `MerlinApi` |

## Auth (`auth/`)

| Datei | Zweck |
|---|---|
| `LoginFlowService.kt` | Äquivalent zu `LoginFlowService.swift`: Nextcloud Login Flow v2 (`start()` → POST `/index.php/login/v2`, `pollForCredentials()` → Polling bis Login im Browser abgeschlossen ist, 5-Minuten-Timeout). Schreibt Ergebnis in `CredentialsStore`. Nutzt bewusst einen eigenen unauthentifizierten `OkHttpClient` statt der Hilt-Singleton-Instanz, da letztere `BaseUrlInterceptor` voraussetzt. Start-Pfad hängt von `CredentialsStore.backendKind` ab: Nextcloud nutzt `/index.php/login/v2`, der unabhängige `merlin-server` (bildet Login-Flow-v2-JSON identisch nach, siehe `merlin-server/src/Controller/LoginFlowController.php`) `/login/v2` |

## Persistenz / Offline-First (`data/`)

Abschnitt 4 aus `todo.md`. Architekturentscheidung: Room als einheitliche
Persistenzschicht für Artikel-Cache, Bild-Cache-Index und Mutation-Queue
(statt iOS' JSON-Dateien), da Abschnitt 5 (Reminder) ohnehin Room braucht.
`Article` wird dabei als JSON-Blob pro Zeile gespeichert statt normalisiert –
Room ersetzt so nur den transaktionssicheren Datei-Write, während die
Filter-/Eviction-Logik 1:1 aus den Swift-Originalen übernommen werden kann.

| Datei | Zweck |
|---|---|
| `AppDatabase.kt` | `RoomDatabase` mit den drei Entities (Artikel-Cache, Bild-Cache-Index, Pending-Mutations) |
| `DatabaseModule.kt` | Hilt-Modul: stellt `AppDatabase` + alle drei DAOs bereit |
| `Converters.kt` | Room-`TypeConverter` für `PendingMutationKind` (Enum ↔ String) |
| `ArticleEntity.kt` / `ArticleDao.kt` | Artikel als JSON-Blob (`id`, `json`); `getAll`/`upsertAll`/`deleteById`/`deleteByIds`/`clearAll` |
| `ArticleCacheService.kt` | Äquivalent zu `ArticleCacheService.swift`: `loadFiltered(filter, tagId)`, `upsert`, `remove`, `evict` (archivierte Artikel >24h löschen), `clear`; Mutex-isoliert (Kotlin-Äquivalent zu Swifts `actor`) |
| `ImageCacheIndexEntity.kt` / `ImageCacheIndexDao.kt` | Room-Index `articleId → urlsJson`, ermöglicht gezielte Pro-Artikel-Eviction, die Coils Disk-Cache nativ nicht bietet |
| `RefererInterceptor.kt` | OkHttp-Interceptor, setzt `Referer: scheme://host/` pro Request (Hotlink-Schutz) |
| `ImageModule.kt` | Hilt-Modul: eigener Coil-`ImageLoader` mit `RefererInterceptor` in dessen `OkHttpClient` |
| `ImageCacheService.kt` | Äquivalent zu `ImageCacheService.swift`, aufbauend auf Coils Disk-Cache statt eigenem Hash-Dateinamen-Cache: `prefetch` (WLAN-only-Option, `Semaphore(4)`), `fetchSingle`, `evict(articleId)`, `clear` |
| `PreferencesStore.kt` | Äquivalent zu `PreferencesStore.swift` via `DataStore<Preferences>`: Filter, Theme, Schriftgröße, Zeilenhöhe, Fortschrittsbalken-Position, Akzentfarbe, Lese-/Scroll-Position pro Artikel, ausgeblendete Tag-IDs, Server-Sync (`loadFromServer`/`toServerSettings`) |
| `PendingMutationEntity.kt` / `MutationDao.kt` | Wartende Mutationen (`TOGGLE_ARCHIVE`/`TOGGLE_FAVORITE`/`SET_TAGS`/`DELETE`) je Artikel |
| `OfflineMutationQueue.kt` | Äquivalent zu `OfflineMutationQueue.swift`: Enqueue mit Dedup (delete verdrängt alles, setTags last-write-wins, Toggle-Parität beim Drain), `drain()` leert die Queue vor Ausführung der berechneten Mutationen, 404-bei-Delete = Erfolg, `onDrained`-Callback, `scheduleDrain()` über WorkManager |
| `MutationDrainWorker.kt` | `@HiltWorker`/`@AssistedInject`-`CoroutineWorker`, ruft `OfflineMutationQueue.drain()`; Constraint `NetworkType.CONNECTED` (Äquivalent zum `NWPathMonitor`-Trigger im iOS-Original) |
| `HapticUtil.kt` | Äquivalent zu `UIImpactFeedbackGenerator+Haptics.swift`: Hilt-Singleton um den `Vibrator`-Systemdienst (`VibratorManager` ab API 31), `lightTap()`/`mediumTap()`/`heavyTap()` über `VibrationEffect.createPredefined(EFFECT_TICK/CLICK/HEAVY_CLICK)` (API 29+) mit `createOneShot`-Fallback für API 26-28. Aus `ArticlesViewModel` injiziert (Abschnitt 11), da Vibrator-Zugriff aus einem ViewModel sonst keine View-gebundene API hätte |

`MerlinApp.kt` implementiert zusätzlich `Configuration.Provider` und injiziert
`HiltWorkerFactory`, damit WorkManager Hilt-Worker korrekt instanziiert; dafür
deaktiviert `AndroidManifest.xml` per `tools:node="remove"` auf
`WorkManagerInitializer` den `androidx.startup`-Default-Init.

## Reminder-System (`data/`, `nav/`)

Abschnitt 5 aus `todo.md`. Android kennt keine vom System selbst ausgelöste
Termin-Notification wie `UNCalendarNotificationTrigger` – stattdessen plant
`ReminderService` einen exakten `AlarmManager`-Alarm, der `ReminderBroadcastReceiver`
auslöst, der die Notification manuell baut/postet und den Reminder als
`FIRED` markiert.

| Datei | Zweck |
|---|---|
| `ReminderEntity.kt` / `ReminderDao.kt` | Room-Persistenz für `Reminder` (ersetzt `reminders.json`); `AppDatabase` auf Version 2 (`fallbackToDestructiveMigration`, da Pre-Release ohne Migrationsbedarf) |
| `ReminderError.kt` | Äquivalent zu `ReminderError` (Swift): `PermissionDenied` (Notifications), `ExactAlarmNotPermitted` (Android 12+) |
| `ReminderService.kt` | Äquivalent zu `ReminderService.swift`: `all()`, `reminder(articleId)`, `schedule(article, triggerAtMillis)` (ersetzt bestehenden Reminder für den Artikel, prüft Notification- und Exact-Alarm-Berechtigung), `cancel(articleId)`, `markFired(reminderId)`; Mutex-isoliert |
| `ReminderBroadcastReceiver.kt` | `@AndroidEntryPoint`-`BroadcastReceiver`, vom Alarm getriggert: erstellt bei Bedarf den Notification-Channel, postet die Notification (Deep-Link-Extra `open_article_id` → `MainActivity`), markiert den Reminder per `goAsync()`-Coroutine als `FIRED` |
| `nav/AppNavigator.kt` | Äquivalent zu `AppNavigator` (Swift): `StateFlow<Int?> articleIdToOpen`, von `MainActivity` gesetzt; wird im `NavHost` von `MainActivity.kt` per `LaunchedEffect` konsumiert (`navController.navigate("reader/$id")` + `consume()`) |

`MainActivity.kt` liest in `onCreate`/`onNewIntent` den Intent-Extra
`open_article_id` und reicht ihn an `AppNavigator` weiter; `launchMode="singleTop"`
in `AndroidManifest.xml` verhindert eine zweite Activity-Instanz bei
Reminder-Tap während die App schon läuft.

## ViewModel-Schicht (`viewmodel/`)

| Datei | Zweck |
|---|---|
| `OnboardingViewModel.kt` | `@HiltViewModel`, kapselt `LoginFlowService` für die UI: `isLoading` deckt sowohl den `start()`-Request als auch die Poll-Phase ab (anders als `LoginFlowService.isLoading`, das nur während des ersten Requests `true` ist), plus `loginUrl`/`error`/`loginSuccess` als `StateFlow` |
| `ArticlesViewModel.kt` | Äquivalent zu `ArticlesViewModel.swift` (Abschnitt 8): zentraler State-Holder für Artikelliste/Filter/Suche/Counts/Tags. Optimistische Updates für `toggleFavorite`/`toggleArchive`/`delete`/`setTags` mit PUT-dann-GET-Pattern (wie `MerlinAPI.swift`) und Offline-Queue-Fallback bei `IOException` (echte Verbindungsfehler) vs. Rollback bei echten Serverfehlern. `UndoableAction` mit `description`/`promptDescription` plus `lastUndoableAction`/`undo()`/`undoToast` als StateFlows, da Android keine Shake-Geste hat (Snackbar-Pattern statt iOS' Shake-to-Undo). 2s-Polling-Loop statt SSE für `isProcessing`-Artikel (SSE-Endpoint hält Verbindungen bis 55s offen). Exponiert zusätzlich `val imageLoader: ImageLoader` für `ArticleCard`/`ArticleThumbnail` (Abschnitt 9). `addArticle(url, tagIds, pendingTagNames)` erweitert um `pendingTagNames`: legt unbekannte Tag-Namen über `resolveTagIds()`/`api.createTag()` an, bevor der Artikel erstellt wird (Äquivalent `MerlinAPI.resolveTagIds(for:)`, genutzt von `AddArticleSheet`). `clearExcludedTags()` (Äquivalent iOS' `excludedTagIds.removeAll()`) leert `excludedTagIds` und persistiert das über `PreferencesStore.setExcludedTagIds`, genutzt von `TagFilterSheet`. **`applyExternalUpdate(article)`/`removeExternally(articleId)`**: Sync-Punkt für Mutationen aus dem Reader (`ArticleReaderViewModel`, eigene unabhängige `_article`-Instanz) – ohne diese beiden Methoden blieb z.B. ein im Reader-Drawer archivierter Artikel bis zum nächsten `load()` fälschlich in „Ungelesen" sichtbar. `MainActivity`s `reader/{articleId}`-Route verdrahtet sie auf `ArticleReaderScreen`s `onArticleChanged`/`onArticleDeleted`-Callbacks (dieselbe geteilte `ArticlesViewModel`-Instanz wie für `onNavigateNext`) – bewusst Compose-Callback-Kopplung statt direkter ViewModel-zu-ViewModel-Referenz |

## UI (`ui/screens/`)

| Datei | Zweck |
|---|---|
| `OnboardingScreen.kt` | Server-URL-Eingabe + „Anmelden"-Button; öffnet die vom Server gelieferte `loginUrl` per Custom Tabs (`androidx.browser`) im System-Browser, Merlin selbst sieht das Nextcloud-Passwort nie. Nach erfolgreichem Polling ruft es `onLoginSuccess` auf |
| `ArticleListScreen.kt` | Äquivalent zur iOS-Artikelliste (Abschnitt 9, Teil 1): `Scaffold` mit `TopAppBar` (Suche-Toggle), Filter-Chips aus `ArticleFilter.entries`, Offline-/Fehler-Banner, Inhalt in `PullToRefreshBox` (`isRefreshing = isLoading`, `onRefresh = { viewModel.load() }`) – Äquivalent zu SwiftUIs `.refreshable`; kein dauerhafter Refresh-IconButton mehr in der TopAppBar (iOS hat ebenfalls keinen), stattdessen ein „Aktualisieren“-Button im Empty-State (Äquivalent `ArticleListView.swift`s `emptyView`-Button), `LazyColumn` über `viewModel.filteredArticles`, `SnackbarHost` verdrahtet mit `lastUndoableAction`/`undoToast` (Undo-Action ruft `viewModel.undo()`) |
| `ArticleCard.kt` | Äquivalent zu `ArticleRowView.swift`: `ArticleThumbnail` (72×54dp) links, Titel (2-zeilig)/Processing-Spinner, Site-Name + Lesezeit + Favoriten-Indikator, Tag-Chips, sichtbarer Favoriten-Toggle-Button, Overflow-`DropdownMenu` (Archivieren/Tags bearbeiten/Löschen) – wie im iOS-Original (das parallel `.swipeActions` UND `.contextMenu` anbietet) zusätzlich in eine `SwipeActionsRow` gewrappt: Trailing Löschen→Archiv/Unarchiv→Favorit/Unfavorit (konditional über `isArchived`/`isFavorite`/`showArchiveAction`/`showFavoriteAction`), Leading Teilen (`Intent.ACTION_SEND`). Neue Parameter `activeSwipeKey: MutableState<Any?>`, `showFavoriteAction`/`showArchiveAction: Boolean = true` |
| `ArticleThumbnail.kt` | Äquivalent zu `CachedAsyncImage`/`NoImageView.swift`: `AsyncImage` mit dem geteilten Hilt-`ImageLoader` (`RefererInterceptor` + Coil-Disk-Cache, siehe `ImageModule`) – Cache-First-Verhalten kommt transparent von Coil, kein eigenes Disk-Lookup wie im iOS-Original nötig. Bei `null`/leerem `imageUrl` oder `onError` ein Icon-Platzhalter (`Icons.Filled.Image` auf `surfaceVariant`) statt eines eigenen `no-img`-Drawable-Assets. `ArticlesViewModel` exponiert dafür `val imageLoader: ImageLoader` (gleiches Konstruktor-Pattern wie `ArticleReaderViewModel`) |
| `EditTagsDialog.kt` | Tag-Editor als `ModalBottomSheet` (Äquivalent zu iOS' `ArticleTagSheet`): Chip-Auswahl aller bekannten Tags plus inline-Erstellung neuer Tags (gleiches `pendingTags`-Konzept wie `AddArticleSheet`). Bewusst ViewModel-agnostisch über einen `onSave`-Suspend-Lambda statt direkter Injection – wird sowohl aus `ArticleListScreen` (`ArticlesViewModel.saveTags`) als auch aus dem Reader-Drawer (`ArticleReaderViewModel.saveTags`) heraus geöffnet |

`MainActivity.kt` schaltet anhand `CredentialsStore.isConfigured` zwischen `OnboardingScreen`/`NavHost` um; innerhalb des `NavHost` führt `ArticleListScreen.onArticleClick` zur Route `reader/{articleId}`, `ArticleListScreen.onRemindersClick` (Glocken-Icon in der TopAppBar) zur Route `reminders`, `ArticleListScreen.onSettingsClick` (Zahnrad-Icon in der TopAppBar) zur Route `settings`.

**„Nächster Artikel"-Verdrahtung** (Äquivalent zu iOS' `onNavigateNext`-Closure aus `ArticleListView.swift`, die der Reader selbst nicht kennt): die `reader/{articleId}`-Route holt sich per `hiltViewModel(navController.getBackStackEntry("list"))` dieselbe `ArticlesViewModel`-Instanz wie die Liste (bleibt im Back-Stack, da nie ersetzt), liest `filteredArticles`, ermittelt den Index des aktuellen Artikels und reicht – falls ein Nachfolger existiert – eine Navigations-Lambda als `onNavigateNext` durch, die den aktuellen Reader-Eintrag per `popUpTo(...) { inclusive = true }` ersetzt statt zu stapeln.

## Swipe-Actions (`ui/components/SwipeActionsRow.kt`)

Generische, wiederverwendbare Custom-Swipe-Geste, da Material3 `SwipeToDismissBox` nur eine Hintergrund-Aktion pro Richtung unterstützt, das iOS-Original aber bis zu 3 Trailing- + 1 Leading-Aktion zeigt. 1:1-Port der handgebauten Drag-Geste aus `ArticleCardView.swift` (die Grid-Variante – SwiftUIs native `.swipeActions` funktioniert nur innerhalb von `List`, nicht `LazyVGrid`, daher dort ebenfalls Custom-Code).

| Datei | Zweck |
|---|---|
| `ui/components/SwipeActionsRow.kt` | `SwipeAction`-Datenklasse (Icon/Label/Farbe/Callback) + `SwipeActionsRow(swipeKey, activeSwipeKey, leadingAction, trailingActions, content)`. Horizontaler `Modifier.draggable` auf einem `Animatable<Float>`-Offset; Konstanten 1:1 aus iOS übernommen (`ActionWidth=72dp`, `SNAP_DIST_DP=55`, `SHARE_SNAP_DIST_DP=100`, `CLOSE_DIST_DP=20`), Snap/Close-Animation via `SnapSpring` (`dampingRatio=0.8, stiffness=StiffnessMediumLow`, Äquivalent zu iOS' `withAnimation(.spring(response: 0.3, dampingFraction: 0.8))`). `activeSwipeKey: MutableState<Any?>` wird als geteilte Instanz aus `ArticleListScreen` durchgereicht (Äquivalent zu iOS' `@Binding var activeSwipeId: Int?`) – per `LaunchedEffect(activeSwipeKey.value)` schließt sich jede Karte automatisch, sobald eine andere aktiv wird, sodass nie zwei Reihen gleichzeitig offen sind. Aktions-Pills (`ActionPill`) sind volle Höhe + Farbfläche statt iOS' runder 44pt-Pillen – bewusste Material-Anpassung, kein 1:1-visueller Port. **Bounce-Close bei offenem Swipe**: eine transparente Overlay-`Box` (`pointerInput`/`detectTapGestures`) über `content` fängt Taps ab, solange `offsetX != 0`, und federt über `BounceCloseSpring` (`dampingRatio=0.42, stiffness=247`, Äquivalent zu iOS' `response: 0.4, dampingFraction: 0.42`) zu – Äquivalent zu iOS' `cardBody.onTapGesture { ... bounceClose() }`; verhindert, dass ein Tap bei offener Reihe versehentlich zur Card-`onClick`-Navigation durchgereicht wird. |

## Erinnerungs-Übersicht (`ui/screens/RemindersScreen.kt`, `viewmodel/RemindersViewModel.kt`)

Teil von Abschnitt 9 aus `todo.md` – Äquivalent zu `RemindersView.swift`.

| Datei | Zweck |
|---|---|
| `viewmodel/RemindersViewModel.kt` | `@HiltViewModel`, lädt `ReminderService.all()` (bereits PENDING-gefiltert/sortiert) in `reminders: StateFlow<List<Reminder>>`; `delete()` entfernt optimistisch aus der Liste und ruft `ReminderService.cancel(articleId)` – kein Undo/Offline-Queue-Pattern nötig, da `cancel` rein lokal ist (Room + `AlarmManager`, kein Server-Request) |
| `ui/screens/RemindersScreen.kt` | `Scaffold` mit `TopAppBar` (Zurück), Empty-State (Glocken-Icon + Hinweistext analog iOS' `ContentUnavailableView`), `LazyColumn` mit `SwipeToDismissBox` pro Zeile (Lösch-Hintergrund in beide Richtungen) als Äquivalent zu iOS' `List.onDelete`/`EditButton`; Zeile zeigt orangenes Glocken-Icon, Artikeltitel (aus `Reminder.articleTitle`, kein Re-Fetch des Artikels nötig) und Datum/Uhrzeit (`DateFormat.MEDIUM`/`SHORT`); Tap auf die Zeile navigiert zum Reader |

## Artikel hinzufügen (`ui/screens/AddArticleSheet.kt`)

Teil von Abschnitt 9 aus `todo.md` – Äquivalent zu `AddArticleSheet.swift`.

| Datei | Zweck |
|---|---|
| `ui/screens/AddArticleSheet.kt` | `ModalBottomSheet` (wie `ReminderSheet`/`ReportArticleSheet`, statt iOS' `NavigationStack`+`Form`-Sheet): URL-Textfeld (`KeyboardType.Uri`) plus Tag-Auswahl – bestehende Tags als `FilterChip`-Reihe (Toggle-Auswahl), neue Tag-Namen werden lokal als „pending“ Chips gesammelt (mit Lösch-Icon) und erst beim Speichern über `ArticlesViewModel.resolveTagIds()` serverseitig angelegt (Äquivalent iOS' `pendingTags`/`MerlinAPI.resolveTagIds(for:)`). Nutzt denselben `hiltViewModel()`-Default wie `ReportArticleSheet`, dadurch identische `ArticlesViewModel`-Instanz wie `ArticleListScreen` (gleiche NavBackStackEntry-Route) – kein eigener State für `allTags` nötig. Einstieg: neuer „+“-`IconButton` (erstes Icon, vor Suche) in `ArticleListScreen`s TopAppBar, `showAddSheet`-Flag steuert die Anzeige |

## Tag-Filter (`ui/screens/TagFilterSheet.kt`)

Teil von Abschnitt 9 aus `todo.md` – Äquivalent zu `TagFilterSheet.swift`.

| Datei | Zweck |
|---|---|
| `ui/screens/TagFilterSheet.kt` | `ModalBottomSheet` (wie `AddArticleSheet`/`ReminderSheet`, statt iOS' `NavigationStack`-Sheet mit `presentationDetents([.medium, .large])`): `LazyColumn` über alle Tags, Tap auf eine Zeile togglet Sichtbarkeit (`Visibility`/`VisibilityOff`-Icon), „Alle einblenden“-`TextButton` nur sichtbar bei aktivem Filter, Empty-State (`EmptyTagFilterState`) und Zähler-Zeile für ausgeblendete Tags. Anders als `AddArticleSheet` eine reine Präsentations-Komposable (Parameter `allTags`/`excludedTagIds`/`onToggle`/`onClearAll`/`onDismiss` statt direkter `ArticlesViewModel`-Injection, analog `EditTagsDialog`). Einstieg über neues Augen-Icon (`VisibilityOff`, mit `BadgedBox`/`Badge` für die Anzahl ausgeblendeter Tags) im `navigationIcon`-Slot der `ArticleListScreen`-TopAppBar (linke Seite, analog iOS' `.topBarLeading`), `showTagFilterSheet`-Flag steuert die Anzeige; `onToggle`/`onClearAll` verdrahtet auf `ArticlesViewModel.toggleTagExclusion`/`clearExcludedTags` |

## Highlights-Datenschicht (`data/`, `viewmodel/`)

Teil von Abschnitt 9 (Reader), analog zur Offline-Mutation-Queue aus Abschnitt 4.

| Datei | Zweck |
|---|---|
| `HighlightEntity.kt` / `HighlightDao.kt` | Room-Persistenz für `Highlight` pro Artikel |
| `HighlightCacheService.kt` | `replaceAll(highlights, articleId)`, `highlights(articleId)`, `remove(id, articleId)` – Äquivalent zum Artikel-Cache-Muster aus Abschnitt 4 |
| `PendingHighlightMutationEntity.kt` / `PendingHighlightMutationDao.kt` | Wartende Highlight-Create/Delete-Mutationen bei Offline-Erstellung |
| `OfflineHighlightQueue.kt` | Äquivalent zu `OfflineHighlightQueue.swift`: `enqueueCreate` liefert eine synthetische negative ID für optimistisches UI-Update, bis der Server eine echte ID zurückgibt |
| `HighlightMutationDrainWorker.kt` | `@HiltWorker`-`CoroutineWorker`, leert die Highlight-Queue analog zu `MutationDrainWorker.kt` |

## Reader (`ui/reader/`, `viewmodel/ArticleReaderViewModel.kt`)

Abschnitt 9, Teil 2 aus `todo.md` – Äquivalent zu `ArticleReaderView.swift`. **v1
bewusst ausgeklammert**, mittlerweile vollständig nachgezogen: `ReminderSheet`,
`ImageLightboxScreen` und `ReportArticleSheet`/`ReportService.kt` sind fertig
(siehe unten). Einzig das TTS-Panel bleibt zurückgestellt (Abschnitt 7,
eigener Task).

| Datei | Zweck |
|---|---|
| `viewmodel/ArticleReaderViewModel.kt` | `@HiltViewModel`, liest `articleId` aus `SavedStateHandle["articleId"]` (vom `NavHost`-Argument befüllt); lädt Artikel + Highlights + Reminder-Status, optimistisches Create/Delete von Highlights mit Offline-Queue-Fallback, Scroll-Position-Persistenz über `PreferencesStore`, `Appearance`-State (Theme/Font/Größe/Zeilenhöhe/Akzentfarbe/Fortschrittsbalken-Kante), `currentReminder`/`reminderError`/`scheduleReminder()`/`cancelReminder()` für `ReminderSheet`, geteilter Coil-`imageLoader` (mit `RefererInterceptor`) für `ImageLightboxScreen`. Eigenes, von `ArticlesViewModel` unabhängiges `allTags`/`loadTags()`/`saveTags()`/`resolveTagIds()` (Dedup gegen vorhandene Tag-Namen, gleiche Logik wie `ArticlesViewModel`) für den im Drawer geöffneten `EditTagsDialog` |
| `ui/reader/ReaderHtmlBuilder.kt` | Baut das Artikel-HTML inkl. eingebettetem `READER_JS` (Highlight-Erstellung per Selection-XPath, Tap-Erkennung auf Highlights/Bildern, Scroll-Postmessages) und themenabhängigem CSS aus `Appearance` |
| `ui/reader/ReaderJsBridge.kt` | `@JavascriptInterface`-Brücke (`MerlinHighlightBridge`): JS → Kotlin-Callbacks für Highlight-Erstellung/-Tap, Bild-Tap (öffnet `ImageLightboxScreen`), Text-Selection-Rect |
| `ui/reader/ReaderWebView.kt` | `AndroidView`-Wrapper um `android.webkit.WebView` (bewusst natives Scrollen statt iOS' `ScrollView`+ResizeObserver-Pattern – siehe Datei-Kommentar); Scroll-Restore mit Retry-Polling (Layout/Reflow nicht sofort final nach `onPageFinished`), live `onScrollProgress`-Callback für den Fortschrittsbalken, zusätzlicher `onScrollMetrics(offsetPx, scrollableRangePx)`-Callback mit rohen Pixelwerten für die Bottom-Bar-Sichtbarkeitslogik (siehe `ArticleReaderScreen.kt`), einmaliges Save-on-Dispose für die Persistenz. **Gesten-Konflikt-Fix**: `systemGestureExclusionRects` auf einem 32dp-Streifen am linken Rand (API 29+, via `addOnLayoutChangeListener` da `height` erst nach dem ersten Layout bekannt ist) reduziert die Kollision mit dem System-Edge-Swipe-Zurück (v.a. Samsung One UI) beim Scrollen nah am Rand – nur ein Hinweis ans System, kein hartes Override, siehe Datei-Kommentar |
| `ui/reader/AppearanceSheet.kt` | `ModalBottomSheet`: Theme/Schriftart/-größe/Zeilenhöhe/Akzentfarbe/Fortschrittsbalken-Kante, schreibt direkt über `viewModel.preferencesStore` |
| `ui/reader/ReminderSheet.kt` | Äquivalent zu `ReminderSheet.swift`. `ModalBottomSheet` mit Material3 `DatePickerDialog` + eigenem `TimePicker`-`AlertDialog` (sequenziell statt iOS' kombiniertem grafischem `DatePicker`, den es in Compose nicht gibt); Pre-Fill mit vorhandenem Reminder-Zeitpunkt, „Erinnerung entfernen“-Button bei bestehendem Reminder, Fehleranzeige aus `viewModel.reminderError`, lokaler `isSaving`-State sperrt die Buttons und schließt das Sheet nach erfolgreichem Speichern |
| `ui/reader/ImageLightboxScreen.kt` | Äquivalent zu `ImageLightboxView.swift`/`LightboxState`. Volle Parität als `Dialog` (Fullscreen statt eigener Nav-Route): endloses `HorizontalPager`-Karussell (Index-Multiplikator-Trick wie iOS' `TabView`-Tags), `ZoomableImage` mit Pinch-Zoom/Pan (`detectTransformGestures`) und Doppeltap-Zoom (`detectTapGestures`), vertikaler Drag-to-dismiss mit Backdrop-Fade/Skalierung über ein `Animatable`. Pinch-Zoom klemmt direkt auf `[1, 5]` statt wie iOS kurz darunter zuzulassen und zurückzuschnappen – einzige bewusste Vereinfachung. `HorizontalPager(userScrollEnabled = !isZoomed)` ersetzt iOS' `TabViewScrollEnabler`-UIKit-Hack |
| `ui/reader/ArticleReaderScreen.kt` | `Scaffold` mit `TopAppBar` (Zurück/Favorit/Hamburger) + `ModalNavigationDrawer` als „Side-Drawer mit voller Aktionsliste“ (Favorit/Erscheinungsbild/Teilen/Browser/archive.ph/Link kopieren/Archivieren/Tags/Erinnern/Artikel melden/Löschen); „Tags bearbeiten…“ öffnet `EditTagsDialog` (lädt vorher per `viewModel.loadTags()` die volle Tag-Liste, da der Reader sie sonst nicht braucht); Highlight-Farb-Toolbar bei aktiver Text-Selection, Highlight-Löschen per `AlertDialog` nach Tap auf bestehendes Highlight, `ProgressBarOverlay` für die vier Kanten, öffnet `ReminderSheet`/`ImageLightboxScreen`/`ReportArticleSheet`. **`ReaderBottomBar`** (Äquivalent zu iOS' `bottomBar` in `ArticleReaderView.swift`): 3 Icon-Buttons (Zurück / Archivieren+Zurück / Archivieren+Weiter) als Overlay am unteren Rand, per `AnimatedVisibility` (slide+fade, 200ms) ein-/ausgeblendet; Sichtbarkeitslogik 1:1 aus iOS übernommen (160dp-Bodennähe, 4dp-Delta-Debounce, 40dp-Mindest-Scroll-Offset vor Richtungs-Tracking) auf Basis von `ReaderWebView`s `onScrollMetrics`. Buttons 2/3 archivieren einseitig (`if (!isArchived) toggleArchive()`, nie un-archivieren) wie im iOS-Original; Button 3 nutzt den optionalen `onNavigateNext`-Parameter (`null` → abgedunkelt/deaktiviert) |
| `data/ReportService.kt` | Äquivalent zu `ReportService.swift`. `@Singleton`, Mutex-isoliert (Kotlin-Pendant zu Swifts `actor`). Cacht die per `MerlinApi.getSettings().reportBackendUrl` geladene Backend-URL; `report(url, comment)` postet JSON an `{backendUrl}?action=report` über einen eigenen, schlanken `OkHttpClient` **ohne** `BaseUrlInterceptor`/`AuthInterceptor` – der Hilt-weite Client ist fest auf den Nextcloud-Host verdrahtet und für das externe, unauthentifizierte merlin-reports-Backend ungeeignet. `ReportError`-Sealed-Class mit denselben deutschen Meldungen wie im iOS-Original |
| `ui/reader/ReportArticleSheet.kt` | Äquivalent zu `ReportArticleSheet.swift`. `ModalBottomSheet` (statt iOS' `.sheet`/`NavigationStack`+Toolbar) mit URL-Vorschau, optionalem Mehrzeilen-Kommentarfeld, Feedback-Text (Erfolg/Fehler aus `viewModel.reportFeedback`), Abbrechen/Melden/Schließen-Button-States passend zu `viewModel.reportSending`/`reportFeedback`; `sendReport()`/`clearReportFeedback()` in `ArticleReaderViewModel.kt` |

**Polish-Punkt (zurückgestellt):** anders als iOS (Bild-Vorab-Cache +
`loadFileURL`) lädt die `WebView` Artikelbilder direkt über ihren eigenen
Netzwerk-Stack statt aus Coils Disk-Cache – Artikelbilder im Reader sind
offline daher nicht garantiert verfügbar.

## Scroll-Position-Synchronisation (geräteübergreifend)

Anders als die rein lokale Persistenz früherer Stände wird die Leseposition
jetzt über den Nextcloud-Server zwischen Geräten (Android/iOS/Web) geteilt.

- **Wert:** synchronisiert wird der Fortschritt als **Fraktion 0..1**, nicht der
  Pixel-Offset (Pixel variieren mit Erscheinungsbild/Gerät). Wiederhergestellt
  wird über `ziel_px = fraktion × aktuelle_range`, im Retry-Loop neu gerechnet.
- **Konflikt:** Last-Write-Wins per Client-Zeitstempel (`scrollUpdatedAt`,
  Epoch-Millis). Beim Öffnen gewinnt lokal vs. Server der neuere Stand.
- **Server:** `merlin_articles.scroll_progress` + `scroll_updated_at` (Migration
  0014); `PUT /api/articles/{id}/progress`; Felder additiv im Article-JSON.
- **`data/PreferencesStore.kt`**: speichert lokal Fraktion (`pct_`) + Zeitstempel
  (`pctts_`); `savedScrollProgress`/`savedScrollTimestamp`/`saveScrollProgress`.
- **`viewmodel/ArticleReaderViewModel.kt`**: `initialScrollProgress: StateFlow<Float?>`
  wird nach dem Artikel-Load per `reconcileInitialScroll` (LWW) gesetzt;
  `saveScrollProgress(progress)` schreibt lokal + pusht an den Server. Läuft im
  **`@ApplicationScope`-CoroutineScope** (`di/AppScopeModule.kt`), nicht im
  `viewModelScope`, da `onCleared()` beim Zurücknavigieren den async DataStore-
  Write/Push sonst abbricht (auf iOS kein Thema, da `UserDefaults` synchron).
- **`ui/reader/ReaderWebView.kt`**: `initialScrollProgress`-Fraktion statt
  Pixel-Offset; Restore gegen die aktuelle `verticalScrollRange()`.
- **Einschränkungen (v1):** kein Offline-Retry des Pushs (fire-and-forget, lokal
  bleibt erhalten); Uhr-Schieflage zwischen Geräten kann selten die „falsche"
  Seite gewinnen lassen. `resumeOnOpen`/`saveProgress` werden jetzt respektiert.

## Share-Funktionalität (`ui/share/`, `viewmodel/ShareViewModel.kt`)

Abschnitt 10 aus `todo.md` – Äquivalent zur `MerlinShare`-Extension (iOS).
**Architekturabweichung von iOS:** die Extension läuft dort in einem eigenen,
eingeschränkten Prozess und braucht daher eine Keychain-Access-Group plus
einen eigenen `URLSession`-Stack. `ShareActivity` ist dagegen eine normale
Activity im Haupt-App-Prozess – `CredentialsStore`/`MerlinApi` (Hilt-Singletons)
werden direkt injiziert, kein separater Netzwerk-Stack nötig. Für den
„noch nicht konfiguriert"-Fall nutzt Android außerdem den bestehenden
`OnboardingScreen` (Login Flow v2 per Custom Tabs) statt iOS' drei manuellen
Textfeldern (URL/Username/App-Passwort) – eine Extension kann keinen
Browser-Login starten, eine Activity kann das problemlos, und so sieht Merlin
das Nextcloud-Passwort nie direkt.

| Datei | Zweck |
|---|---|
| `ui/share/ShareActivity.kt` | Nimmt `ACTION_SEND`-Intents entgegen (`extractSharedText`: `EXTRA_TEXT`, Fallback `EXTRA_SUBJECT`), zeigt `ShareScreen` als halbtransparentes Overlay (`Color.Black.copy(alpha = 0.4f)`) – Äquivalent zum abgedunkelten Hintergrund im iOS-Original |
| `viewmodel/ShareViewModel.kt` | `Mode`-Enum (`ONBOARDING/EXTRACTING/STAGING/SAVING/SUCCESS/ERROR/RATE_LIMITED`) als Äquivalent zu den Settings/Staging/Saving/Error/Retry-Funktionen in `ShareViewController.swift`. `extractFirstUrl` per Regex (`https?://\S+`) statt `NSDataDetector`. `resolveTagIds` legt fehlende, neu eingetippte Tags per `createTag` an; `confirmSave` speichert den Artikel über `createArticle` mit `tagIds` direkt im Request-Body (kein Query-Param-Workaround wie auf iOS nötig) |
| `ui/share/ShareScreen.kt` | Mode-`when`-Dispatch: `OnboardingScreen` (Onboarding) / `LoadingCard` (Extracting/Saving/Success) / `StagingCard` (Tag-`FilterChip`-Auswahl per `LazyRow` + Textfeld für neue Tags) / `MessageCard` (Error/Rate-Limit). Auto-Dismiss-Timings analog iOS' `DispatchQueue.main.asyncAfter` (3s Error, 2.5s Rate-Limit zurück zu Staging, 600ms Success-Bestätigung vor dem Schließen) |

## Einstellungen (`ui/screens/SettingsScreen.kt`, `viewmodel/SettingsViewModel.kt`)

Letzter offener Punkt aus Abschnitt 9 in `todo.md` – Äquivalent zu `SettingsView.swift`.

| Datei | Zweck |
|---|---|
| `viewmodel/SettingsViewModel.kt` | `@HiltViewModel`. Login-Logik bewusst aus `OnboardingViewModel` dupliziert statt injiziert (ViewModels referenzieren keine anderen ViewModels) – beide kapseln denselben `LoginFlowService`, analog zu iOS' separaten `@StateObject`s in `OnboardingView`/`SettingsView`. Exponiert `preferencesStore` public (gleiches Pattern wie `ArticleReaderViewModel`, siehe `AppearanceSheet.kt`): UI liest dessen `Flow`s direkt per `collectAsState()`, schreibt aber über die Setter hier (`setDefaultFilter`/`setProgressEdge`/`setSaveProgress`/`setResumeOnOpen`/`setPrefetchWifiOnly`/`setDeveloperMode`), die bei sync-fähigen Feldern automatisch `syncPreferences()` anstoßen. `testConnection()` ruft mangels eigenem `/test`-Endpoint einfach `api.getSettings()` auf (gleiche Vereinfachung wie iOS' `MerlinAPI.testConnection()`). `syncPreferences()` ist ein einfaches `runCatching { api.updateSettings(...) }` – Android hat (noch) kein `SettingsSyncQueue`-Äquivalent für Offline-Retry, ein Fehlschlag wird daher bewusst verschluckt (lokaler Wert bleibt trotzdem gesetzt). `clearCache()` löscht Artikel-/Bild-/Highlight-Cache, Lesepositionen und Zugangsdaten – loggt den Nutzer dadurch implizit aus |
| `ui/screens/SettingsScreen.kt` | Eigene Route (`Scaffold` + Zurück-`TopAppBar`) statt iOS' `Form`/Sheet: Konto-Sektion (Server-URL-Feld, „Mit Nextcloud anmelden"-Button öffnet die `loginUrl` per Custom Tabs wie `OnboardingScreen`, Lade-/Erfolgs-/Fehleranzeige, „Abmelden" mit `AlertDialog`-Bestätigung), Verbindungstest-Sektion, Präferenzen (`FilterChip`-Reihen für `ArticleFilter`/`ProgressEdge`, `Switch` für `saveProgress`/`resumeOnOpen`), Cache-Sektion (`Switch` für WLAN-only-Vorladen, „Cache leeren" mit `AlertDialog`-Bestätigung), Über-Sektion (`BuildConfig.VERSION_NAME`), Entwickler-Sektion (`Switch` + Debug-Infos). `onLoggedOut`-Callback feuert per `LaunchedEffect(isConfigured)`, sobald `isConfigured` von `true` auf `false` wechselt (Logout/Cache leeren) |

Einstieg über neues Zahnrad-Icon in der `ArticleListScreen`-TopAppBar
(`onSettingsClick`); `MainActivity.kt` registriert die Route `settings` und
setzt bei `onLoggedOut` sein lokales `isConfigured` auf `false` zurück
(`popBackStack("list", inclusive = true)`), wodurch wieder der
`OnboardingScreen` angezeigt wird.

## Bewusst noch nicht angelegt

Siehe `todo.md` für die vollständige Liste; insbesondere fehlen noch: TTS-Pipeline (Media3) und der `OnboardingTour`-Screen (inkl. der UI-seitige Notification-Permission-Prompt), SSE-Client, ein `SettingsSyncQueue`-Äquivalent für Offline-Retry der Settings-Synchronisation.

## Bekannte offene Punkte für den ersten Android-Studio-Sync

- Gradle-Wrapper-Dateien (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) fehlen noch – Android Studio bietet beim ersten Öffnen an, sie zu erzeugen; das sollte bestätigt werden.
- `compileSdk`/`targetSdk 34` setzt voraus, dass die entsprechende SDK-Platform im SDK-Manager installiert ist.
- Kein Launcher-Icon hinterlegt (`mipmap`-Ressourcen fehlen) – Android Studio nutzt sonst ein Default-Icon; kein Blocker für den Build.
