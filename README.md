# Telefonhjelp

Et lokalt, norskspråklig supportsystem for en telefonbutikk. Programmet er bygget med Vue/TypeScript og en Java/Spring/SQLite-desktopvert.

Se [databaseskjemaet](docs/database-schema.md) for tabeller, kolonner, relasjoner og indekser.

## Utvikling

Forutsetninger: Node.js 24 og JDK 25 på `PATH`, med `JAVA_HOME` satt til JDK-mappen. Maven Wrapper henter Maven; en separat Maven-installasjon er ikke nødvendig. Produksjonsinstallasjonen inkluderer Java og krever ingen utviklingsverktøy eller nettforbindelse.

```powershell
npm ci
npm run dev
```

Kommandoene kjøres fra prosjektroten. `npm run dev` bruker Vites `mock`-modus med minnedata. Produksjonsbygg bruker HTTP-gatewayen og inkluderer ikke mock-filer. For hele programmet:

```powershell
npm run new-version
```

`new-version` konfigurerer prosjektets lokale Java- og Node-verktøy, bygger siste kildekode og starter skrivebordsprogrammet. Hold terminalvinduet åpent mens programmet kjører.

Den tilsvarende manuelle kommandoen er:

```powershell
$env:JAVA_HOME = 'C:\sti\til\jdk-25'
.\mvnw.cmd compile javafx:run
```

Maven bygger og kopierer Vue-grensesnittet automatisk. JavaFX og øvrige biblioteker hentes av Maven. Data lagres under `%LOCALAPPDATA%\PhoneSupport`.

## PIN-kode

Fra versjon 1.0.4 opprettes en PIN-kode med 4–12 sifre ved første oppstart. PIN-koden må oppgis hver gang skrivebordsprogrammet startes. API-et og saksdata åpnes først etter vellykket opplåsing. Ansattvalg fungerer som før.

Hver gruppe på fem feil PIN-koder gir en sperre på henholdsvis 1 minutt, 2 minutter, 5 minutter, 10 minutter, 30 minutter, 60 minutter, 5 timer og 24 timer. Fem nye feil etter dette gir permanent sperre (45 feil totalt uten vellykket opplåsing). Omstart nullstiller ikke sperren. Riktig PIN-kode etter at ventetiden er over nullstiller hele feilrekken. Under en aktiv sperre kan heller ikke riktig PIN-kode åpne programmet. Permanent sperre har ingen automatisk gjenoppretting.

## Mappestruktur

```text
frontend/
  src/
    components/    Gjenbrukbare UI-komponenter
    data/          Kategorier og enhetskatalog
    domain/        Typer, hjelpefunksjoner og enhetstester
    gateway/       Gateway-kontrakt og HTTP-implementasjon
    mocks/         Minnedatabase og eksempeldata for utvikling
    stores/        Pinia-tilstand
    styles/        SCSS
    views/         Sider
  tsconfig.json    TypeScript-konfigurasjon
  vite.config.ts  Vite- og Vitest-konfigurasjon
src/
  main/java/      JavaFX-vert, API og tjenester
  main/resources/ Innstillinger og Flyway-migreringer
  test/java/      Java-tester
scripts/          Pakking og opprydding
.mvn/             Maven Wrapper-konfigurasjon
.tools/           Lokale utviklingsverktøy (ignorert av Git)
target/           Genererte filer, tester og pakkestaging (ignorert)
releases/         EXE-installere (ignorert; beholdes ved opprydding)
```

`package.json` og låsefilen ligger i roten slik at npm- og Maven-kommandoene kan kjøres fra samme sted. Frontend-konfigurasjonen ligger sammen med frontend-koden. Appversjonen for Windows-pakking leses fra `pom.xml`.

## Kundehistorikk og avslutningsnotater

Ved registrering kan du klikke på antallet tidligere saker for en kjent kunde. På en eksisterende sak velger du «Vis kundens tidligere saker». Historikken åpnes på samme side og viser enhet, problem, status og siste avslutningsnotat, med nyeste sak først. Oppslaget bruker kundens eksakte normaliserte telefonnummer; den åpne saken vises ikke i sin egen kundehistorikk.

«Lukk saken» åpner et felt for avslutningsnotat (påkrevd, maks. 4000 tegn). «Lagre og lukk saken» lagrer notatet og lukker saken samlet. Ved gjenåpning beholdes tidligere notater i historikken, og neste lukking krever et nytt notat. Eldre saker uten notat kan fortsatt leses og gjenåpnes. Notatene inngår i vanlige sikkerhetskopier, men ikke i Excel-rapporter. Ikke skriv passord eller koder i notatene.

