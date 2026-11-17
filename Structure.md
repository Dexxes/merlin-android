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
| `app/src/main/AndroidManifest.xml` | Permissions (Internet, Notifications, Exact Alarm), `android:icon`/`android:roundIcon` → `@mipmap/ic_launcher`/`ic_launcher_round` (siehe „App-Icon" unten), `MainActivity` als Launcher; `ShareActivity` mit `ACTION_SEND`/`ACTION_SEND_MULTIPLE`-Intent-Filter für `*/*` (Abschnitt 10), `excludeFromRecents`/`noHistory` da reine Overlay-Activity, transparentes `Theme.Merlin.Share` |

## Lokalisierung (`res/values(-de)/strings_i18n.xml`)

Die App gibt es auf **Englisch und Deutsch**. Englisch liegt im Default-Ordner
`res/values/`, Deutsch in `res/values-de/`. Android wählt daher Deutsch, wenn
das Gerät (bzw. ab Android 13 die Spracheinstellung pro App) auf Deutsch steht,
und fällt bei jeder anderen Sprache (Französisch, Spanisch, …) auf Englisch
zurück.

`merlin-translations` ist die zentrale Quelle für alle UI-Strings
(`<lang>.json`, siehe `schema.md` dort). `python3 export.py --platform android`
(im Ordner `merlin-translations`, mit `merlin-android` daneben) generiert daraus
`res/values/strings_i18n.xml` (Quellsprache `en`) und
`res/values-de/strings_i18n.xml` – eigene Datei statt Einträge in der
bestehenden `strings.xml`, damit die dort von Hand gepflegte `app_name`-
Ressource unangetastet bleibt (der Exporter lässt `app.name` deshalb aus). Nicht
direkt editieren, Änderungen gehen beim nächsten Export verloren – stattdessen
`<lang>.json` in `merlin-translations` anpassen und neu exportieren.

- **Namen:** Dot-Key mit `_` (`settings.account.loginButton` →
  `R.string.settings_account_loginButton`).
- **Namespaces:** exportiert werden alle App-Keys; `webext.*`/`nextcloudWeb.*`/
  `merlinServer.*` nicht. Texte, die es nur auf Android gibt (Android-eigene
  Bedienelemente, Fehlermeldungen der ViewModels, …), stehen unter `android.*`
  und werden umgekehrt nicht nach iOS exportiert.
- **Platzhalter:** positionell (`%1$s`, `%2$d`), Reihenfolge nach dem ersten
  Auftreten im englischen Text; `{count}`/`{code}` sind Integer, alles andere
  `%s`.
- **Plurals:** `<plurals>` + `pluralStringResource()`.

**Im Code:** Compose nutzt `stringResource()`/`pluralStringResource()`.
ViewModels bekommen `@ApplicationContext` und lösen über `context.getString()`
auf. Modelle/Enums (z. B. `ArticleFilter.label`, `ImageCompression.titleRes`,
`SwipeAction.label`, `CommentApiException.userMessage`) tragen `@StringRes`-IDs
statt fertiger Texte. Fehler aus Services/Interceptoren, die Nutzer sehen
können, implementieren `LocalizedError` (`data/LocalizedError.kt`) und werden
mit `Throwable.userMessage(context)` übersetzt; `Exception.message` bleibt
englisch für Logs. `ReaderHtmlBuilder` (ohne Context) bekommt seine Texte als
`ReaderTexts`/`SupportBoxTexts` vom `ReaderWebView`.

**Datums-/Zahlenformate:** `AppLocale.of(context)` bzw. `appLocale()`
(`ui/AppLocale.kt`) liefert die Locale passend zur *angezeigten* Sprache (über
den Key `android.localeTag`): spricht das Gerät dieselbe Sprache, bleibt dessen
Region erhalten (en-GB, de-AT), sonst gilt die App-Sprache – damit auf einem
französischen Gerät kein „17 nov.“ in einem englischen Satz steht. Genutzt für
Erinnerungen (Sheet + Liste, 12/24-h nach Geräteeinstellung), Ablaufdatum des
Freigabelinks und das Kurzdatum im Reader-Header.

**Sprachkonfiguration:** `res/xml/locales_config.xml` (im Manifest als
`android:localeConfig`) meldet `en`/`de` für die App-Spracheinstellung ab
Android 13. `androidResources.localeFilters = en, de` in `app/build.gradle.kts`
wirft die Übersetzungen der Bibliotheken für andere Sprachen raus, damit
System-Buttons (Date-/TimePicker) zur App-Sprache passen.

Bewusst nicht übersetzt: der Eigenname „Nextcloud“ im Backend-Umschalter und
die Werte der Entwickler-Sektion in den Einstellungen.

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
| `MerlinApp.kt` | `Application`-Klasse mit `@HiltAndroidApp`; Äquivalent zu `MerlinApp.swift` (Splash-Logik liegt in `MainActivity.kt`; iOS-spezifische Credentials-Migration entfällt) |
| `MainActivity.kt` | Einzige Activity, hostet Compose; zeigt `OnboardingScreen` solange `CredentialsStore.isConfigured == false`, sonst ein `NavHost` (Routen `list`/`reader/{articleId}`, Äquivalent zu `NavigationStack`) zwischen `ArticleListScreen` und `ArticleReaderScreen`. `articleId` als typisiertes Nav-Argument, damit `hiltViewModel()` die `SavedStateHandle` von `ArticleReaderViewModel` automatisch befüllt. `AppNavigator.articleIdToOpen` (Reminder-Deep-Link) wird per `LaunchedEffect` konsumiert und navigiert direkt zum Reader. Splash wie iOS' `SplashView`: der System-Splash (`Theme.Merlin.Starting`) zeigt das Logo über `drawable/splash_icon.xml` auf 96 dp verkleinert, damit die kreisförmige Icon-Maske der SplashScreen-API nichts abschneidet; danach übernimmt `ui/screens/MerlinSplash.kt` mit dem Logo an gleicher Stelle, Ladeindikator und nach 3 s „Im Offlinemodus starten?“ (`ArticlesViewModel.continueOffline()`); er verschwindet nach mind. 200 ms, sobald Server-Settings und erster Ladevorgang durch sind |
| `ui/theme/Theme.kt` | `MerlinTheme`-Composable mit Platzhalter Light/Dark-Farbschema; wird mit der Settings-Portierung um Theme-Auswahl (light/dark/auto) erweitert |

## Models (`models/`)

1:1-Übertragung von `merlin-ios/Sources/Merlin/Models/`, als `kotlinx.serialization`-`@Serializable data class`:

| Datei | Zweck |
|---|---|
| `Article.kt` | Wie iOS-Original inkl. `displayTitle`/`displaySiteName`/`faviconUrl`/`isPdf` (Kategorie `PDF` oder URL endet auf `.pdf`)/`pdfSourceUrl` (bei Datei-Einträgen `data-pdf-src` aus dem Inhalt, sonst `url`); eigene `equals`/`hashCode` (id + isProcessing + updatedAt + sortierte Tag-IDs, `equals` zusätzlich Titel wegen Umbenennen) statt Default-Datenklassen-Vergleich, aus demselben Grund wie im iOS-Original (Recomposition bei Server-Updates) |
| `Tag.kt` | 1:1, inkl. `parentId` (verschachtelte Tags, nur merlin-nextcloud; fehlt das Feld, sind alle Tags oberste Ebene) |
| `InstalledFonts.kt` | Kein iOS-Gegenstück als Model (dort `UIFont.familyNames`): listet installierte Schriftfamilien – ab API 29 über `SystemFonts.getAvailableFonts()`, darunter aus `/system/fonts`/`/product/fonts`; Familienname/Gewicht/Kursiv aus `name`-/`OS/2`-Tabelle der Datei. Nur Schriften mit lateinischen Grundzeichen, aus `.ttc` nur der erste Schnitt. Gecacht, Aufruf nur im Hintergrund |
| `TagTree.kt` | Portierung von `TagTree.swift`: Baum über der flachen Tag-Liste – `rows(collapsed)` in Baumreihenfolge mit Tiefe, `scope`/`descendantIds`/`ancestorIds`, `toggling`/`selecting` (Unter-Tag wählt Eltern mit, Abwählen nimmt Unter-Tags mit), `path` („Reisen › Japan“), `canMove` (kein Kreis). Tags mit fehlendem Eltern-Tag gelten als oberste Ebene |
| `SupportBox.kt` | Daten der Support-Infobox (`siteName`, `subscribeUrl`, `donationsUrl`, `accentColor`, `iconUrl`) aus `GET /api/articles/{id}`; `Article.supportBox` (nur Einzelabruf, wird von `ArticleCacheService.upsert` bewusst nie in Room geschrieben – der Login-Status kann sich ändern, Äquivalent zu `Article.encode` in iOS) |
| `Highlight.kt` | `Highlight` (inkl. Verfasser, `authorColor`, PDF-Felder) + `HighlightCreate` (Request-Payload ohne Server-Felder) + `HighlightUpdate` (`PUT api/highlights/{id}`) |
| `Comment.kt` | `Comment`, `CommentsPayload`, `CommentAnchor` (Text-XPaths oder PDF-Seite/Rechtecke), Requests und `CommentApiException` |
| `Reminder.kt` | `triggerAt`/`createdAt` als Epoch-Millis statt `Date` (passt direkt zu `AlarmManager`/`WorkManager`); `id` als String-UUID statt `UUID`-Typ wegen Room-Kompatibilität |

## Netzwerk-Layer (`network/`)

| Datei | Zweck |
|---|---|
| `MerlinApi.kt` | Retrofit-Interface auf Basis von `merlin-api.yaml`: Artikel-CRUD/-Suche/-Counts, Read/Favorite/Archive-Toggle, Tags-CRUD (inkl. `moveTag` über `parentId`, `deleteTag` mit `deletedIds`), Tag-Zuordnung, `renameFile` (`PUT /api/articles/{id}/file-name`), Settings, `/video-stream` (ARD/ZDF/3sat/Arte-Stream-Auflösung, siehe `ui/reader/NativeVideoPlayerCard.kt`), Paywall-Site-Zugangsdaten (`/api/user/site-credentials`, siehe eigener Abschnitt unten). SSE-Stream (`articleUpdateStream`) bewusst ausgeklammert – eigener Task |
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
| `PdfCacheService.kt` | Äquivalent zu `PDFCacheService.swift`: lädt die PDF eines PDF-Artikels von der Quell-URL mit eigenem, auth-freiem `OkHttpClient` (nur `RefererInterceptor`; der Retrofit-Client würde Host umschreiben und Zugangsdaten senden) nach `cacheDir/pdf/<sha256>.pdf` (Status-, `%PDF-`- und 100-MB-Prüfung); `prune(days)` beim Start, `remove(url)` beim Löschen, `clear()` aus den Einstellungen |
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
| `ArticlesViewModel.kt` | Äquivalent zu `ArticlesViewModel.swift` (Abschnitt 8): zentraler State-Holder für Artikelliste/Filter/Suche/Counts/Tags. Optimistische Updates für `toggleFavorite`/`toggleArchive`/`delete`/`setTags` mit PUT-dann-GET-Pattern (wie `MerlinAPI.swift`) und Offline-Queue-Fallback bei `IOException` (echte Verbindungsfehler) vs. Rollback bei echten Serverfehlern. `UndoableAction` mit `description`/`promptDescription` plus `lastUndoableAction`/`undo()`/`undoToast` als StateFlows, da Android keine Shake-Geste hat (Snackbar-Pattern statt iOS' Shake-to-Undo). 2s-Polling-Loop statt SSE für `isProcessing`-Artikel (SSE-Endpoint hält Verbindungen bis 55s offen). Exponiert zusätzlich `val imageLoader: ImageLoader` für `ArticleCard`/`ArticleThumbnail` (Abschnitt 9). `addArticle(url, tagIds, pendingTagNames)` erweitert um `pendingTagNames`: legt unbekannte Tag-Namen über `resolveTagIds()`/`api.createTag()` an, bevor der Artikel erstellt wird (Äquivalent `MerlinAPI.resolveTagIds(for:)`, genutzt von `AddArticleSheet`). `clearExcludedTags()` (Äquivalent iOS' `excludedTagIds.removeAll()`) leert `excludedTagIds` und persistiert das über `PreferencesStore.setExcludedTagIds`, genutzt von `TagFilterSheet`. **`applyExternalUpdate(article)`/`removeExternally(articleId)`**: Sync-Punkt für Mutationen aus dem Reader (`ArticleReaderViewModel`, eigene unabhängige `_article`-Instanz) – ohne diese beiden Methoden blieb z.B. ein im Reader-Drawer archivierter Artikel bis zum nächsten `load()` fälschlich in „Ungelesen" sichtbar. `MainActivity`s `reader/{articleId}`-Route verdrahtet sie auf `ArticleReaderScreen`s `onArticleChanged`/`onArticleDeleted`-Callbacks – bewusst Compose-Callback-Kopplung statt direkter ViewModel-zu-ViewModel-Referenz |

## UI (`ui/screens/`)

| Datei | Zweck |
|---|---|
| `OnboardingScreen.kt` | Server-URL-Eingabe + „Anmelden"-Button; öffnet die vom Server gelieferte `loginUrl` per Custom Tabs (`androidx.browser`) im System-Browser, Merlin selbst sieht das Nextcloud-Passwort nie. Nach erfolgreichem Polling ruft es `onLoginSuccess` auf |
| `ArticleListScreen.kt` | Äquivalent zu `ArticleListView.swift`: `ModalNavigationDrawer` mit `ListDrawerContent` (Seitenmenü, öffnet über den Menü-Button links – der linke Rand gehört auf Android der Zurück-Geste), `CenterAlignedTopAppBar` mit Logo + Name der Ansicht bzw. des Tags, rechts Augen-Icon (Tag-Filter bzw. Archiv-Sichtbarkeit in der Tag-Ansicht) und „+“. Suchfeld hinter dem Lupen-Icon neben dem Menü-Button, Offline-/Fehler-Banner, `PullToRefreshBox`, `LazyColumn` mit `ArticleGridCard` (Kartenansicht) bzw. `ArticleRow` + Trennlinie (Listenansicht), Nachlade-Footer (Spinner bzw. „Ältere Artikel laden“), filterabhängiger Leer-Zustand. Lesefortschritt je Zeile per Last-Write-Wins aus `ArticlesViewModel.localProgress` und `Article.scrollProgress`. `SnackbarHost` mit `lastUndoableAction`/`undoToast` |
| `ArticleCard.kt` | `ArticleRow` (Äquivalent `ArticleRowView.swift`): schlichte Zeile, Vorschaubild rechts, Status-Symbole (Verarbeitung, Schloss bei Login/Paywall, nicht unterstützte Seite), Lesefortschritt als Ring + %, „Archiviert“-Markierung + Abblenden in gemischten Listen, `SwipeActionsRow` mit Teilen links und Löschen→Favorit→Archiv rechts (Archiv ganz außen wie iOS). `ArticleGridCard` (Äquivalent `ArticleCardView.swift`): 16:9-Hero mit Fortschrittsbalken und „Archiviert“-Plakette, ohne Wischgesten. Beide ohne sichtbaren Stern-/⋮-Button; Aktionen über langes Drücken (`ArticleContextMenu`: Favorit, Archiv/Als ungelesen markieren, Tags, Link kopieren, Teilen, Löschen) |
| `ListDrawer.kt` | `ListDrawerContent`, Äquivalent zu `ListFlyoutModifier.swift`: Tabs Text/Video/Audio (`SingleChoiceSegmentedButtonRow`), darunter die vier Ansichten der Medienart mit Zählern aus `ArticleCounts` (ohne Zähler bei Weiterlesen/-schauen/-hören), ausklappbare Tag-Liste (Tap = Einzel-Tag-Ansicht, „Tag-Filter aufheben“), Listen-/Kartenansicht, Erinnerungen, Einstellungen, Merlin-Logo unten. Außerdem `ArticleFilter.icon`. Tab „Dateien“ (nur merlin-nextcloud) mit „Nicht geöffnet“/Favoriten/Archiv. Tags als Baum (`TagTreeDrawerRow`): Pfeil klappt Unter-Tags auf/zu (`ArticlesViewModel.expandedTagIds`, persistiert), langes Drücken öffnet „Verschieben nach…“ (nur merlin-nextcloud) und „Tag löschen“ |
| `ArticleThumbnail.kt` | Äquivalent zu `CachedAsyncImage`/`NoImageView.swift`: `AsyncImage` mit dem geteilten Hilt-`ImageLoader` (`RefererInterceptor` + Coil-Disk-Cache, siehe `ImageModule`) – Cache-First-Verhalten kommt transparent von Coil, kein eigenes Disk-Lookup wie im iOS-Original nötig. Bei `null`/leerem `imageUrl` oder `onError` ein Icon-Platzhalter (`Icons.Filled.Image` auf `surfaceVariant`) statt eines eigenen `no-img`-Drawable-Assets. `ArticlesViewModel` exponiert dafür `val imageLoader: ImageLoader` (gleiches Konstruktor-Pattern wie `ArticleReaderViewModel`) |
| `EditTagsDialog.kt` | Tag-Editor als `ModalBottomSheet` (Äquivalent zu iOS' `ArticleTagSheet`): Chip-Auswahl aller bekannten Tags plus inline-Erstellung neuer Tags (gleiches `pendingTags`-Konzept wie `AddArticleSheet`). Bewusst ViewModel-agnostisch über einen `onSave`-Suspend-Lambda statt direkter Injection – wird sowohl aus `ArticleListScreen` (`ArticlesViewModel.saveTags`) als auch aus dem Reader-Drawer (`ArticleReaderViewModel.saveTags`) heraus geöffnet. Tags als Baum (`TagTreeSelectionList`), neue Tags optional unter einem Eltern-Tag (`TagParentPicker`, nur merlin-nextcloud) |
| `TagTreeSelection.kt` | `TagTreeSelectionList` (Äquivalent `TagTreeSelectionList.swift`, Auswahl folgt `TagTree.toggling`) und `TagParentPicker` („Unter“ für neue Tags), genutzt von `EditTagsDialog` und `AddArticleSheet` |
| `MoveTagSheet.kt` | Äquivalent `MoveTagSheet.swift`: neuer Eltern-Tag oder „Oberste Ebene“; der Tag selbst und seine Unter-Tags stehen nicht zur Wahl |
| `RenameDialogs.kt` | `RenameFileDialog`, Äquivalent `RenameFileAlert.swift`: Name ohne Endung, Hinweis „Die Endung .xyz bleibt erhalten.“, Fehlermeldung bei 409; genutzt von Liste/Karte (`ArticlesViewModel.renameFile`) und Reader-Menü (`ArticleReaderViewModel.renameFile`); außerdem `RenameTagDialog` (langes Drücken auf einen Tag im Seitenmenü, `ArticlesViewModel.renameTag`) |

`MainActivity.kt` schaltet anhand `CredentialsStore.isConfigured` zwischen `OnboardingScreen`/`NavHost` um; innerhalb des `NavHost` führt `ArticleListScreen.onArticleClick` zur Route `reader/{articleId}`, `ArticleListScreen.onRemindersClick` (Seitenmenü-Eintrag „Erinnerungen“) zur Route `reminders`, `ArticleListScreen.onSettingsClick` (Seitenmenü-Eintrag „Einstellungen“) zur Route `settings`.

**Reader-Verdrahtung**: die `reader/{articleId}`-Route holt sich per `hiltViewModel(navController.getBackStackEntry("list"))` dieselbe `ArticlesViewModel`-Instanz wie die Liste (bleibt im Back-Stack, da nie ersetzt), um Reader-Änderungen über `onArticleChanged`/`onArticleDeleted` sofort in der Liste widerzuspiegeln. Die frühere „Nächster Artikel"-Verdrahtung (`onNavigateNext`) ist mit dem Entfernen des „Archivieren und weiter"-Buttons entfallen.

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
| `ui/reader/ReaderHtmlBuilder.kt` | Baut das Artikel-HTML inkl. eingebettetem `READER_JS` (Highlight-Erstellung per Selection-XPath, Markierungen per `MerlinReader.setHighlights` (Kommentarstellen unterstrichen mit Zähler, Reihenfolge nach id wie iOS), Tap-Erkennung auf Highlights/Bildern, Scroll-Postmessages) und themenabhängigem CSS aus `Appearance` |
| `ui/reader/ReaderJsBridge.kt` | `@JavascriptInterface`-Brücke (`MerlinHighlightBridge`): JS → Kotlin-Callbacks für Highlight-Erstellung/-Tap, Bild-Tap (öffnet `ImageLightboxScreen`), Text-Selection-Rect, `onOpenExternalLink` (Links der Support-Infobox; nur http(s), Öffnen per `ACTION_VIEW` in `ReaderWebView`) |
| `ui/reader/PdfReaderView.kt` | Reader-Inhalt für PDF-Artikel (`Article.isPdf`): lädt über `ArticleReaderViewModel.loadPdf` (→ `PdfCacheService`) und zeigt die Datei mit dem mitgelieferten pdf.js (`assets/pdfjs`, Version in `VERSION`) in einer WebView (`assets/pdfreader/index.html`, `reader.js`, `reader.css`). `shouldInterceptRequest` liefert nur die Assets und die PDF unter `/document.pdf` (Host `appassets.androidplatform.net`), alles andere 403. Bridge `MerlinPdfBridge` (Auswahl, Markierung anlegen, Kommentar-Anker, Tipp auf Markierung, Suchtreffer, Links nur http(s)); `PdfDocumentActions` steuert `window.MerlinPdf`. Markierungen als `pdfPage` + `pdfRects` (normiert auf die unrotierte CropBox, oben links). Pinch-Zoom bis 5x, sichtbare Seiten werden nach dem Zoomen schärfer gerendert. Lade-, Fehler- und Passwortschutz-Zustand wie vorher |
| `ui/reader/ReaderDocumentActions.kt` | Gemeinsame Schnittstelle für Farbleiste, Kommentar-Sheet und Suchleiste: `WebViewDocumentActions` (Text, `MerlinReader` + `findAllAsync`) bzw. `PdfDocumentActions` (PDF) |
| `ui/reader/CommentsSheet.kt` | Äquivalent zu `CommentsSheet.swift`: Threads mit Antworten, Bearbeiten, Löschen, Sortierung (SharedPreferences `merlin_comments`), Fokus auf eine Markierung oder eine frische Auswahl (`CommentFocus`) |
| `viewmodel/CommentStore.kt` / `network/CommentStreamClient.kt` | Äquivalent zu `CommentStore.swift`: Kommentare und alle Markierungen eines Artikels, live über den SSE-Kanal `api/articles/{id}/comments/stream` (nur merlin-nextcloud, 404 = nicht unterstützt), ältere Stände per `generatedAt` verworfen |
| `ui/reader/ReaderWebView.kt` | `AndroidView`-Wrapper um `android.webkit.WebView` (bewusst natives Scrollen statt iOS' `ScrollView`+ResizeObserver-Pattern – siehe Datei-Kommentar); Scroll-Restore mit Retry-Polling (Layout/Reflow nicht sofort final nach `onPageFinished`), live `onScrollProgress`-Callback für den Fortschrittsbalken, zusätzlicher `onScrollMetrics(offsetPx, scrollableRangePx)`-Callback mit rohen Pixelwerten für die Bottom-Bar-Sichtbarkeitslogik (siehe `ArticleReaderScreen.kt`), einmaliges Save-on-Dispose für die Persistenz. **Gesten-Konflikt-Fix**: `systemGestureExclusionRects` auf einem 32dp-Streifen am linken Rand (API 29+, via `addOnLayoutChangeListener` da `height` erst nach dem ersten Layout bekannt ist) reduziert die Kollision mit dem System-Edge-Swipe-Zurück (v.a. Samsung One UI) beim Scrollen nah am Rand – nur ein Hinweis ans System, kein hartes Override, siehe Datei-Kommentar |
| `ui/reader/AppearanceSheet.kt` | `ModalBottomSheet` (scrollbar): Theme/Schriftart (4 Voreinstellungen + „Installierte Schrift“, öffnet `InstalledFontPickerDialog`)/-größe/Zeilenhöhe/Akzentfarbe/Fortschrittsbalken-Kante, Schalter „Freihand-Lesemodus“ (Button in der Bottom-Bar, nur lokal) und „Zitat-Tafel“ (Knopf an der Farbleiste, nur lokal), schreibt direkt über `viewModel.preferencesStore` |
| `ui/reader/InstalledFontPicker.kt` | Durchsuchbarer Dialog mit allen installierten Schriftfamilien (`InstalledFonts`), jede in ihrer eigenen Schrift; `null` = zurück zur Voreinstellung |
| `ui/reader/ReaderLocalFonts.kt` | Bringt die gewählte installierte Schrift in den Reader-WebView: `@font-face` auf eine Pseudo-URL, die `ReaderWebView.shouldInterceptRequest` mit der Datei beantwortet (nur von `InstalledFonts` gelistete Dateien, CORS-Header) |
| `ui/reader/QuoteCard.kt` | Zitat-Tafel (Äquivalent zu `QuoteMode.swift`): `QuoteOpenButton` (Knopf über der Farbleiste, auch bei angetippten Markierungen, nur Text-Artikel), `QuoteCardOverlay` (weichgezeichnetes Glas über dem ganzen Reader, Text gleitet aus seiner Stelle im Artikel in die Mitte; Wischen = Hintergrund, Ziehen am Text = verschieben, zwei Finger = zoomen, Tipp/✕/Zurück = schließen; „Design“-Feld mit Schrift/Größe 12–48/Farbe/Weichzeichner 0–40/Leuchten, Kästchen Autor/Quelle, „Bild speichern“/„Teilen“). `QuoteRenderer` setzt den Text per `StaticLayout` als Bitmap (Leuchten als Text-Schatten), fotografiert den sichtbaren WebView-Ausschnitt im dp-Raster ab, zeichnet weich (3× Box-Blur in halber Auflösung, Sättigung 1,15) und setzt das Bild zum Speichern/Teilen aus denselben Bitmaps zusammen. `QuoteBackdrop` zeichnet die 7 Hintergründe (Farben wie iOS). Abweichung von iOS: „Original“ ist kein Snapshot der Artikelschrift, sondern derselbe Text neu gesetzt in Schrift/Größe/Farbe des Readers; „Rund“ und Georgia fehlen (keine Android-Systemschriften). Speichern über MediaStore nach „Bilder/Merlin“ (bis Android 9 mit `WRITE_EXTERNAL_STORAGE`), Teilen per FileProvider (`cache/merlin-quote-out/`). Einstellungen `reader_quote_*` in `PreferencesStore` (nur lokal); die Selektion liefert `MerlinReader.quoteSelection()` (READER_JS) über `ReaderDocumentActions.quoteSelection` |
| `ui/reader/AutoScrollController.kt` | Freihand-Lesemodus (Äquivalent zu iOS' `AutoScrollController.swift`): Zustände `isActive`/`isPaused`/`reachedEnd`/`slider`, Frame-Loop per `withFrameNanos` (Tempo × Δt, Sub-Pixel-Akkumulator, Δt ≤ 0,1 s), Tempo quadratisch 10–150 dp/s, nach Berührung/Fling 1,5 s Pause, stoppt am Ende (`canScrollForward`). Ziele über `AutoScrollTarget`: `WebViewAutoScrollTarget` (`scrollBy`) und `LazyListAutoScrollTarget` (PDF, `dispatchRawDelta`). Liest im Loop keinen Compose-State, der den Reader neu komponiert |
| `ui/reader/ReminderSheet.kt` | Äquivalent zu `ReminderSheet.swift`. `ModalBottomSheet` mit Material3 `DatePickerDialog` + eigenem `TimePicker`-`AlertDialog` (sequenziell statt iOS' kombiniertem grafischem `DatePicker`, den es in Compose nicht gibt); Pre-Fill mit vorhandenem Reminder-Zeitpunkt, „Erinnerung entfernen“-Button bei bestehendem Reminder, Fehleranzeige aus `viewModel.reminderError`, lokaler `isSaving`-State sperrt die Buttons und schließt das Sheet nach erfolgreichem Speichern |
| `ui/reader/ImageLightboxScreen.kt` | Äquivalent zu `ImageLightboxView.swift`/`LightboxState`. Volle Parität als `Dialog` (Fullscreen statt eigener Nav-Route): endloses `HorizontalPager`-Karussell (Index-Multiplikator-Trick wie iOS' `TabView`-Tags), `ZoomableImage` mit Pinch-Zoom/Pan (`detectTransformGestures`) und Doppeltap-Zoom (`detectTapGestures`), vertikaler Drag-to-dismiss mit Backdrop-Fade/Skalierung über ein `Animatable`. Pinch-Zoom klemmt direkt auf `[1, 5]` statt wie iOS kurz darunter zuzulassen und zurückzuschnappen – einzige bewusste Vereinfachung. `HorizontalPager(userScrollEnabled = !isZoomed)` ersetzt iOS' `TabViewScrollEnabler`-UIKit-Hack |
| `ui/reader/ArticleReaderScreen.kt` | `Scaffold` ohne `TopAppBar` (kein Zurück-Button und kein Favoriten-Stern – Zurück per System-Geste/-Taste, Favorisieren über den Drawer; der Hamburger sitzt rechts in der `ReaderBottomBar`) + `ModalNavigationDrawer` als „Side-Drawer mit voller Aktionsliste“ (ohne Artikeltitel als Überschrift; Favorit/Erscheinungsbild/Teilen/Browser/archive.ph/Link kopieren/Archivieren/Tags/Erinnern/Artikel melden/Löschen); „Tags bearbeiten…“ öffnet `EditTagsDialog` (lädt vorher per `viewModel.loadTags()` die volle Tag-Liste, da der Reader sie sonst nicht braucht); Highlight-Farb-Toolbar bei aktiver Text-Selection, Tipp auf eine Markierung zeigt die Farbleiste mit Umfärben/Kommentieren/Entfernen (Rückfrage nur bei Kommentaren), Kommentar-Sheet und Suchleiste (`FindBar`), `ProgressBarOverlay` für die vier Kanten, öffnet `ReminderSheet`/`ImageLightboxScreen`/`ReportArticleSheet`. **`ReaderBottomBar`** (Äquivalent zu iOS' `bottomBar` in `ArticleReaderView.swift`): gleich breite Icon-Buttons (Archivieren+Zurück links, optional Freihand-Lesen in der Mitte, Menü rechts; Zurück- und Archivieren+Weiter-Button entfernt) als Overlay am unteren Rand, per `AnimatedVisibility` (slide+fade, 200ms) ein-/ausgeblendet; Sichtbarkeitslogik 1:1 aus iOS übernommen (160dp-Bodennähe, 4dp-Delta-Debounce, 40dp-Mindest-Scroll-Offset vor Richtungs-Tracking) auf Basis von `ReaderWebView`s `onScrollMetrics`. Der Button archiviert einseitig (`if (!isArchived) toggleArchive()`, nie un-archivieren) wie im iOS-Original. **Freihand-Lesemodus**: solange `AutoScrollController.isActive`, ersetzt `HandsFreeBar` (Pause/Weiter, Tempo-Slider, Beenden) die Bottom-Bar und bleibt beim Scrollen stehen; ein Pointer-Beobachter (`PointerEventPass.Initial`, verbraucht nichts) auf dem Reader-Inhalt meldet Berührungen, `handsFreeShouldPause` (Auswahl, Popover, Drawer, Sheets, Dialoge, Lightbox, Lifecycle < RESUMED) pausiert, `keepScreenOn` solange der Motor läuft |
| `data/ReportService.kt` | Äquivalent zu `ReportService.swift`. `@Singleton`, Mutex-isoliert (Kotlin-Pendant zu Swifts `actor`). Cacht die per `MerlinApi.getSettings().reportBackendUrl` geladene Backend-URL; `report(url, comment)` postet JSON an `{backendUrl}?action=report` über einen eigenen, schlanken `OkHttpClient` **ohne** `BaseUrlInterceptor`/`AuthInterceptor` – der Hilt-weite Client ist fest auf den Nextcloud-Host verdrahtet und für das externe, unauthentifizierte merlin-reports-Backend ungeeignet. `ReportError`-Sealed-Class mit denselben deutschen Meldungen wie im iOS-Original |
| `ui/reader/ReportArticleSheet.kt` | Äquivalent zu `ReportArticleSheet.swift`. `ModalBottomSheet` (statt iOS' `.sheet`/`NavigationStack`+Toolbar) mit URL-Vorschau, optionalem Mehrzeilen-Kommentarfeld, Feedback-Text (Erfolg/Fehler aus `viewModel.reportFeedback`), Abbrechen/Melden/Schließen-Button-States passend zu `viewModel.reportSending`/`reportFeedback`; `sendReport()`/`clearReportFeedback()` in `ArticleReaderViewModel.kt` |
| `ui/reader/NativeVideoPlayerCard.kt` | Äquivalent zu `NativeVideoPlayerView.swift`. `NativeVideoHost.matches(url)` erkennt ARD-/ZDF-/3sat-/Arte-Mediathek-Artikel-URLs; `NativeVideoPlayerCard` fragt darauf `MerlinApi.getVideoStream(articleId)` ab (Pass-through über `ArticleReaderViewModel.getVideoStream`, da `api` dort `private` ist) und spielt die aufgelöste HLS-Stream-URL per Media3 `ExoPlayer`/`PlayerView` ab; Poster-Cover (geteilter `viewModel.imageLoader` für Hotlink-Schutz) bis zum ersten Play-Tap (ohne verfügbaren Stream bleibt das Titelbild ohne Play-Symbol stehen), Vollbild-Umschalter über einen `Dialog` mit demselben, geteilten `ExoPlayer` (Inline-`PlayerView` gibt den Player während `isFullScreen` frei, siehe Datei-Kommentar zum Surface-Konflikt), Varianten-Auswahl per `DropdownMenu` bei mehr als einer Variante, `setPreferredTextLanguage` für Arte-Untertitelspuren. **Architekturabweichung von iOS**: die Karte sitzt als fixiertes Compose-Element *über* der `ReaderWebView` (analog zum `PaywallWarningBanner`-Overlay) statt als scrollendes Element davor, da Android Header+Artikeltext komplett als HTML in einer einzigen, selbst scrollenden `WebView` rendert (siehe `ReaderWebView`-Kommentar) und sich dort kein natives Element einbetten lässt. `ReaderHtmlBuilder.stripHeroImageIfShownAsVideoCover()` entfernt dafür wie im iOS-Original das erste Bild aus dem Artikeltext, wenn dasselbe Titelbild schon als Player-Cover dient |
| `ui/reader/ReaderMediaRewriter.kt` | Port der Video-Umbauten aus `ArticleReaderView.swift`/`MediaMarker.swift` (Markup identisch zu iOS, damit Highlight-XPaths übereinstimmen): `rewriteYouTubeEmbeds()` ersetzt YouTube-`<iframe>`s durch `.merlin-yt-embed`-Vorschaukarten, `YOUTUBE_JS` setzt beim Antippen den youtube-nocookie-Player als `<iframe>` in die Karte (anders als iOS kein natives Overlay nötig, da die Seite per `loadDataWithBaseURL` eine echte https-Origin hat); `INLINE_MEDIA_JS` + `CSS` legen auf `figure.merlin-inline-media` (Server-`InlineMediaService`) einen `<video>`/`<audio>`-Player in `<merlin-inline-player>`; `promoteHeroVideo()` macht bei Medien-Marker `kind=video` (file/HLS) das führende Hero Image zur Inline-Medien-Figure. Vollbild der WebView-Videos über `FullScreenVideoChromeClient` in `ReaderWebView.kt` (Zurück beendet es per `BackHandler`) |

**Support-Infobox (`ReaderHtmlBuilder.buildSupportBoxScript`, `SupportBoxTexts`):** Port von
`supportBoxScript(for:seed:)` aus `ArticleReaderView.swift` – Abo-/Spendenhinweis ("Dir gefällt der
Artikel von …?") nach einem pseudo-zufälligen Top-Level-`<p>` (Seed = Artikel-ID, ab 4 Absätzen), in
der Akzentfarbe des Nutzers und mit dem Icon der konkreten Artikelseite (`iconUrl`, vom Server aus
dem Seiten-HTML gelesen) als eigene Spalte links (2,5 em, ohne Kachel-Hintergrund) über die volle Boxhöhe
(`align-self: stretch`; Titel und Satz stehen in der Spalte daneben, vertikal zentriert). Der Server liefert `supportBox` nur im Einzelabruf und lässt sie im Reader
weg, wenn der Nutzer dort einen aktiven Abo-Login hat; offline (Artikel aus dem Room-Cache) gibt es
keine Box. Eingefügt wird ein eigenes Element `<merlin-support-box>` nach dem Wiederherstellen der
Highlights – `getXPath`/`resolveXPath` zählen Geschwister je Tag-Name, ein unbekannter Tag
verschiebt daher keinen Index des Artikeltextes. Der Builder ist ein `object` ohne Context: die
Texte löst `ReaderWebView` über Android-Ressourcen auf (`articleReader_supportBox_*`, Platzhalter
`{subscribe}`/`{donate}` bleiben stehen, das Skript setzt daran die Links ein). Sicherheit: nur
http(s)-URLs (Kotlin **und** JS), Konfiguration als JSON mit maskiertem `<`/`>`/`&` (ein Seitenname
wie `</script>` bricht nicht aus dem inline-Script aus), alles per `textContent`/DOM. Link-Klicks
gehen über `MerlinHighlightBridge.onOpenExternalLink` in den externen Browser, weil ein normaler
Klick die WebView selbst wegnavigieren und den Artikel ersetzen würde. Das Icon ist in `READER_JS`
vom Lightbox-Tap und vom "Bild nicht verfügbar"-Platzhalter ausgenommen (ein kaputtes Icon wird
einfach entfernt). Die Strings in `strings_i18n.xml` stammen aus den `articleReader.supportBox.*`-
Keys von `merlin-translations`.

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
| `ui/share/ShareActivity.kt` | Nimmt `ACTION_SEND`/`ACTION_SEND_MULTIPLE` mit `*/*` entgegen: Text aus `EXTRA_TEXT` (Fallback `EXTRA_SUBJECT`), Dateien aus `EXTRA_STREAM` plus `ClipData` (nur `content:`-URIs). Transparentes Fenster (`Theme.Merlin.Share`, `adjustResize`), damit die teilende App hinter dem Sheet sichtbar bleibt |
| `viewmodel/ShareViewModel.kt` | Verhalten wie `ShareViewController.swift`. `Mode`: `ONBOARDING/EXTRACTING/STAGING/SAVING/UPLOADING/ERROR/RATE_LIMITED`. Eine Web-Adresse im Text (`extractFirstUrl`, Regex statt `NSDataDetector`) wird als Artikel gespeichert (`createArticle`, Tags im Body); sonst werden die Dateien nach `cacheDir/merlin-share/<uuid>/` kopiert und nach „Merlin Dateien“ hochgeladen (merlin-server: Fehlermeldung). Tags laden beim Staging, Antippen folgt `TagTree.toggling` (Unter-Tag wählt Eltern mit, Abwählen nimmt Unter-Tags mit); eingetippte Namen (kommagetrennt) werden nach dem Laden der Tag-Liste aufgelöst oder angelegt. Fotos: drei Stufen nacheinander berechnet, die gewählte zuerst; die Wahl wird in `merlin_share`-Prefs gemerkt (Standard „Schwach“), beim Speichern werden die anderen Stufen abgebrochen. Fehler wie iOS (401/404/429 → Rate-Limit-Anzeige und zurück zum Staging, sonst HTTP-Code + `error` des Servers) |
| `ui/share/ShareScreen.kt` | Sheet wie iOS: unten, oben 16 dp abgerundet, höchstens 540 dp breit, Abdunkelung 40 %. Kopfzeile Logo + „Merlin“ + Untertitel je Modus (Fehler: rotes Kreuz, Rate-Limit: orange Uhr). Staging: Adresse (auf 57 Zeichen gekürzt) bzw. Dateiname/-anzahl + Größe, `CompressionTile`-Zeile, Tag-Baum (36-dp-Zeilen, 22 dp Einzug je Ebene, bis 5 Zeilen sichtbar, dann scrollend), „Neuer Tag (optional)“ (Tastatur-„Fertig“ speichert), Speichern-Knopf unter dem scrollenden Inhalt. Upload mit „Datei x von y · %“ und Fortschrittsbalken. Fehler bleiben 10 s bzw. bis zum Antippen, Rate-Limit 2,5 s; Erfolg schließt sofort |
| `share/FileUploader.kt` | Port von `FileUploader.swift`: `POST api/files/target` → WebDAV-`PUT` (≤ 10 MB, mit Fortschritt) bzw. Chunked Upload v2 (`MKCOL`, nummerierte `PUT`s à 10 MB, `MOVE` auf `.file`) → `POST api/files?tagIds[]=…`. Eigener OkHttp-Client ohne `BaseUrlInterceptor`; 3 Versuche bei Netzwerkfehlern, 5xx und 429 |
| `share/ImageCompressor.kt` | Port von `ImageCompressor.swift`: Stark (1600 px, Qualität 55), Schwach (4096 px, 80), Unkomprimiert; GIF/SVG bleiben unverändert, ein größeres Ergebnis wird verworfen. Dekodieren mit `inSampleSize`, Pixel bleiben in der gespeicherten Ausrichtung, EXIF (inkl. Ort) per `ExifInterface` übernommen – IPTC/XMP gehen anders als auf iOS verloren. Vorschau: mittiger Ausschnitt (15 % der langen Seite) auf 360 px, beim Vergrößern ohne Glättung |

**Noch anders als iOS:** keine Texterkennung in Fotos (iOS: Vision; auf Android nur über
ML Kit, einen Google-Baustein, den die Datenschutzerklärung ausschließt), keine
Einrichtung per Textfeldern (siehe oben). Nicht kompiliert und nicht auf einem Gerät getestet.

## Einstellungen (`ui/screens/SettingsScreen.kt`, `viewmodel/SettingsViewModel.kt`)

Letzter offener Punkt aus Abschnitt 9 in `todo.md` – Äquivalent zu `SettingsView.swift`.

| Datei | Zweck |
|---|---|
| `viewmodel/SettingsViewModel.kt` | `@HiltViewModel`. Login-Logik bewusst aus `OnboardingViewModel` dupliziert statt injiziert (ViewModels referenzieren keine anderen ViewModels) – beide kapseln denselben `LoginFlowService`, analog zu iOS' separaten `@StateObject`s in `OnboardingView`/`SettingsView`. Exponiert `preferencesStore` public (gleiches Pattern wie `ArticleReaderViewModel`, siehe `AppearanceSheet.kt`): UI liest dessen `Flow`s direkt per `collectAsState()`, schreibt aber über die Setter hier (`setDefaultFilter`/`setProgressEdge`/`setSaveProgress`/`setResumeOnOpen`/`setPrefetchWifiOnly`/`setDeveloperMode`), die bei sync-fähigen Feldern automatisch `syncPreferences()` anstoßen. `testConnection()` ruft mangels eigenem `/test`-Endpoint einfach `api.getSettings()` auf (gleiche Vereinfachung wie iOS' `MerlinAPI.testConnection()`). `syncPreferences()` fängt einen `IOException`-Fehlschlag ab und markiert `SettingsSyncQueue` (siehe unten) für einen späteren Retry statt ihn stillschweigend zu verlieren – der lokale Wert bleibt in jedem Fall sofort gesetzt. `clearCache()` löscht Artikel-/Bild-/Highlight-Cache, Lesepositionen und Zugangsdaten – loggt den Nutzer dadurch implizit aus |
| `ui/screens/SettingsScreen.kt` | Eigene Route (`Scaffold` + Zurück-`TopAppBar`) statt iOS' `Form`/Sheet: Konto-Sektion (Server-URL-Feld, „Mit Nextcloud anmelden"-Button öffnet die `loginUrl` per Custom Tabs wie `OnboardingScreen`, Lade-/Erfolgs-/Fehleranzeige, „Abmelden" mit `AlertDialog`-Bestätigung), Verbindungstest-Sektion, Präferenzen (`FilterChip`-Reihen für `ArticleFilter`/`ProgressEdge`, `Switch` für `saveProgress`/`resumeOnOpen`), Cache-Sektion (`Switch` für WLAN-only-Vorladen, „Cache leeren" mit `AlertDialog`-Bestätigung), Über-Sektion (`BuildConfig.VERSION_NAME`), Entwickler-Sektion (`Switch` + Debug-Infos). `onLoggedOut`-Callback feuert per `LaunchedEffect(isConfigured)`, sobald `isConfigured` von `true` auf `false` wechselt (Logout/Cache leeren) |

Einstieg über neues Zahnrad-Icon in der `ArticleListScreen`-TopAppBar
(`onSettingsClick`); `MainActivity.kt` registriert die Route `settings` und
setzt bei `onLoggedOut` sein lokales `isConfigured` auf `false` zurück
(`popBackStack("list", inclusive = true)`), wodurch wieder der
`OnboardingScreen` angezeigt wird.

## Paywall-Site-Zugangsdaten (`ui/screens/SiteCredentialsScreen.kt`, `viewmodel/SiteCredentialsViewModel.kt`)

Äquivalent zu `SiteCredentialsView.swift`/`SiteCredentialsViewModel.swift`
(merlin-ios). Getrennt von der Nextcloud-/Server-Anmeldung
(`SettingsViewModel`/`CredentialsStore`, oben) – hier geht es um Login-
Zugangsdaten pro Paywall-Domain (z.B. Tagesspiegel Plus), mit denen der
*Server* bei der Volltext-Extraktion eines Artikels einloggt. Endpunkte in
`MerlinApi.kt` gemäß `merlin-api.yaml` (`/api/user/site-credentials`):
`GET` (Liste + verfügbare Domains), `PUT /{domain}` (anlegen/ersetzen,
Antwort `SiteCredentialInfo` – nie das Passwort), `DELETE /{domain}` (kein
Body laut Contract, daher `Response<Unit>` statt `SuccessResponse`).

| Datei | Zweck |
|---|---|
| `models/SiteCredential.kt` | `SiteCredentialInfo` (`domain`, `status`, `lastLoginAt`; `statusEnum`-Convenience-Property für `ok`/`invalid_credentials`/`login_flow_broken`/sonstiges→`PENDING`), `SiteCredentialsResponse` (`GET`-Antwort), `SiteCredentialUpdateRequest` (`PUT`-Body), `SiteCredentialErrorResponse` (400/401-Fehler-Body `{ message, reason? }`) |
| `viewmodel/SiteCredentialsViewModel.kt` | `@HiltViewModel`. `credentials`/`availableDomains`/`connectableDomains` (Differenzmenge per `combine`)/`isLoading`/`errorMessage` als `StateFlow`s. `save()` ist die einzige Stelle im Client, die einen typisierten `HttpException`-Fehler-Body parst (`e.response()?.errorBody()` → `SiteCredentialErrorResponse` über das Hilt-`Json`-Singleton aus `NetworkModule`) – analog zum `try/catch(HttpException)`-Muster aus `ShareViewModel.saveArticle`, aber mit Body-Auswertung statt reiner Statuscode-Unterscheidung |
| `ui/screens/SiteCredentialsScreen.kt` | Eigene Route: "Verbunden"-Sektion (bestehende Zugangsdaten, farbcodierter Status, Swipe-to-Delete wie `RemindersScreen`s `ReminderRow`, Bearbeiten-Icon öffnet den Edit-Dialog erneut) + "Hinzufügen"-Sektion (`connectableDomains`, Tap öffnet den Dialog für eine neue Domain). `preselectedDomain`-Parameter öffnet den Dialog beim Betreten automatisch (Aufruf aus `PaywallWarningBanner`). Edit-Dialog als `AlertDialog` (Username/Passwort, Speichern deaktiviert bis beide Felder gefüllt, zeigt nach dem Speichern inline Erfolg/Fehler) |

Route `site-credentials?domain={domain}` in `MainActivity.kt`; Einstieg über
neue Zeile "Site-Zugangsdaten verwalten" in `SettingsScreen.kt`
(`onSiteCredentialsClick`).

## Settings-Sync mit Offline-Retry (`data/SettingsSyncQueue.kt`)

Äquivalent zu `SettingsSyncQueue.swift`, schließt die in `SettingsView.swift`/`ArticleReaderView.swift`
referenzierte Lücke (bisheriges Android-`todo.md`: „Android hat (noch) kein `SettingsSyncQueue`-
Äquivalent, ein Fehlschlag wird bewusst verschluckt"). Anders als `OfflineMutationQueue`/
`OfflineHighlightQueue` muss sich diese Queue nicht merken, *was* sich geändert hat: Settings werden
immer als ein flaches Snapshot-Objekt gepusht (`PreferencesStore.toServerSettings()`), ein einzelnes
Dirty-Flag genügt daher – beliebig viele Offline-Änderungen fassen sich beim Retry automatisch zu
einem PUT mit dem dann aktuellen lokalen Stand zusammen.

| Datei | Zweck |
|---|---|
| `data/SettingsSyncQueue.kt` | `isDirty()`/`markDirty()` (Persistenz über `PreferencesStore.needsSettingsSync`/`setNeedsSettingsSync`, DataStore-Boolean statt In-Memory, damit ein Prozess-Tod das Flag nicht verliert), `retryIfNeeded()` (Mutex-isoliert, pusht `toServerSettings()` erneut und löscht das Flag nur bei Erfolg), `scheduleRetry()` (netzwerkabhängiger `OneTimeWorkRequest` über WorkManager, `NetworkType.CONNECTED`, `ExistingWorkPolicy.KEEP`) |
| `data/SettingsSyncWorker.kt` | `@HiltWorker`-`CoroutineWorker`, ruft `SettingsSyncQueue.retryIfNeeded()` – Äquivalent zum `NWPathMonitor`-Trigger im iOS-Original, hier als WorkManager-Job (robuster gegen Prozess-Tod als ein reiner `ConnectivityManager.NetworkCallback`), analog `MutationDrainWorker` |

`SettingsViewModel.syncPreferences()` und `ArticleReaderViewModel.syncAppearanceToServer()` (Appearance-
Push aus `AppearanceSheet.kt`) sind die beiden Aufrufer: beide fangen jetzt `IOException` (echter
Verbindungsfehler, Äquivalent zu iOS' `MerlinAPIError.networkError`) ab und rufen
`settingsSyncQueue.markDirty()` + `scheduleRetry()` auf, statt den Fehlschlag wie zuvor stillschweigend
zu verschlucken.

### Paywall-Hinweis im Reader (`ui/reader/PaywallWarningBanner.kt`)

Äquivalent zum Paywall-Banner in `ArticleReaderView.swift`. `Article.kt` trägt
dafür zwei zusätzliche, vom Server gesetzte Felder: `requiresLoginDomain`
(Paywall-Domain, wenn die Extraktion mangels gültiger Zugangsdaten
gescheitert ist) und `requiresLoginPage` (nur gesetzt, wenn auch
`requiresLoginDomain` gesetzt ist). `ArticleReaderScreen.kt` zeigt das Banner
oben im Artikelbereich, solange `requiresLoginDomain` gesetzt und der Banner
nicht lokal weggewischt ist (nicht-persistenter `remember`-State, pro
`articleId` zurückgesetzt – Parität zu iOS' Session-Dismiss). "Verbinden"
navigiert über den neuen `onNavigateToSiteCredentials`-Parameter zu
`SiteCredentialsScreen` mit vorausgewählter Domain; "Erneut versuchen" ruft
`ArticleReaderViewModel.retryPaywall()` auf (löscht den Artikel und legt ihn
mit derselben URL neu an, analog zu iOS' Retry-Verhalten, dann wird der
Reader wie bei `delete()` verlassen).

## Bewusst noch nicht angelegt

Siehe `todo.md` für die vollständige Liste; insbesondere fehlen noch: TTS-Pipeline (Media3) und der `OnboardingTour`-Screen (inkl. der UI-seitige Notification-Permission-Prompt), SSE-Client.

## Bekannte offene Punkte für den ersten Android-Studio-Sync

- Gradle-Wrapper-Dateien (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) fehlen noch – Android Studio bietet beim ersten Öffnen an, sie zu erzeugen; das sollte bestätigt werden.
- `compileSdk`/`targetSdk 34` setzt voraus, dass die entsprechende SDK-Platform im SDK-Manager installiert ist.
- Kein Launcher-Icon hinterlegt (`mipmap`-Ressourcen fehlen) – Android Studio nutzt sonst ein Default-Icon; kein Blocker für den Build.
