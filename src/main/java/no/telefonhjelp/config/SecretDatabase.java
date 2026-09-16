package no.telefonhjelp.config;

import java.nio.file.*;
import java.sql.*;

public final class SecretDatabase {
    private SecretDatabase() {}

    public static Connection open(String url) throws SQLException {
        var connection = DriverManager.getConnection(url);
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA trusted_schema=OFF");
            statement.execute("PRAGMA secure_delete=ON");
            try (var result = statement.executeQuery("PRAGMA secure_delete")) {
                if (!result.next() || result.getInt(1) != 1) throw new SQLException("Secure deletion unavailable");
            }
            // TRUNCATE keeps rollback recovery and truncates the journal after each commit.
            try (var result = statement.executeQuery("PRAGMA journal_mode=TRUNCATE")) {
                if (!result.next() || !"truncate".equalsIgnoreCase(result.getString(1))) throw new SQLException("Private journal mode unavailable");
            }
            statement.execute("PRAGMA synchronous=FULL");
            statement.execute("PRAGMA temp_store=MEMORY");
            return connection;
        } catch (SQLException exception) { connection.close(); throw exception; }
    }

    /** Called before activation with all application connections closed. Failure leaves restore pending. */
    public static void erase(StoragePaths paths) throws Exception {
        if (Files.exists(paths.secretsDatabase())) {
            try (var connection = open("jdbc:sqlite:" + paths.secretsDatabase()); var statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM temporary_credentials");
                statement.execute("VACUUM");
            }
        }
        // Wipe any legacy journal bytes before unlinking. This is best effort at the filesystem layer,
        // not a promise about SSD remapping, snapshots or external backups.
        for (var suffix : new String[]{"-wal", "-shm", "-journal", ""}) {
            var file = paths.root().resolve("secrets.db" + suffix);
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) continue;
            PrivateFiles.localPath(file);
            try (var channel = java.nio.channels.FileChannel.open(file, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                var remaining = channel.size(); var zeros = java.nio.ByteBuffer.allocate(8192);
                while (remaining > 0) { zeros.clear(); zeros.limit((int) Math.min(remaining, zeros.capacity())); remaining -= channel.write(zeros); }
                channel.force(true);
            }
            Files.delete(file);
        }
    }
}