Notatene lagres som `RESOLUTION`-hendelser i eksisterende sakshistorikk; funksjonen krever ingen ny databasemigrering.

## Kontroller

```powershell
npm test
.\mvnw.cmd test
npm run typecheck
```

`npm test` kjører de rene domene-testene i Node. `npm run build` lager grensesnittet i `target/frontend`; `.\mvnw.cmd package` lager også Java-appen.

## Demodata

Utviklingsmodusen og nye skrivebordsdatabaser får et representativt datasett for rapporttesting: 8 ansatte (2 deaktiverte), 70 kunder og 140 saker fordelt på åpne, ventende, eskalerte og lukkede saker. Flyway legger datasettet inn én gang via `V3__add_demo_report_data.sql`; eksisterende data beholdes. Velg en rapportperiode som dekker de siste fem dagene for å få med hele datasettet i Excel-rapporten.

Den eksisterende lokale installasjonen ble tømt for saker, kommentarer, sakshistorikk og midlertidige verdier 17. september 2026. Kunder og ansatte ble beholdt. Den allerede utførte demomigreringen kjøres ikke på nytt ved omstart; utviklingsmodus og nye testdatabaser har fortsatt demodata. Tidligere sikkerhetskopier kan fortsatt inneholde gamle saker.

## Windows-pakking

Bygg installasjonsprogrammet med `.\scripts\package.ps1`. Det krever WiX 3 (`candle.exe` og `light.exe`) på `PATH`; resultatet legges i `releases`. Skriptet kjører Java-testene, frontend-testene og kontrollene av byggkommandoer med mindre `-SkipTests` er angitt. En appmappe med runtime kan bygges med `.\scripts\package.ps1 -Type app-image`; den legges i `target/jpackage`.

På denne arbeidskopien finnes et lokalt verktøysett som kan brukes slik:

```powershell
$env:JAVA_HOME = (Resolve-Path '.tools/liberica-full/jdk-25.0.4.1').Path
$nodeTools = (Resolve-Path '.tools/node-v24.19.0-win-x64').Path
$wixTools = (Resolve-Path '.tools/wix314').Path
$env:Path = "$env:JAVA_HOME\bin;$nodeTools;$wixTools;" + $env:Path
.\scripts\package.ps1
```

`.tools` følger ikke med Git. På en ny maskin må utviklingsverktøyene installeres eller pakkes ut først.

## Opprydding

`.\scripts\clean.ps1 -WhatIf` viser hva som ryddes. `.\scripts\clean.ps1` fjerner `target` og eldre genererte byggfiler. Installere i `releases`, kildekode, `node_modules`, `.tools` og programdata i AppData beholdes. De genererte filene kan bygges på nytt med npm/Maven.

## Data og personvern

Vanlige data ligger i `%LOCALAPPDATA%\PhoneSupport\app.db` med private filrettigheter. Midlertidige koder og passord ligger separat i `secrets.db`, er kryptert med Windows DPAPI og utløper feltvis 24 timer etter siste lagring. Opprydding skjer mens programmet kjører. De tas aldri med i historikk, logger, søk, rapporter, Excel eller sikkerhetskopier. Feltene hentes bare når de åpnes eksplisitt og er maskert som standard.

Ved en databaseoppgradering opprettes en konsistent kopi før Flyway migrerer. Manuelle sikkerhetskopier bruker `.thbackup` og krypteres med et passord fra Windows-dialogen. Gjenoppretting krever samme passord, validerer hele databaseskjemaet og aktiveres atomisk ved omstart etter at midlertidige verdier er fjernet. Maksimal størrelse er 64 MiB. Eldre `.db`-kopier med gjeldende skjema støttes fortsatt.

API-et krever en privat nøkkel som opprettes ved hver programstart; det kan ikke lenger brukes direkte fra en vanlig nettleser. `npm run dev` bruker fortsatt minnedata uten nøkkel. Ansattvalg er fortsatt registrering av hvem som er valgt, ikke personlig innlogging.

Se [SECURITY.md](SECURITY.md) for sikkerhetsgrenser, oppbevaringspolicy, diskbeskyttelse, avhengighetskontroller og signering. `scripts/security-check.ps1` kjører bygg, tester og avhengighetskontroller. `scripts/package.ps1 -Release` krever et ferdig konfigurert signeringssertifikat og tidsstempeltjeneste.
