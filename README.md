# Rollentrainer – Android-App für den Van Rysel D100 (FTMS)

Kotlin · Jetpack Compose (Material 3) · Bluetooth Low Energy (Fitness Machine Service)

Die App scannt und verbindet den Trainer selbst (kein Koppeln in den Android-Einstellungen),
steuert den Widerstand im ERG-Modus und bietet **Geräte**, **Freie Fahrt** (21 Stufen) und
**Trainingsauswahl** (Intervall-Vorlagen). Es werden keine Fahrdaten aufgezeichnet.

> **Stand:** Der Code wurde ohne Android-SDK geschrieben. Geprüft sind Syntax und Typen
> (gegen nachgebildete Android-/Compose-Schnittstellen) sowie Parser- und Ablauflogik per Test.
> Ein echter Gradle-Build und ein Test am Trainer stehen noch aus.

---

## Weg A: APK automatisch bauen lassen (ohne PC-Installation)

Das Projekt enthält einen fertigen Build-Ablauf für GitHub (`.github/workflows/build.yml`).

1. Kostenloses Konto auf <https://github.com> anlegen und ein **neues, privates Repository**
   erstellen (z. B. `rollentrainer`), ohne README.
2. ZIP entpacken. Im leeren Repository auf **„uploading an existing file“** klicken und den
   **gesamten Inhalt** des Ordners `Rollentrainer` hineinziehen (inklusive des versteckten
   Ordners `.github`), dann **Commit changes**.
   - Falls `.github` beim Hochladen fehlt: im Repository **Add file → Create new file**,
     als Namen `.github/workflows/build.yml` eintragen und den Inhalt der Datei einfügen.
3. Reiter **Actions** öffnen – der Lauf „APK bauen“ startet automatisch (ca. 5 Minuten).
4. Danach liegt die Datei **`Rollentrainer.apk`** unter **Releases** (rechte Seitenleiste)
   und zusätzlich als Artefakt im Actions-Lauf.
5. APK am Xiaomi im Browser herunterladen und antippen.

Schlägt der Lauf fehl, steht die Fehlermeldung im Schritt „APK bauen“ – einfach kopieren
und zur Korrektur weitergeben.

## Weg B: Android Studio

1. Android Studio (aktuelle Version) installieren, **Open** → Ordner `Rollentrainer`.
2. Gradle-Sync abwarten (lädt Abhängigkeiten).
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
   Ergebnis: `app/build/outputs/apk/debug/app-debug.apk`.
4. Entweder Handy per USB anschließen (Entwickleroptionen → **USB-Debugging** und bei Xiaomi
   zusätzlich **„Über USB installieren“**) und **Run** drücken – oder die APK aufs Handy kopieren.

## APK auf dem Xiaomi installieren

- APK antippen → „Unbekannte Apps installieren“ für den Browser/Dateimanager erlauben.
- HyperOS zeigt ggf. eine Sicherheitswarnung mit Wartezeit – bestätigen.
- Alle Builds sind mit demselben Schlüssel signiert (`app/rollentrainer.jks`), Updates lassen
  sich also einfach über die alte Version installieren.

---

## Bedienung in Kürze

- **Geräte:** „Neues Gerät hinzufügen“ → Trainer antippen → wird verbunden und gespeichert.
  Danach verbindet sich die App beim Start automatisch mit dem zuletzt genutzten Gerät.
  Findet der Scan nichts, „Alle Bluetooth-Geräte anzeigen“ aktivieren.
- **Freie Fahrt:** Countdown, dann Stufe 0 (Leerlauf). Mit „–“/„+“ in 21 Stufen bis 550 W.
- **Trainingsauswahl:** Vorlagen anlegen (Aufwärmen, Intervall, Pause, Wiederholungen,
  optional Ausfahren), speichern, starten, duplizieren, löschen.

## Bekannte Stolperfallen

- **Akkuoptimierung (Xiaomi/HyperOS):** Einstellungen → Apps → Rollentrainer → Energiesparen →
  **„Keine Beschränkungen“**. Sonst trennt das System die Bluetooth-Verbindung. Die App weist
  beim ersten Start darauf hin.
- **Berechtigung:** „Geräte in der Nähe“ muss erlaubt sein (wird beim Start abgefragt).
  Standort wird nicht benötigt.
- **Nur eine App gleichzeitig:** Der Trainer darf nicht mit Zwift, MyWhoosh, OneLap o. Ä.
  verbunden sein – diese Apps vorher vollständig schließen.
- **Nicht in den Bluetooth-Einstellungen koppeln.** Die App verbindet direkt.
- **Trainer wach?** Kurz treten, damit er Bluetooth-Signale sendet.
- **Leerlauf (Stufe 0 / Pause):** Die App sendet kein 0-W-Ziel, sondern Simulationsmodus mit
  0 % Steigung; lehnt der Trainer das ab, die kleinste Widerstandsstufe, zuletzt die kleinste
  unterstützte Zielleistung. Wie leicht sich „Leerlauf“ anfühlt, hängt vom Trainer ab.
- **Zielwerte** werden auf den vom Trainer gemeldeten Leistungsbereich begrenzt.
- **Android-Version:** `targetSdk`/`compileSdk` stehen auf 36 (mit dem gewählten, bewährten
  Build-Werkzeug-Stand). Die App läuft damit auch unter Android 17; für API 37 müssten
  Android-Gradle-Plugin und Gradle auf Version 9.x angehoben werden.

## Projektaufbau

```
app/src/main/java/de/rollentrainer/app/
├── MainActivity.kt        Berechtigungen, Einstieg
├── AppViewModel.kt        Navigation, Freie Fahrt, Trainingsablauf (MVVM, Coroutines/Flow)
├── ble/
│   ├── FtmsManager.kt     Scan, Verbindung, Reconnect, GATT-Queue, Control Point
│   └── FtmsParser.kt      Indoor Bike Data, Power Range, Antworten
├── data/
│   ├── Models.kt          Gerät, Training, Phasen, Stufentabelle
│   └── Store.kt           Speicherung (DataStore)
└── ui/                    Compose-Oberfläche (Hauptmenü, Geräte, Freie Fahrt, Trainings)
```
