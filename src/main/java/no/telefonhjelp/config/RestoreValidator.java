package no.telefonhjelp.config;

import no.telefonhjelp.service.AppException;
import org.flywaydb.core.Flyway;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** The allowlist comes from bundled migrations, never from a customer database. */
public final class RestoreValidator {
    public static final long MAX_BYTES = 64L * 1024 * 1024;
    private static List<List<String>> expectedSchema;
    private static List<List<String>> expectedMigrations;
    private RestoreValidator() {}

    public static void copyBounded(Path source, Path destination) throws Exception {
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.size(source) < 100 || Files.size(source) > MAX_BYTES) throw invalid();
        try (var input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS); var output = Files.newOutputStream(destination)) {
            var buffer = new byte[8192]; long total = 0; int count;
            while ((count = input.read(buffer)) != -1) {
                total += count; if (total > MAX_BYTES) throw invalid();
                output.write(buffer, 0, count);
            }
            if (total < 100) throw invalid();
        }
    }

    public static void validate(Path staged, Path privateDirectory) throws Exception {
        loadExpected(privateDirectory);
        if (!Files.isRegularFile(staged, LinkOption.NOFOLLOW_LINKS) || Files.size(staged) > MAX_BYTES) throw invalid();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + staged.toUri() + "?mode=ro")) {
            var sqlite = connection.unwrap(org.sqlite.SQLiteConnection.class);
            sqlite.setLimit(org.sqlite.SQLiteLimits.SQLITE_LIMIT_LENGTH, 65_536);
            sqlite.setLimit(org.sqlite.SQLiteLimits.SQLITE_LIMIT_SQL_LENGTH, 65_536);
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(10);
                statement.execute("PRAGMA trusted_schema=OFF");
                statement.execute("PRAGMA query_only=ON");
                if (!rows(connection, SCHEMA_SQL).equals(expectedSchema)) throw invalid();
                if (!rows(connection, MIGRATIONS_SQL).equals(expectedMigrations)) throw invalid();
                try (var result = statement.executeQuery("PRAGMA integrity_check")) {
                    if (!result.next() || !"ok".equals(result.getString(1)) || result.next()) throw invalid();
                }
                try (var result = statement.executeQuery("PRAGMA foreign_key_check")) { if (result.next()) throw invalid(); }
            }
        } catch (AppException exception) { throw exception; }
        catch (Exception exception) { throw invalid(); }
    }

    private static synchronized void loadExpected(Path directory) throws Exception {
        if (expectedSchema != null) return;
        var reference = Files.createTempFile(directory, "schema-reference-", ".db");
        PrivateFiles.restrict(reference);
        try {
            Flyway.configure().dataSource("jdbc:sqlite:" + reference, null, null).locations("classpath:db/migration").load().migrate();
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + reference)) {
                var schema = rows(connection, SCHEMA_SQL);
                expectedMigrations = rows(connection, MIGRATIONS_SQL);
                expectedSchema = schema;
            }
        } finally { Files.deleteIfExists(reference); }
    }

    private static List<List<String>> rows(Connection connection, String sql) throws SQLException {
        var rows = new ArrayList<List<String>>();
        try (var statement = connection.createStatement()) {
            statement.setMaxRows(100); statement.setQueryTimeout(10);
            try (var result = statement.executeQuery(sql)) {
                while (result.next()) {
                    var row = new ArrayList<String>();
                    for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) row.add(result.getString(i));
                    rows.add(row);
                }
            }
        }
        return rows;
    }
    private static AppException invalid() { return AppException.badRequest("Filen er skadet eller har et databaseskjema som denne programversjonen ikke støtter."); }
    private static final String SCHEMA_SQL = "SELECT type,name,tbl_name,sql FROM sqlite_schema ORDER BY type,name";
    private static final String MIGRATIONS_SQL = "SELECT version,type,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank";
}
