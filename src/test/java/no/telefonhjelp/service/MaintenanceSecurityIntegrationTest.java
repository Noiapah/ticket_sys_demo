package no.telefonhjelp.service;

import no.telefonhjelp.PhoneSupportApplication;
import no.telefonhjelp.config.*;
import no.telefonhjelp.domain.ApiModels.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = PhoneSupportApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MaintenanceSecurityIntegrationTest {
    @TempDir static Path data;
    @TempDir Path exports;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("phone.support.data-dir", data::toString); registry.add("spring.main.web-application-type", () -> "none"); }
    @Autowired StoragePaths paths;
    @Autowired MaintenanceService maintenance;
    @Autowired FileAuthorizations authorizations;
    @Autowired TicketService tickets;
    @Autowired JdbcTemplate jdbc;
    private char[] password() { return "test-only-backup-password".toCharArray(); }
    private Path legacyBackup() throws Exception {
        var target = exports.resolve(java.util.UUID.randomUUID() + ".db");
        StoragePaths.snapshot(paths.database(), target);
        return target;
    }

    @Test void nativeGrantsAreRequiredBoundToAnOperationAndSingleUse() throws Exception {
        var target = exports.resolve("backup.thbackup");
        Files.writeString(target, "KEEP THIS FILE");
        assertThatThrownBy(() -> maintenance.backup(target.toString())).isInstanceOf(AppException.class);
        assertThat(Files.readString(target)).isEqualTo("KEEP THIS FILE");
        assertThatThrownBy(() -> authorizations.issue(target, FileAuthorizations.Operation.BACKUP, false, password())).isInstanceOf(AppException.class);
        var wrongOperation = authorizations.issue(target, FileAuthorizations.Operation.BACKUP, true, password());
        assertThatThrownBy(() -> maintenance.prepareRestore(wrongOperation)).isInstanceOf(AppException.class);
        var grant = authorizations.issue(target, FileAuthorizations.Operation.BACKUP, true, password());
        maintenance.backup(grant);
        assertThatThrownBy(() -> maintenance.backup(grant)).isInstanceOf(AppException.class);
        var plain = exports.resolve("decrypted.db"); Files.createFile(plain);
        BackupEncryption.decrypt(target, plain, password());
        RestoreValidator.validate(plain, paths.root());
        assertThat(new String(Files.readAllBytes(target), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("SQLite format", "customers", "temporary_credentials");
        assertThatThrownBy(() -> BackupEncryption.decrypt(target, plain, "incorrect-password".toCharArray())).isInstanceOf(AppException.class);
    }

    @Test void rejectsExpiredSelectionsInternalFilesAndChangedDestinations() throws Exception {
        var clock = new TestClock(); var grants = new FileAuthorizations(paths, clock);
        var token = grants.issue(exports.resolve("expired.thbackup"), FileAuthorizations.Operation.BACKUP, false, password());
        clock.now = clock.now.plusSeconds(60);
        assertThatThrownBy(() -> grants.consume(token, FileAuthorizations.Operation.BACKUP)).isInstanceOf(AppException.class);
        for (var file : List.of(paths.database(), paths.secretsDatabase(), paths.lockFile(), paths.pendingRestore(), paths.backups().resolve("x.thbackup"))) {
            assertThatThrownBy(() -> authorizations.issue(file, FileAuthorizations.Operation.BACKUP, true, password())).isInstanceOf(AppException.class);
        }
        var target = exports.resolve("changed.thbackup");
        var grant = authorizations.issue(target, FileAuthorizations.Operation.BACKUP, false, password());
        Files.writeString(target, "CREATED AFTER SELECTION");
        assertThatThrownBy(() -> maintenance.backup(grant)).isInstanceOf(AppException.class);
        assertThat(Files.readString(target)).isEqualTo("CREATED AFTER SELECTION");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "CREATE TRIGGER injected AFTER INSERT ON comments BEGIN UPDATE app_settings SET setting_value='999'; END",
        "CREATE VIEW injected AS SELECT * FROM customers",
        "CREATE INDEX injected ON customers(name)",
        "ALTER TABLE customers ADD COLUMN injected TEXT",
        "UPDATE tickets SET customer_id=999999 WHERE id=1",
        "UPDATE flyway_schema_history SET checksum=0 WHERE version='3'"
    })
    void rejectsTamperedSchemaMigrationHistoryAndForeignKeys(String sql) throws Exception {
        var backup = legacyBackup();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + backup); var statement = connection.createStatement()) { statement.execute(sql); }
        var grant = authorizations.issue(backup, FileAuthorizations.Operation.RESTORE, false, new char[0]);
        assertThatThrownBy(() -> maintenance.prepareRestore(grant)).isInstanceOf(AppException.class);
        assertThat(Files.exists(paths.pendingRestore())).isFalse();
    }

    @Test void stagingCopiesSourceAndActivationValidatesAgainBeforeChangingData() throws Exception {
        var isolated = new StoragePaths(exports.resolve("isolated").toString());
        var original = legacyBackup();
        Files.copy(original, isolated.database());
        Files.copy(original, isolated.pendingRestore());
        var originalBytes = Files.readAllBytes(isolated.database());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + isolated.pendingRestore()); var statement = connection.createStatement()) { statement.execute("CREATE TRIGGER injected AFTER INSERT ON comments BEGIN SELECT 1; END"); }
        assertThatThrownBy(isolated::applyPendingRestore).isInstanceOf(AppException.class);
        assertThat(Files.readAllBytes(isolated.database())).isEqualTo(originalBytes);
        assertThat(Files.exists(isolated.pendingRestore())).isTrue();
    }

    @Test void secretCleanupFailureIsRecoverableAndPrecedesDatabaseReplacement() throws Exception {
        var isolated = new StoragePaths(exports.resolve("activation").toString());
        var backup = legacyBackup();
        Files.copy(backup, isolated.pendingRestore());
        Files.copy(backup, isolated.database());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + isolated.database()); var statement = connection.createStatement()) { statement.execute("UPDATE employees SET name='OLD-DATABASE' WHERE id=1"); }
        var before = Files.readAllBytes(isolated.database());
        var secrets = new SecretService(isolated, Clock.systemUTC()); secrets.initialize();
        secrets.replace(1, List.of(new TemporaryValue("code", "Code", "secret")));
        // An unexpected sidecar directory forces cleanup to fail without touching app.db.
        var obstacle = isolated.root().resolve("secrets.db-shm"); Files.createDirectory(obstacle);
        assertThatThrownBy(isolated::applyPendingRestore).isInstanceOf(Exception.class);
        assertThat(Files.readAllBytes(isolated.database())).isEqualTo(before);
        assertThat(Files.exists(isolated.pendingRestore())).isTrue();
        Files.delete(obstacle);
        isolated.applyPendingRestore();
        assertThat(Files.exists(isolated.pendingRestore())).isFalse();
        assertThat(Files.exists(isolated.secretsDatabase())).isFalse();
        RestoreValidator.validate(isolated.database(), isolated.root());
    }

    @Test void successfulStagingIsIndependentOfSubsequentSourceChanges() throws Exception {
        var backup = legacyBackup();
        try {
            maintenance.prepareRestore(authorizations.issue(backup, FileAuthorizations.Operation.RESTORE, false, new char[0]));
            Files.writeString(backup, "changed source");
            RestoreValidator.validate(paths.pendingRestore(), paths.root());
        } finally { Files.deleteIfExists(paths.pendingRestore()); }
    }

    @Test void ticketPagesAreBoundedAndContainNoChildCollections() {
        var first = tickets.list("all", null, null, null, null, null, 0, 10);
        var next = tickets.list("all", null, null, null, null, null, 1, 10);
        assertThat(first).hasSize(10); assertThat(next).hasSize(10);
        assertThat(first).extracting(Ticket::id).doesNotContainAnyElementsOf(next.stream().map(Ticket::id).toList());
        assertThat(first).allSatisfy(ticket -> { assertThat(ticket.comments()).isEmpty(); assertThat(ticket.history()).isEmpty(); });
    }

    private static class TestClock extends Clock {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
