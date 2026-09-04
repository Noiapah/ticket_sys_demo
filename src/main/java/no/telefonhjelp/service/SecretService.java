package no.telefonhjelp.service;

import com.sun.jna.platform.win32.Crypt32Util;
import jakarta.annotation.PostConstruct;
import no.telefonhjelp.config.StoragePaths;
import no.telefonhjelp.domain.ApiModels.TemporaryCredential;
import no.telefonhjelp.domain.ApiModels.TemporaryValue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class SecretService {
    private final String url;
    private final Clock clock;
    public SecretService(StoragePaths paths, Clock clock) { this.url = "jdbc:sqlite:" + paths.secretsDatabase().toAbsolutePath(); this.clock = clock; }

    @PostConstruct
    void initialize() throws Exception {
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS temporary_credentials(ticket_id INTEGER NOT NULL, credential_key TEXT NOT NULL, label TEXT NOT NULL, encrypted_value BLOB NOT NULL, expires_at TEXT NOT NULL, PRIMARY KEY(ticket_id, credential_key))");
        }
        purgeExpired();
    }

    public synchronized List<TemporaryCredential> list(long ticketId) throws Exception {
        purgeExpired();
        var values = new ArrayList<TemporaryCredential>();
        try (var connection = DriverManager.getConnection(url); var statement = connection.prepareStatement("SELECT credential_key,label,encrypted_value,expires_at FROM temporary_credentials WHERE ticket_id=? ORDER BY credential_key")) {
            statement.setLong(1, ticketId);
            try (var result = statement.executeQuery()) {
                while (result.next()) values.add(new TemporaryCredential(result.getString(1), result.getString(2), decrypt(result.getBytes(3)), Instant.parse(result.getString(4))));
            }
        }
        return values;
    }

    public synchronized List<TemporaryCredential> replace(long ticketId, List<TemporaryValue> values) throws Exception {
        var expires = Instant.now(clock).plus(24, ChronoUnit.HOURS).toString();
        try (var connection = DriverManager.getConnection(url)) {
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("INSERT INTO temporary_credentials(ticket_id,credential_key,label,encrypted_value,expires_at) VALUES (?,?,?,?,?) ON CONFLICT(ticket_id,credential_key) DO UPDATE SET label=excluded.label,encrypted_value=excluded.encrypted_value,expires_at=excluded.expires_at");
                 var delete = connection.prepareStatement("DELETE FROM temporary_credentials WHERE ticket_id=? AND credential_key=?")) {
                for (var value : values) {
                    var key = safeKey(value.key());
                    if (value.value() == null || value.value().isBlank()) {
                        delete.setLong(1, ticketId); delete.setString(2, key); delete.addBatch();
                    } else {
                        insert.setLong(1, ticketId); insert.setString(2, key); insert.setString(3, safeLabel(value.label())); insert.setBytes(4, encrypt(value.value().trim())); insert.setString(5, expires); insert.addBatch();
                    }
                }
                insert.executeBatch();
                delete.executeBatch();
            }
            connection.commit();
        }
        return list(ticketId);
    }

    public synchronized void clear(long ticketId, String key) throws Exception {
        var sql = key == null ? "DELETE FROM temporary_credentials WHERE ticket_id=?" : "DELETE FROM temporary_credentials WHERE ticket_id=? AND credential_key=?";
        try (var connection = DriverManager.getConnection(url); var statement = connection.prepareStatement(sql)) { statement.setLong(1, ticketId); if (key != null) statement.setString(2, key); statement.executeUpdate(); }
    }

    @Scheduled(fixedDelay = 900_000)
    public synchronized void purgeExpired() throws Exception {
        try (var connection = DriverManager.getConnection(url); var statement = connection.prepareStatement("DELETE FROM temporary_credentials WHERE expires_at<=?")) { statement.setString(1, Instant.now(clock).toString()); statement.executeUpdate(); }
    }

    public synchronized void clearAll() throws Exception {
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) { statement.executeUpdate("DELETE FROM temporary_credentials"); }
    }

    private byte[] encrypt(String value) { return Crypt32Util.cryptProtectData(value.getBytes(StandardCharsets.UTF_8)); }
    private String decrypt(byte[] value) { return new String(Crypt32Util.cryptUnprotectData(value), StandardCharsets.UTF_8); }
    private static String safeKey(String value) { if (value == null || !value.matches("[A-Za-z0-9_-]{1,40}")) throw AppException.badRequest("Ugyldig feltnøkkel."); return value; }
    private static String safeLabel(String value) { if (value == null || value.isBlank() || value.length() > 80) throw AppException.badRequest("Ugyldig feltnavn."); return value.trim(); }
}
