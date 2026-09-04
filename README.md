# Telefonhjelp

Et lokalt, norskspråklig supportsystem for en telefonbutikk. Programmet er bygget med Vue/TypeScript og en Java/Spring/SQLite-desktopvert.

## Utvikling

Forutsetninger: Node.js 24 LTS og JDK 25. JavaFX og øvrige biblioteker hentes av Maven. Produksjonsinstallasjonen inkluderer Java og krever ingen utviklingsverktøy eller nettforbindelse.

```powershell
npm install
npm run dev
```

`npm run dev` bruker minnedata. For hele programmet:

```powershell
npm run build
$env:JAVA_HOME = 'C:\sti\til\jdk-25'
.\mvnw.cmd javafx:run
```

Data lagres under `%LOCALAPPDATA%\PhoneSupport`. Vanlige sikkerhetskopier inneholder aldri midlertidig informasjon.

## Kontroller

```powershell
npm test
.\mvnw.cmd test
npm run build
.\mvnw.cmd package
```

Bygg Windows-installasjonsprogrammet med `.\scripts\package.ps1`. Det krever WiX Toolset på `PATH`; resultatet legges i `target\jpackage`. En installerbarhetsuavhengig kontroll av den medfølgende runtime-pakken kan kjøres med `.\scripts\package.ps1 -Type app-image`.

## Data og personvern

Vanlige data ligger i `%LOCALAPPDATA%\PhoneSupport\app.db`. Midlertidige koder og passord ligger separat i `secrets.db`, er kryptert med Windows DPAPI og slettes feltvis 24 timer etter siste lagring. De tas aldri med i historikk, logger, søk, rapporter, Excel eller sikkerhetskopier.

Ved en databaseoppgradering opprettes en automatisk kopi før Flyway migrerer. Manuell sikkerhetskopiering og gjenoppretting bruker Windows-filvelgeren. En gjenoppretting valideres først, aktiveres atomisk ved omstart og sletter alle midlertidige verdier.
