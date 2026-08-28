Ich arbeite am Kotlin/Compose/Hilt-Port von `merlin-ios` nach `merlin-android` (Projekt „Merlin", siehe `C:\Users\jvb\Desktop\Claude\Merlin\.claude\CLAUDE.md` für Konventionen). Bitte lies zuerst `merlin-android/todo.md` und `merlin-android/Structure.md` komplett, das ist der aktuelle Stand.

Kurzer Status: Onboarding/Login-Flow, Persistenz/Offline-First (Abschnitt 4), Reminder-System (Abschnitt 5), Report-Funktion (Abschnitt 6), ViewModel-Schicht (Abschnitt 8), Artikelliste- und Reader-UI (Abschnitt 9, in Navigation eingehängt, inkl. `ReminderSheet`, `ReportArticleSheet`, `ImageLightboxScreen`) und Share-Funktionalität (Abschnitt 10 – `ShareActivity`/`ShareScreen`/`ShareViewModel`, Manifest-Intent-Filter aktiv) sind fertig und Gradle-sync-verifiziert ("BUILD SUCCESSFUL").

Offen sind insbesondere:
- Abschnitt 7: TTS-Pipeline (Media3/ExoPlayer, siehe CLAUDE.md TTS-Kette)
- Abschnitt 9 Rest: `OnboardingTour`, `AddArticleSheet`, `TagFilterSheet`, `RemindersScreen`, `SettingsScreen`, Bild-Komponente mit Cache-First-Loading, Platzhalterbild für Artikel ohne Vorschaubild
- Polish-Punkte: SSE-Listener für Echtzeit-Updates (Abschnitt 8, aktuell Polling-Loop statt SSE); Swipe-Actions auf `ArticleCard`; echtes Pull-to-Refresh
- Abschnitt 11: Sonstiges (Haptik, App-Versionsanzeige, Entwicklermodus)
- Abschnitt 12: Tests & Verifikation

Bitte frage mich zuerst per AskUserQuestion, mit welchem der offenen Punkte ich als Nächstes weitermachen möchte (analog zum letzten Mal, als ich „Report-Funktion" gewählt habe). Halte dich an die Projektregeln aus CLAUDE.md: Klärungsfragen vor Beginn, Tasks tracken, todo.md + Structure.md am Ende jedes Abschnitts aktualisieren, Verifikation vor „done", Swift-Äquivalenz in Kommentaren dokumentieren, minimal-invasive Änderungen.
