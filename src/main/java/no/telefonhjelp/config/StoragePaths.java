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
        this.root = (override != null && !override.isBlank() ? Path.of(override) : Path.of(local != null ? local : System.getProperty("user.home"), "PhoneSupport")).toAbsolutePath().normalize();
        PrivateFiles.localPath(root);
        Files.createDirectories(root);
        PrivateFiles.restrict(root);
        Files.createDirectories(backups());
        try (var files = Files.walk(root)) {
            for (var file : files.toList()) { PrivateFiles.localPath(file); PrivateFiles.restrict(file); }
        }
    }

    public StoragePaths() throws IOException { this(System.getProperty("phone.support.data-dir", "")); }

    public Path root() { return root; }
    public Path database() { return root.resolve("app.db"); }
    public Path secretsDatabase() { return root.resolve("secrets.db"); }
    public Path lockFile() { return root.resolve("app.lock"); }
    public Path backups() { return root.resolve("backups"); }
    public Path pendingRestore() { return root.resolve("restore.pending"); }

    public void applyPendingRestore() throws Exception {
        var pending = pendingRestore();
        if (!Files.exists(pending, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        PrivateFiles.localPath(pending);
        RestoreValidator.validate(pending, root);
        var database = database();
        if (Files.isRegularFile(database) && Files.size(database) > 0) {
            var stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            snapshot(database, backups().resolve("pre-restore-" + stamp + "-" + java.util.UUID.randomUUID() + ".db"));
        }
        SecretDatabase.erase(this);
        // Old sidecars must never be replayed against the replacement database.
        for (var suffix : new String[]{"-wal", "-shm", "-journal"}) Files.deleteIfExists(root.resolve("app.db" + suffix));
        moveReplacing(pending, database);
        PrivateFiles.restrict(database);
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void snapshot(Path source, Path target) throws Exception {
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + source); var statement = connection.prepareStatement("VACUUM INTO ?")) {
            statement.setString(1, target.toString()); statement.execute();
        }
        PrivateFiles.restrict(target);
    }
}
