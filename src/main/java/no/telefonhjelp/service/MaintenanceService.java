package no.telefonhjelp.service;

import no.telefonhjelp.config.StoragePaths;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class MaintenanceService {
    private final StoragePaths paths;
    private final JdbcTemplate jdbc;
    public MaintenanceService(StoragePaths paths, JdbcTemplate jdbc) { this.paths=paths; this.jdbc=jdbc; }
    public synchronized String backup(String destination) throws Exception {
        jdbc.execute("PRAGMA wal_checkpoint(FULL)");
        var name = "telefonhjelp-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".db";
        var target = destination == null || destination.isBlank() ? paths.backups().resolve(name) : Path.of(destination).toAbsolutePath().normalize();
        if (Files.isDirectory(target)) target = target.resolve(name);
        if (target.getParent() != null) Files.createDirectories(target.getParent());
        Files.copy(paths.database(), target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        return "Sikkerhetskopi lagret: " + target;
    }

    public synchronized String prepareRestore(String sourcePath) throws Exception {
        if (sourcePath == null || sourcePath.isBlank()) throw AppException.badRequest("Velg en sikkerhetskopi som skal gjenopprettes.");
        var source = Path.of(sourcePath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(source) || Files.size(source) < 100) throw AppException.badRequest("Filen er ikke en gyldig sikkerhetskopi.");
        validateSchema(source);
        var temporary = paths.root().resolve("restore.pending.tmp");
        Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        try {
            Files.move(temporary, paths.pendingRestore(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, paths.pendingRestore(), StandardCopyOption.REPLACE_EXISTING);
        }
        return "Sikkerhetskopien er kontrollert. Programmet må startes på nytt for å fullføre gjenopprettingen.";
    }

    private void validateSchema(Path source) throws Exception {
        Integer supported = jdbc.queryForObject("SELECT MAX(version) FROM flyway_schema_history WHERE success=1", Integer.class);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + source)) {
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA query_only=ON");
                try (var result = statement.executeQuery("SELECT MAX(version) FROM flyway_schema_history WHERE success=1")) {
                    if (!result.next() || result.getObject(1) == null) throw AppException.badRequest("Sikkerhetskopien mangler skjemainformasjon.");
                    var version = result.getInt(1);
                    if (supported == null || version > supported) throw AppException.badRequest("Sikkerhetskopien er laget av en nyere programversjon.");
                }
                for (var table : new String[]{"employees", "customers", "tickets", "comments", "ticket_history", "app_settings"}) {
                    try (var result = statement.executeQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
                        if (!result.next()) throw AppException.badRequest("Sikkerhetskopien mangler nødvendige data.");
                    }
                }
            }
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.badRequest("Filen kunne ikke leses som en Telefonhjelp-sikkerhetskopi.");
        }
    }
}
