# Google Play: Angaben für „Datensicherheit“

Vorlage für das Formular *App-Inhalte → Datensicherheit* in der Play Console. Stand: 22. November 2026,
passend zu [`PRIVACY.md`](PRIVACY.md). Ändert sich ein Datenfluss, beide Dateien anpassen.

Dies ist eine Einschätzung, keine Rechtsberatung. Google definiert „Erheben“ als jede Übertragung
vom Gerät weg, auch wenn der Entwickler die Daten nie sieht
([Google-Hilfe](https://support.google.com/googleplay/android-developer/answer/10787469)).
Eine Ausnahme für Daten, die an einen vom Nutzer selbst eingetragenen Server gehen, ist dort nicht
eindeutig beschrieben. Deshalb ist unten vorsichtig alles als „erhoben“ angegeben, was die App an
den Server des Nutzers sendet.

## Allgemeine Fragen

| Frage | Antwort | Begründung |
|---|---|---|
| Erhebt oder teilt die App erforderliche Nutzerdaten? | **Ja** | Inhalte gehen an den Server, den der Nutzer einträgt |
| Werden alle Daten bei der Übertragung verschlüsselt? | **Ja** | Nur HTTPS, Cleartext ist blockiert (`network/HttpsUrls.kt`) |
| Können Nutzer das Löschen ihrer Daten beantragen? | **Ja** | Einträge lassen sich in der App löschen. Alles Weitere liegt beim Betreiber des eigenen Servers; Abmelden löscht die Zugangsdaten vom Gerät |
| Ermöglicht die App das Erstellen eines Kontos? | **Nein** | Anmeldung nur an einem bestehenden Nextcloud- oder Merlin-Server-Konto (Login Flow v2). Deshalb ist keine Konto-Löschungs-URL nötig |
| Datenschutzerklärung | `https://github.com/Dexxes/merlin-android/blob/main/PRIVACY.md` | Besser später eine eigene, dauerhaft erreichbare Seite |

## Erhobene Datentypen

Alle mit Zweck **App-Funktionalität**, **nicht geteilt**, **nicht flüchtig verarbeitet** (der Server speichert sie).

| Kategorie → Datentyp | Erforderlich/optional | Was genau |
|---|---|---|
| Persönliche Daten → Nutzer-IDs | Erforderlich | Benutzername des Server-Kontos |
| Persönliche Daten → Sonstige Angaben | Optional | Zugangsdaten für Abos von Nachrichtenseiten (Website-Anmeldungen), nur wenn hinterlegt |
| Fotos und Videos → Fotos, Videos | Optional | Über das Teilen-Menü an Merlin gesendete Fotos und Videos, mit erhaltenen EXIF-Daten (auch Aufnahmeort) |
| Audiodateien → Sonstige Audiodateien | Optional | Über das Teilen-Menü gesendete Audiodateien |
| Dateien und Dokumente | Optional | Über das Teilen-Menü gesendete Dateien, PDFs |
| App-Aktivitäten → Sonstige von Nutzern erstellte Inhalte | Erforderlich | Gespeicherte Links und Texte, Tags, Markierungen, Kommentare, Meldungen von Artikeln |
| App-Aktivitäten → App-Interaktionen | Erforderlich | Lesepositionen, Favoriten, Archivstatus. Erinnerungen bleiben nur auf dem Gerät |

Optional bedeutet hier: Der Nutzer löst die Übertragung selbst aus und kann die App ohne sie nutzen.

**Nicht erhoben:** Standort (abgesehen von EXIF in Fotos, siehe oben), Finanzdaten, Gesundheit,
Nachrichten, Kontakte, Kalender, Geräte-IDs, Absturz- und Leistungsdaten, Web-Browserverlauf.
Werbe-, Analyse- und Tracking-SDKs sind keine eingebunden.

## Teilen mit Dritten

**Keine Weitergabe** im Sinne des Formulars. Was direkt an Dritte geht, ist vom Nutzer ausgelöst und
in der Datenschutzerklärung beschrieben, und fällt damit unter Googles Ausnahme für vom Nutzer
veranlasste Übertragungen:

- Artikelbilder und Videos (ARD, ZDF, 3sat, Arte, YouTube über `youtube-nocookie.com`) werden beim
  Anzeigen bzw. Abspielen direkt beim jeweiligen Anbieter geladen.
- „Über archive.ph öffnen“ und angeklickte Links öffnen den Browser.
- Gemeldete Artikel gehen an das Melde-Backend, das der Server-Administrator einrichtet.

Seit diesem Stand lädt die App keine Website-Icons mehr von DuckDuckGo.
