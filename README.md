# Telefonhjelp

Et lokalt, norskspråklig supportsystem for en telefonbutikk. Programmet er bygget med Vue/TypeScript og en Java/Spring/SQLite-desktopvert.

## Utvikling

Forutsetninger: Node.js 24 og JDK 25 på `PATH`, med `JAVA_HOME` satt til JDK-mappen. Maven Wrapper henter Maven; en separat Maven-installasjon er ikke nødvendig. Produksjonsinstallasjonen inkluderer Java og krever ingen utviklingsverktøy eller nettforbindelse.

```powershell
npm ci
npm run dev
```

Kommandoene kjøres fra prosjektroten. `npm run dev` bruker Vites `mock`-modus med minnedata. Produksjonsbygg bruker HTTP-gatewayen og inkluderer ikke mock-filer. For hele programmet:

```powershell
$env:JAVA_HOME = 'C:\sti\til\jdk-25'
.\mvnw.cmd compile javafx:run
```

Maven bygger og kopierer Vue-grensesnittet automatisk. JavaFX og øvrige biblioteker hentes av Maven. Data lagres under `%LOCALAPPDATA%\PhoneSupport`.

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

## Kontroller

```powershell
npm test
.\mvnw.cmd test
npm run typecheck
```

`npm test` kjører de rene domene-testene i Node. `npm run build` lager grensesnittet i `target/frontend`; `.\mvnw.cmd package` lager også Java-appen.

## Windows-pakking

Bygg installasjonsprogrammet med `.\scripts\package.ps1`. Det krever WiX 3 (`candle.exe` og `light.exe`) på `PATH`; resultatet legges i `releases`. Skriptet kjører Java-testene med mindre `-SkipTests` er angitt. En appmappe med runtime kan bygges med `.\scripts\package.ps1 -Type app-image`; den legges i `target/jpackage`.

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

Vanlige data ligger i `%LOCALAPPDATA%\PhoneSupport\app.db`. Midlertidige koder og passord ligger separat i `secrets.db`, er kryptert med Windows DPAPI og slettes feltvis 24 timer etter siste lagring. De tas aldri med i historikk, logger, søk, rapporter, Excel eller sikkerhetskopier.

Ved en databaseoppgradering opprettes en automatisk kopi før Flyway migrerer. Manuell sikkerhetskopiering og gjenoppretting bruker Windows-filvelgeren. En gjenoppretting valideres først, aktiveres atomisk ved omstart og sletter alle midlertidige verdier.
