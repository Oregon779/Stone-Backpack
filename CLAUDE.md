# REGELN FÜR DIE VERSIONIERUNG & BUILD-ERSTELLUNG

Für dieses Minecraft-Plugin trennen wir strikt zwischen Entwicklungs-Builds (Dev-Builds) und Release-Builds für Modrinth. Halte dich bei jeder Änderung an der Codebase zwingend an folgenden Workflow:

1. **Stabile Hauptversion:** Die aktuelle Hauptversionsnummer (z. B. `1.1`) darf von dir NIEMALS selbstständig geändert oder erhöht werden.
2. **Das Dev-Suffix:** Für alle laufenden Änderungen, Fehlerbehebungen und Code-Tests nutzt du ein Entwickler-Suffix. Das Format ist `[Hauptversion]-dev[Nummer]` (Beispiel: `1.1-dev1`, `1.1-dev2`).
3. **Automatisches Hochzählen:** Bei jedem neuen Build, den du generierst, zählst du *ausschließlich* die Nummer des Dev-Suffixes um +1 hoch. Aktualisiere diese Version in allen relevanten Dateien (z.B. `plugin.yml` und `build.gradle` bzw. `pom.xml`).
4. **Der Release-Befehl:** Erst wenn ich ausdrücklich den Befehl **"Baue das Release"** gebe, entfernst du das `-dev`-Suffix vollständig. Du trägst dann die reine Hauptversion (z.B. `1.1`) in die Dateien ein und erstellst die finale, saubere `.jar`-Datei.
5. **Nächster Zyklus:** Nachdem das Release gebaut wurde und neue Änderungen anstehen, fragst du mich, wie die nächste Hauptversion lauten soll, und wir starten wieder bei `-dev1`.

## Umsetzung in diesem Projekt

- Die Version steht an genau zwei Stellen, die immer übereinstimmen müssen:
  - `pom.xml`: das `<version>` des Projekts direkt unter `<artifactId>` (nicht die Versionen von Abhängigkeiten oder Maven-Plugins)
  - `src/main/resources/plugin.yml`: `version: '...'`
- Der Name der JAR ergibt sich daraus automatisch (`finalName` = `${project.name}-${project.version}`), z. B. `target/StoneBackpack-1.5.0-dev2.jar`.
- In diesen Dateien steht immer die Version der zuletzt gebauten JAR. Steht dort `X-devN`, wird der nächste Build `X-dev(N+1)`. Steht dort die reine Hauptversion `X`, wurde sie als Release gebaut: Vor neuen Änderungen fragst du nach der nächsten Hauptversion (Regel 5).
- Als Build zählt jede JAR, die du am Ende einer Änderungsrunde an mich auslieferst. Builds, die während der Arbeit nur zum Kompilieren und Testen laufen, zählen nicht. Die erhöhte Version wird zusammen mit der Änderung committet und gepusht.
- Die Hauptversion ist hier dreistellig (z. B. `1.5.0`).
- `-devN` stört den Update-Checker nicht: `UpdateChecker.isNewer` liest pro Teil nur die führenden Ziffern, `1.5.0-dev3` zählt dort wie `1.5.0`.
