package no.telefonhjelp.service;

import no.telefonhjelp.config.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.nio.file.*;

@Service
public class MaintenanceService {
    private final StoragePaths paths;
    private final JdbcTemplate jdbc;
    private final FileAuthorizations authorizations;
    public MaintenanceService(StoragePaths paths, JdbcTemplate jdbc, FileAuthorizations authorizations) {
        this.paths = paths; this.jdbc = jdbc; this.authorizations = authorizations;
    }

    public synchronized String backup(String authorization) throws Exception {
        try (var grant = authorizations.consume(authorization, FileAuthorizations.Operation.BACKUP)) {
            var snapshot = Files.createTempFile(paths.root(), "backup-", ".db");
            var encrypted = Files.createTempFile(grant.path().getParent(), ".telefonhjelp-", ".tmp");
            try {
                PrivateFiles.restrict(snapshot); PrivateFiles.restrict(encrypted);
                jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
                    try (var statement = connection.prepareStatement("VACUUM INTO ?")) { statement.setString(1, snapshot.toString()); statement.execute(); }
                    return null;
                });
                if (Files.size(snapshot) > RestoreValidator.MAX_BYTES - 100) throw AppException.badRequest("Databasen er for stor for eksport (maksimalt 64 MiB).");
                BackupEncryption.encrypt(snapshot, encrypted, grant.password());
                authorizations.recheck(grant);
                if (grant.overwrite()) Files.move(encrypted, grant.path(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                else Files.move(encrypted, grant.path());
                return "Sikkerhetskopien er lagret.";
            } finally { Files.deleteIfExists(snapshot); Files.deleteIfExists(encrypted); }
        }
    }

    public synchronized String prepareRestore(String authorization) throws Exception {
        try (var grant = authorizations.consume(authorization, FileAuthorizations.Operation.RESTORE)) {
            if (Files.exists(paths.pendingRestore())) throw AppException.conflict("En gjenoppretting venter allerede på omstart.");
            var copied = Files.createTempFile(paths.root(), "restore-source-", ".tmp");
            var staged = Files.createTempFile(paths.root(), "restore-validated-", ".db");
            try {
                PrivateFiles.restrict(copied); PrivateFiles.restrict(staged);
                RestoreValidator.copyBounded(grant.path(), copied);
                if (grant.path().toString().endsWith(".thbackup")) BackupEncryption.decrypt(copied, staged, grant.password());
                else RestoreValidator.copyBounded(copied, staged);
                RestoreValidator.validate(staged, paths.root());
                Files.move(staged, paths.pendingRestore());
                return "Sikkerhetskopien er kontrollert. Start programmet på nytt for å fullføre gjenopprettingen.";
            } finally { Files.deleteIfExists(copied); Files.deleteIfExists(staged); }
        }
    }
}
