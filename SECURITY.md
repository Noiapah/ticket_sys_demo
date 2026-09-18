# Security and operating requirements

The desktop application uses a separate, random 256-bit credential for each process. Every API route, including bootstrap, customer lookup and Excel downloads, requires it. The credential reaches the trusted page through the JavaFX bridge and stays in a JavaScript module; it is not stored in a cookie, browser storage, URL, HTTP bootstrap response or log. The server binds to `127.0.0.1` on an ephemeral port, checks the exact Host and Origin, and requires the expected Origin on writes. A custom credential header and the absence of CORS permissions prevent unauthenticated cross-origin writes.

The native bridge is attached only to the exact application page and hash routes. Unexpected navigation is cancelled, popups are disabled, and bridge methods recheck the page before operating. Responses include a Content Security Policy, framing denial, MIME sniffing protection and `Cache-Control: no-store`.

## Identity and workstation access

This remains a desktop application for one trusted Windows account; individual employee sign-in is outside the current deployment scope. Employee selection records the selected employee; **it is not individual authentication or reliable proof of who performed an action**. Everyone who unlocks with the shared application PIN has the same application privileges. Deployments requiring individual accountability must add employee sign-in, session-derived actors and role-based permissions for administration, secrets, exports and restore before using a shared account. The application credential does not protect against malware, administrators or a fully compromised Windows account.

The application data directory and its existing contents receive an owner-only Windows ACL (or owner-only POSIX permissions on supported test filesystems). Filesystems without private permissions, network paths, junctions and symbolic links are rejected. Use Windows device encryption/BitLocker for the data volume: `app.db` and internal recovery copies remain ordinary SQLite databases. ACLs do not protect a stolen, unencrypted drive. No machine-wide encryption settings are changed by the application.

## Application PIN

Version 1.0.4 adds a shared application PIN, chosen and confirmed in a native window on first launch. PINs contain 4–12 digits, including leading zeroes. The desktop launcher acquires its instance lock, then waits for PIN setup or successful verification before starting Spring, opening the database, or serving HTTP. PIN values never pass through the web page, API, command line or logs. Closing and restarting requires the PIN again; there is no additional idle lock in this version.

Five consecutive failed attempts advance the lockout schedule below. Failed attempts are counted across restarts and waiting periods. Submissions during an active lockout are rejected without changing the count; even the correct PIN is rejected until the wait ends. A successful unlock then resets both the failure count and escalation level.

| Failed attempts without a successful unlock | Lockout |
| --- | --- |
| 5 | 1 minute |
| 10 | 2 minutes |
| 15 | 5 minutes |
| 20 | 10 minutes |
| 25 | 30 minutes |
| 30 | 60 minutes |
| 35 | 5 hours |
| 40 | 24 hours |
| 45 | Permanent |

Permanent lockout has no time-based reset or PIN-based recovery. There is no default PIN or reset endpoint. The PIN verifier uses PBKDF2-HMAC-SHA256 with a random 32-byte salt and 600,000 iterations. The verifier and counters are protected with Windows user-scoped DPAPI in `pin-access.dat`, with owner-only permissions and atomic writes. Attempts must be saved before their results are accepted. A missing state file after setup (tracked by `pin-access.initialized`) or an unreadable/tampered state fails closed. Neither file is part of a ticket backup or restore, so restoring an older ticket database does not reset the PIN or lockout.

This is an application access gate, not encryption of customer records or protection from an attacker controlling the Windows account. Such an attacker can still access the databases, replace the application or restore/delete authentication state. Timed lockouts use the computer's clock and are not a defense against clock manipulation by a privileged attacker.

## Backup and restore

