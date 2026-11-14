# Rollentrainer – Android-App für den Van Rysel D100 (FTMS)

Kotlin · Jetpack Compose (Material 3) · Bluetooth Low Energy (Fitness Machine Service)

Die App scannt und verbindet den Trainer selbst (kein Koppeln in den Android-Einstellungen),
steuert den Widerstand im ERG-Modus und bietet **Geräte**, **Freie Fahrt** (21 Stufen),
**Trainingsauswahl** (Intervall-Vorlagen) und **Verlauf** (aufgezeichnete Fahrten).

## Version 2.0

- **Aufzeichnung:** Jede Freie Fahrt und jedes Training wird sekündlich mitgeschrieben
  (Leistung, Geschwindigkeit, Strecke, Kadenz, Puls). Beim Beenden fragt die App, ob die Fahrt
  **gespeichert** oder **verworfen** werden soll.
- **Verlauf:** Liste aller gespeicherten Fahrten mit Zusammenfassung und Verlaufskurven.
- **Export:** FIT und TCX – teilen (Strava, Garmin Connect, intervals.icu, Mail …) oder als
  Datei speichern.
- **Weiterlaufen im Hintergrund:** Verbindung, Zeit, Trainingsablauf und Aufzeichnung laufen
  weiter, wenn das Smartphone im Standby ist oder eine andere App geöffnet wird. Währenddessen
  zeigt eine Benachrichtigung den aktuellen Stand.
- **Pulsmesser:** Brustgurte, Armbänder und Uhren mit Bluetooth-Herzfrequenz lassen sich
  zusätzlich zum Trainer verbinden.

## Version 2.2 – Tablet, Querformat, einstellbare Stufen

- **Tablet:** Alle Bildschirme nutzen die volle Fläche – zwei Bereiche nebeneinander,
  mehrspaltige Listen, größere Zahlen und Bedienelemente.
- **Querformat:** Die App lässt sich drehen, auch auf dem Handy. Quer stehen z. B. Live-Werte
  und Steuerung nebeneinander; der Editor zeigt das Trainingsprofil dauerhaft neben den
  Einstellungen.
- **Handy hochkant:** unverändert, bis auf das neue Einstellungs-Symbol unten rechts im Hauptmenü.
- **Stufen der Freien Fahrt:** Anzahl der Stufen (5–40) und Leistung der höchsten Stufe
  (100–1500 W) sind einstellbar – im Hauptmenü unten rechts über das Regler-Symbol. Stufe 0
  bleibt Leerlauf, die Stufen steigen linear und werden auf 5 W gerundet. Standard: 20 Stufen
  bis 550 W.

## Version 2.1 – überarbeitete Oberfläche

