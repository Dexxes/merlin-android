# Datenschutzerklärung – Merlin für Android

*English version below.*

Stand: 15. November 2026

## Kurz gesagt

Merlin ist ein Client für deinen eigenen Server: eine Nextcloud mit der Merlin-App oder einen Merlin-Server. Der Entwickler der App betreibt keinen eigenen Dienst für die App und erhält keine Daten von dir. Die App enthält keine Werbung, keine Analyse- oder Tracking-Werkzeuge und keine Software-Bausteine Dritter, die Daten sammeln.

## Verantwortlicher

Julian von Bülow, Entwickler der App Merlin für Android.
Kontakt: E-Mail an <jvb@live.de> oder über die Issues des Projekts unter <https://github.com/Dexxes/merlin-android/issues>.

Für die Daten auf deinem Server ist verantwortlich, wer den Server betreibt, also du selbst oder dein Server-Administrator.

## Welche Daten die App verarbeitet

### Auf deinem Gerät

- **Zugangsdaten:** Server-Adresse, Benutzername und App-Passwort werden verschlüsselt gespeichert (EncryptedSharedPreferences, Schlüssel im Android-Keystore).
- **Zwischenspeicher:** Artikel, Bilder, Markierungen und Lesepositionen werden gespeichert, damit die App auch offline funktioniert. Dazu kommen Änderungen, die noch nicht an den Server übertragen wurden.
- **Einstellungen und Erinnerungen:** Erinnerungen werden als lokale Wecker bzw. Mitteilungen auf dem Gerät geplant und nach einem Neustart wiederhergestellt. Es gibt keine Push-Mitteilungen über einen Server.

Beim Abmelden werden die Zugangsdaten vom Gerät gelöscht. Zwischengespeicherte Artikel und Lesepositionen bleiben derzeit erhalten, bis du die App-Daten in den Android-Einstellungen löschst oder die App deinstallierst.

Die App erlaubt die Android-Sicherung (`allowBackup`). Wenn du die Sicherung deines Geräts aktiviert hast, kann Android App-Daten in deine eigene Sicherung übernehmen, zum Beispiel bei Google. Das steuerst du in den Android-Einstellungen.

### An deinen Server

Die App überträgt alle Inhalte ausschließlich an den Server, den du selbst einträgst. Dazu gehören:

- gespeicherte Links und Texte, die du über das Teilen-Menü an Merlin sendest
- Tags, Favoriten, Archivstatus, Lesepositionen und Markierungen
- Einstellungen der App
- Zugangsdaten für Abos von Nachrichtenseiten, wenn du sie hinterlegst. Der Server nutzt sie nur, um sich bei der jeweiligen Seite anzumelden.
- Einstellungen für Share-Links, wenn du einen Artikel öffentlich teilst

Die Verbindung ist mit HTTPS verschlüsselt. Wie lange dein Server Daten speichert und wie du sie löschst, legt der Betreiber des Servers fest.

### Direkt an Dritte

Einige Funktionen laden Inhalte direkt von anderen Anbietern. Diese sehen dabei deine IP-Adresse und die übliche technische Anfrage.

- **Bilder in Artikeln:** Sie werden von den Websites geladen, aus denen die Artikel stammen.
- **YouTube-Videos:** Das Vorschaubild kommt von `i.ytimg.com`, das Video selbst wird erst geladen, wenn du es antippst, und läuft dann über `youtube-nocookie.com`. Dabei gilt die Datenschutzerklärung von Google.
- **Videos aus Mediatheken und in Artikeln:** Videos von ARD, ZDF, 3sat und Arte sowie Videos im Artikeltext werden direkt von den Servern der Anbieter gestreamt, sobald du sie abspielst.
- **Melden von Artikeln:** Wenn du einen Artikel meldest, gehen Adresse und Kommentar an das Melde-Backend, das dein Server-Administrator eingerichtet hat.
- **Anmeldung und Links:** Die Anmeldeseite deines Servers und angeklickte Links öffnet sich im Browser bzw. auf der jeweiligen Website.

## Was die App nicht tut

- kein Tracking und keine Werbung, und keine Weitergabe oder kein Verkauf von Daten
- keine Analyse des Nutzungsverhaltens und keine Absturzberichte an den Entwickler
- kein Konto beim Entwickler: Konten gibt es nur auf deinem eigenen Server

## Berechtigungen

