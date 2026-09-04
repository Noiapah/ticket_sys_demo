package no.telefonhjelp.config;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public final class StoragePaths {
    private final Path root;

    @Autowired
    public StoragePaths(@Value("${phone.support.data-dir:}") String configuredPath) throws IOException {
        var override = configuredPath == null || configuredPath.isBlank() ? System.getProperty("phone.support.data-dir") : configuredPath;
        var local = System.getenv("LOCALAPPDATA");
        this.root = override != null ? Path.of(override) : Path.of(local != null ? local : System.getProperty("user.home"), "PhoneSupport");
        Files.createDirectories(root);
        Files.createDirectories(backups());
        applyPendingRestore();
    }

    public StoragePaths() throws IOException { this(System.getProperty("phone.support.data-dir", "")); }

    public Path root() { return root; }
    public Path database() { return root.resolve("app.db"); }
    public Path secretsDatabase() { return root.resolve("secrets.db"); }
    public Path lockFile() { return root.resolve("app.lock"); }
    public Path backups() { return root.resolve("backups"); }
    public Path pendingRestore() { return root.resolve("restore.pending"); }

    private void applyPendingRestore() throws IOException {
        var pending = pendingRestore();
        if (!Files.isRegularFile(pending)) return;
        var database = database();
        if (Files.isRegularFile(database) && Files.size(database) > 0) {
            var stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Files.copy(database, backups().resolve("pre-restore-" + stamp + ".db"), StandardCopyOption.COPY_ATTRIBUTES);
        }
        moveReplacing(pending, database);
        Files.deleteIfExists(secretsDatabase());
        Files.deleteIfExists(root.resolve("secrets.db-wal"));
        Files.deleteIfExists(root.resolve("secrets.db-shm"));
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
