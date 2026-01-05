# LabWiki (Android App)

LabWiki ist eine Android-App, die mehrere Docusaurus-Wikis in einem WebView bündelt und
on-demand offline verfügbar macht. Jedes Wiki wird erst beim Öffnen zwischengespeichert.

## Lokaler Build

```bash
cd app
./gradlew assembleDebug
```

## Run in Android Studio

1. Android Studio öffnen.
2. Ordner `LabWiki/app` als Projekt auswählen.
3. App über ein Device/Emulator starten.

## Wiki-Konfiguration

Die 5 Wikis werden zentral in `app/app/src/main/java/com/labwiki/app/WikiRegistry.kt` gepflegt.
Trage dort für jedes Wiki `id`, `remoteStartUrl` und `remoteOfflineBase` ein.

## Erwartete Offline-Endpunkte

Jedes Wiki-Repo muss folgende URLs bereitstellen:

- `<remoteOfflineBase>/version.json`
- `<remoteOfflineBase>/build.zip`

Die App lädt `version.json`, vergleicht `commit` (bevorzugt) bzw. `version`, und lädt
`build.zip` nur bei Bedarf. Der Cache wird erst beim Öffnen eines Wikis aufgebaut.

## Offline-Verhalten (on-demand)

- Ist ein Wiki bereits gecached, lädt die App die lokale Version.
- Wenn der Cache fehlt, lädt die App die Online-Start-URL.
- Ohne Netzwerk und ohne Cache wird `assets/hub/offline.html` angezeigt.