- **Internet und Netzwerkstatus:** für die Verbindung zu deinem Server und zum Erkennen, wann offline gespeicherte Änderungen übertragen werden können
- **Mitteilungen, exakte Wecker, Start nach dem Hochfahren:** für Erinnerungen an Artikel
- **Vibration:** für haptische Rückmeldungen

Du kannst die Berechtigung für Mitteilungen jederzeit in den Android-Einstellungen widerrufen.

## Deine Rechte

Nach der DSGVO hast du das Recht auf Auskunft, Berichtigung, Löschung, Einschränkung der Verarbeitung, Datenübertragbarkeit und Widerspruch. Außerdem kannst du dich bei einer Datenschutz-Aufsichtsbehörde beschweren. Da der Entwickler keine Daten von dir speichert, richten sich Anfragen zu Daten auf deinem Server an dessen Betreiber.

## Änderungen

Wenn sich die App ändert, wird diese Erklärung angepasst. Die jeweils aktuelle Fassung steht unter <https://github.com/Dexxes/merlin-android/blob/main/PRIVACY.md>.

---

# Privacy Policy – Merlin for Android

Last updated: November 15, 2026

## In short

Merlin is a client for your own server: a Nextcloud with the Merlin app, or a Merlin server. The developer of the app runs no service for the app and receives no data from you. The app contains no ads, no analytics or tracking tools, and no third-party components that collect data.

## Controller

Julian von Bülow, developer of the Merlin for Android app.
Contact: email <jvb@live.de> or the project's issues at <https://github.com/Dexxes/merlin-android/issues>.

Whoever runs your server is responsible for the data stored on it: you, or your server administrator.

## Data the app processes

### On your device

- **Login credentials:** server address, username and app password are stored encrypted (EncryptedSharedPreferences, key in the Android Keystore).
- **Cache:** articles, images, highlights and reading positions are stored so the app also works offline. This includes changes not yet sent to the server.
- **Settings and reminders:** reminders are scheduled as local alarms and notifications on the device and restored after a reboot. There are no push notifications from a server.

Logging out removes the login credentials from the device. Cached articles and reading positions are currently kept until you clear the app's data in Android settings or uninstall the app.

The app allows Android backup (`allowBackup`). If you have turned on device backup, Android may include app data in your own backup, for example with Google. You control this in Android settings.

### Sent to your server

The app sends all content only to the server you enter yourself. This includes:

- saved links, and text you share to Merlin through the share sheet
- tags, favorites, archive status, reading positions and highlights
- app settings
- login credentials for news site subscriptions, if you add them. The server uses them only to log in to that site.
- share link settings, if you share an article publicly

The connection is encrypted with HTTPS. How long your server keeps data and how you delete it is up to whoever runs the server.

### Sent directly to third parties

Some features load content directly from other providers. They see your IP address and the usual technical request.

- **Images in articles:** they are loaded from the websites the articles come from.
- **YouTube videos:** the preview image comes from `i.ytimg.com`; the video itself loads only when you tap it and then plays through `youtube-nocookie.com`. Google's privacy policy applies.
- **Videos from broadcaster media libraries and in articles:** videos from ARD, ZDF, 3sat and Arte, and videos inside article text, are streamed directly from the providers' servers once you play them.
- **Site icons:** they come from DuckDuckGo (`icons.duckduckgo.com`). This sends the domain name of the saved article, for example `example.com`, but not the full address.
- **Reporting articles:** if you report an article, its address and your comment go to the reporting backend your server administrator set up.
- **Login and links:** your server's login page, links and "Open via archive.ph" open in the browser or on the respective website.

## What the app does not do

- no tracking and no advertising, and no sharing or selling of data
- no usage analytics and no crash reports sent to the developer
- no account with the developer: accounts exist only on your own server

## Permissions

- **Internet and network state:** to connect to your server and to know when changes saved offline can be sent
- **Notifications, exact alarms, run at startup:** for article reminders
- **Vibration:** for haptic feedback

You can revoke the notification permission at any time in Android settings.

## Your rights

Under the GDPR you have the right of access, rectification, erasure, restriction of processing, data portability and objection. You can also lodge a complaint with a data protection supervisory authority. Since the developer stores no data about you, requests about data on your server go to whoever runs it.

## Changes

This policy is updated when the app changes. The current version is always at <https://github.com/Dexxes/merlin-android/blob/main/PRIVACY.md>.