- Native selection issues a random authorization valid for 60 seconds and one operation. The API accepts that authorization, never an arbitrary path. A changed destination, expired/replayed grant or wrong operation is rejected. Paths inside the data directory and aliases of application databases, locks or staged restores are excluded. Network drives, symlinks and junctions are rejected. Export replaces a directory entry rather than writing through an existing hard link; overwrite requires an explicit native confirmation.
- New exported backups use `.thbackup`, AES-256-GCM and a fresh salt/nonce. The key is derived with PBKDF2-HMAC-SHA256 (600,000 iterations) from a password entered and confirmed in a native dialog. Passwords require 12–256 characters and never pass through the web page/API. Keep the password separately: there is no password recovery. The format is `THBACKUP1`, 16-byte salt, 12-byte nonce, authenticated ciphertext including its 16-byte tag; the magic is authenticated as associated data.
- SQLite `VACUUM INTO` produces consistent snapshots, including for automatic migration/recovery backups. Temporary secrets are stored in a separate database and never included. Exports and restore inputs are limited to 64 MiB.
- Restore first copies into the private data directory with a streaming size limit. Encrypted data must authenticate successfully before SQLite opens it. The staged database is opened read-only with `trusted_schema=OFF`; integrity, foreign keys, the complete schema SQL (tables, columns, indexes, views and triggers) and migration versions/checksums must match the bundled migrations. The schema allowlist is generated from a fresh reference database, not from the running or restored database.
- Only the current supported schema is accepted. Legacy `.db` backups with that schema can be imported; older schema versions need a controlled upgrade with a compatible application first. Unknown schema is rejected rather than executed. Schema checks establish structure, not the truth of customer data or the identity of a backup's author.
- At startup, after the desktop instance lock is acquired and before pooled database connections open, the staged artifact is checked again. Secret cleanup must succeed before atomic replacement of `app.db`. A validation/cleanup failure retains the pending restore and the previous application database for retry. Correct the filesystem problem and restart; to cancel, close the app and remove only `restore.pending`. A recovery copy of the old ordinary database is retained under `backups`.

## Temporary secrets

Every secret-database connection enables and verifies `secure_delete=ON`, uses a truncating rollback journal with full synchronization, and keeps SQLite temporary storage in memory. Startup runs `VACUUM` to remove free pages left by older versions. Expiry, replacement and manual deletion all use these connections. Expiry is checked when the application services start after PIN entry, on read, and every 15 minutes while those services run. Cleanup does not run while the program is waiting at the PIN screen.

Opening a ticket does not fetch decrypted values. The user opens temporary fields explicitly; values are masked until the separate “Vis verdier” action. The UI clears values at expiry (checked every second and on focus), after five minutes without keyboard/pointer activity, on blur/hidden page, employee change, route change, session rejection and unmount. Late network responses cannot repopulate cleared fields. Secret records redact their string representation to reduce accidental debug logging.