Die Oberfläche wurde nach der Methode [unslop](https://github.com/heydamir/unslop) von
Damir Sokolovsky (@heydamir, CC BY 4.0) überarbeitet. Funktionen und Abläufe sind unverändert.

- **Farben:** eine neutrale Graphit-Reihe für alle Flächen, drei Textrollen, ein Akzent (Petrol).
  Farbe tragen nur die Intensitätsskala, das Trainingsprofil und die Verlaufskurven.
- **Symbole:** Linien-Symbole statt Emojis und Sonderzeichen (Stil angelehnt an Lucide, ISC).
- **Formen:** Radius nach Größe (Bedienelemente 12, Karten 16, Dialoge 20) statt Pillenform,
  Haarlinien statt getönter Flächen.
- **Schrift:** ruhigere Gewichte, Zahlen mit Tabellenziffern, Werte in Textfarbe – die
  Intensität zeigt ein Punkt oder Streifen statt farbiger Schrift.
- **Hauptmenü:** Geräte und Verlauf als kompakte Zeilen mit aktuellem Stand; Freie Fahrt und
  Trainingsauswahl als große Kacheln mit Stufenskala bzw. Trainingsprofil.
- **Leere Seiten:** Hinweis mittig statt oben angeklebt.
- **Texte:** keine Ausrufezeichen und Emojis mehr („Start“, „Training abgeschlossen“).

### Version 1.0 weiterhin verfügbar

Die bisherige Version (ohne Aufzeichnung) bleibt abrufbar:

- **APK:** unter [Releases](../../releases) der Eintrag
  [Rollentrainer 1.0.4](../../releases/tag/v1.0.4)
- **Quellcode:** Zweig [`version-1.0`](../../tree/version-1.0)

Version 2.0 lässt sich direkt über 1.0 installieren, Geräte und Trainings bleiben erhalten.
Der Weg zurück von 2.0 auf 1.0 erfordert, die App vorher zu deinstallieren (Android erlaubt
kein Herabstufen); dabei gehen die gespeicherten Daten verloren.

> **Stand:** Die APK wird per GitHub Actions gebaut; FIT- und TCX-Export sind mit unabhängigen
> Lesern geprüft, die Bildschirme wurden für Handy und Tablet in beiden Ausrichtungen als
> Bilder gerendert und durchgesehen.
> Ein Test am echten Trainer, mit Pulsmesser und im Standby steht noch aus.

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
5. APK am Smartphone im Browser herunterladen und antippen.

Schlägt der Lauf fehl, steht die Fehlermeldung im Schritt „APK bauen“ – einfach kopieren
und zur Korrektur weitergeben.

## Weg B: Android Studio

1. Android Studio (aktuelle Version) installieren, **Open** → Ordner `Rollentrainer`.
2. Gradle-Sync abwarten (lädt Abhängigkeiten).
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
   Ergebnis: `app/build/outputs/apk/debug/app-debug.apk`.
4. Entweder Handy per USB anschließen (Entwickleroptionen → **USB-Debugging** und bei Xiaomi
   zusätzlich **„Über USB installieren“**) und **Run** drücken – oder die APK aufs Handy kopieren.

## APK auf dem Smartphone installieren

- APK antippen → „Unbekannte Apps installieren“ für den Browser/Dateimanager erlauben.
- OS zeigt ggf. eine Sicherheitswarnung mit Wartezeit – bestätigen.
- Alle Builds sind mit demselben Schlüssel signiert (`app/rollentrainer.jks`), Updates lassen
  sich also einfach über die alte Version installieren.

---

## Bedienung in Kürze

- **Geräte:** „Neues Gerät hinzufügen“ → Trainer antippen → wird verbunden und gespeichert.
  Danach verbindet sich die App beim Start automatisch mit dem zuletzt genutzten Gerät.
  Findet der Scan nichts, „Alle Bluetooth-Geräte anzeigen“ aktivieren.
- **Freie Fahrt:** Countdown, dann Stufe 0 (Leerlauf). Mit „–“/„+“ durch die Stufen
  (Standard: 20 Stufen bis 550 W, einstellbar).
- **Trainingsauswahl:** Vorlagen anlegen (Aufwärmen, Intervall, Pause, Wiederholungen,
  optional Ausfahren), speichern, starten, duplizieren, löschen.
- **Pulsmesser:** Unter „Geräte“ auf „＋ Pulsmesser“ tippen. Bei Uhren vorher
  „Herzfrequenz übertragen“ einschalten. Geräte, die nur ANT+ senden, funktionieren nicht.
- **Verlauf:** Fahrt antippen → Kurven ansehen, umbenennen, löschen oder exportieren.
- **Bildschirm:** Im Hauptmenü lässt sich wählen, ob der Bildschirm während der Fahrt an bleibt.

## Bekannte Stolperfallen

- **Akkuoptimierung (OS):** Einstellungen → Apps → Rollentrainer → Energiesparen →
  **„Keine Beschränkungen“**. Sonst trennt das System die Bluetooth-Verbindung. Die App weist
  beim ersten Start darauf hin.
- **Berechtigung:** „Geräte in der Nähe“ muss erlaubt sein (wird beim Start abgefragt).
  Standort wird nicht benötigt. „Benachrichtigungen“ sollte erlaubt sein, damit die laufende
  Einheit angezeigt wird.
- **Standby/Hintergrund:** Manche Hersteller beenden Apps im Hintergrund besonders streng.
  Hilft die Akku-Einstellung allein nicht, zusätzlich „Autostart“ erlauben und die App in der
  Übersicht der zuletzt genutzten Apps sperren (Schloss-Symbol).
- **Abbruch durch das System:** Die laufende Aufzeichnung wird alle 30 Sekunden gesichert.
  Wird die App beendet, bietet sie die Fahrt beim nächsten Start zum Speichern an.
- **Strecke und Geschwindigkeit** sind auf der Rolle rechnerische Werte des Trainers. Die
  Strecke summiert die App aus der Geschwindigkeit.
- **Kalorien:** Liefert der Trainer keinen Wert, schätzt die App ihn aus der Leistung
  (1 kJ Arbeit ≈ 1 kcal).
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
├── MainActivity.kt        Berechtigungen, Einstieg, Export (Teilen/Speichern)
├── Engine.kt              Navigation, Freie Fahrt, Trainingsablauf, Aufzeichnung (Coroutines/Flow)
├── RideService.kt         Vordergrunddienst für Standby/Hintergrund
├── ble/
│   ├── FtmsManager.kt     Scan, Verbindung, Reconnect, GATT-Queue, Control Point
│   ├── FtmsParser.kt      Indoor Bike Data, Power Range, Antworten
│   └── HrManager.kt       Pulsmesser (Heart Rate Service)
├── data/
│   ├── Models.kt          Gerät, Training, Phasen, Stufentabelle, Fahrt, Messpunkt
│   ├── Store.kt           Einstellungen, Geräte, Trainings (DataStore)
│   ├── RideStore.kt       Gespeicherte Fahrten (Dateien)
│   └── Export.kt          FIT- und TCX-Export
└── ui/                    Compose-Oberfläche (Hauptmenü, Geräte, Freie Fahrt, Trainings, Verlauf)
```

## Lizenz

Copyright (C) 2026 franzkoe

Dieses Programm ist freie Software und steht unter der **GNU General Public License,
Version 3 oder (nach deiner Wahl) jeder späteren Version** (GPL-3.0-or-later). Es wird ohne
jede Gewährleistung bereitgestellt. Der vollständige Lizenztext steht in der Datei
[`LICENSE`](LICENSE).

Die verwendeten Bibliotheken (AndroidX, Jetpack Compose, Kotlin, kotlinx.coroutines) stehen
unter der Apache-Lizenz 2.0, die mit der GPL Version 3 vereinbar ist.