These controls reduce recoverable bytes in the live database and journal. They cannot guarantee physical erasure from SSD remapping, filesystem snapshots, old backups or process memory. DPAPI itself has no expiry, and cleanup cannot run while the app is closed. See [SQLite secure deletion](https://www.sqlite.org/pragma.html#pragma_secure_delete) and [VACUUM](https://sqlite.org/lang_vacuum.html).

## Resource limits

API JSON bodies are limited to 64 KiB including chunked requests; compressed request bodies are rejected. There are at most 16 active API requests and one active report request, with bounded server connections and timeouts. Ticket pages default to 50 items and allow at most 100, without comments/history; those are fetched on the ticket detail page. Page indices are bounded at 10,000.

Names/models, phone numbers, categories, descriptions and comments have explicit limits (120–200, 40, 120 and 4,000 characters respectively). There are at most 16 credential fields per ticket, each at most 1,024 characters, and at most 1,000 employees. Reports require ordered dates within 2000–2100, span at most 366 days, and reject periods containing more than 10,000 created/closed tickets. Report queries have a timeout and row cap. Public errors use stable messages; unexpected failures have a correlation ID without request bodies or raw exception messages.

## Retention policy

The default operating policy is to retain manual backups and internal recovery copies for 30 days after a replacement backup has been verified. Remove expired copies from export locations, synced folders and external media as part of the same process. Keep at least one verified recovery copy until replacement has been tested. Review closed customer records after 12 months and retain only records still needed for support; the operator must establish any different business retention requirement before deployment. This policy is documented, not an automatic deletion job: this update does not silently delete existing customer records or backups. A customer-record purge workflow remains an operational follow-up if automated retention is required.

## Dependency checks and releases

JavaFX is pinned to 25.0.4 ([vendor release notes](https://gluonhq.com/products/javafx/openjfx-25-release-notes/)), Tomcat to 11.0.25 ([Apache security fixes](https://tomcat.apache.org/security-11.html)), and Vitest to 4.1.11 ([maintainer advisory](https://github.com/vitest-dev/vitest/security/advisories/GHSA-82fw-gwwq-j7x9)).

Run `scripts/security-check.ps1` with the documented Java/Node toolchain to build/test, generate a CycloneDX Java SBOM including test dependencies, check all Maven coordinates against [OSV](https://google.github.io/osv.dev/post-v1-querybatch/), run npm audit and generate the frontend runtime SBOM. Any Java finding, high/critical npm finding or failed advisory lookup fails the check. Outputs are `target/sbom-java.json`, `target/sbom-frontend.json` and `target/osv-java-audit.json`. The optional Maven `security-audit` profile runs OWASP Dependency-Check as an additional NVD-based check; it requires a valid `NVD_API_KEY` environment variable and network access. An incomplete NVD update is a failure, never a clean result. Package-coordinate scanners do not establish exhaustive coverage of bundled WebKit/native code; follow vendor maintenance releases too.

Both verification and packaging run the Java and frontend test suites unless `-SkipTests` is explicitly selected for a development build. Native commands fail on nonzero exit codes while keeping stderr warnings separate from captured stdout, so redirecting a JDK warning to a log does not abort a successful build or corrupt SBOM JSON. `scripts/test-native-command.ps1` checks these behaviors and missing executables.

`scripts/package.ps1 -Release` requires tests, dependency checks, a signing certificate thumbprint in `PHONE_SUPPORT_SIGNING_THUMBPRINT`, a timestamp service URL in `PHONE_SUPPORT_TIMESTAMP_URL`, and Windows SDK `signtool.exe` on PATH. It signs/verifies both the application launcher and installer with SHA-256 and includes both SBOMs. The certificate/private key must already be provisioned in the Windows certificate store. Development builds without `-Release` may be unsigned. Signing follows [Microsoft SignTool guidance](https://learn.microsoft.com/en-us/windows/win32/seccrypto/signtool); no certificate is generated or purchased by this change.

## Implementation verification — 16 September 2026

All 30 Java tests and 14 frontend tests passed. Tests cover anonymous API access, Host/Origin enforcement, path-normalization bypass attempts, chunked request limits, native grant replay/expiry/path changes, encrypted backups, tampered schema and foreign keys, restore cleanup failure/retry, ciphertext deletion, pagination and late secret responses. The JavaFX test loads the production bundle in an invisible WebView window and verifies private bootstrap, explicit masked reveal, session clearing, CSP enforcement and navigation recovery.

The production frontend, Java package and Windows app image built successfully with the test suites enabled. An unsigned Windows EXE installer was also generated from that image under `target/installer-verification`; it was not installed. Both CycloneDX SBOMs were included in the app image and parsed successfully: 104 Java components (98 distinct Maven package versions, including test dependencies) and 16 frontend runtime components. Refreshed OSV and npm audits returned zero findings. Native-command regression checks passed with output redirected to a log, including stderr separation and rejection of failed or missing commands.

OWASP Dependency-Check could not populate its NVD database without a valid API key and is not counted as a completed scan. Production signing was not tested with a real signing certificate. Existing installed application data and workstation encryption settings were not used or changed during verification; tests used disposable databases. The agreed deployment continues to use Windows-account access and employee selection.

## PIN and ticket reset verification — 17 September 2026

All 36 Java tests and 14 frontend tests passed for version 1.0.4. PIN tests cover all nine lockout stages with a controlled clock, restart persistence after every attempt, correct PIN rejection during a lockout, permanent lock after 45 failures, success resetting the sequence, invalid setup, tampered/missing state and storage failure. The native UI test covers setup confirmation, leading zeroes, failed attempts, countdown and successful unlock. Separate invisible-window startup checks confirmed that the actual application starts no backend or database before PIN entry, and loads the production dashboard after successful entry. The unsigned Windows installer was built at `releases/Telefonhjelp-1.0.4.exe`; it has not been installed automatically. Refreshed OSV and npm audits returned zero findings.

At the operator's explicit request, the existing local database was cleared under its instance lock in a transaction with secure deletion enabled: 141 tickets, 35 comments and 302 history entries were removed. A final read-only check found zero tickets, comments, history entries and temporary credentials, while preserving 71 customers and 9 employees. Existing backup files were not deleted and may retain earlier records. This was a one-time maintenance operation, not a destructive application migration; existing installations do not re-run the demo seed migration after their tickets are cleared.
