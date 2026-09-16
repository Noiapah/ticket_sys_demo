package no.telefonhjelp.service;

import no.telefonhjelp.config.StoragePaths;
import no.telefonhjelp.domain.ApiModels.TemporaryValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretServiceTest {
    @TempDir Path data;

    @Test
    void deletionRemovesCiphertextFromDatabaseAndTruncatesJournal() throws Exception {
        var paths = new StoragePaths(data.toString());
        var service = new SecretService(paths, Clock.systemUTC()); service.initialize();
        service.replace(42, List.of(new TemporaryValue("code", "Code", "ERASE-THIS-SECRET")));
        byte[] cipher;
        try (var connection = no.telefonhjelp.config.SecretDatabase.open("jdbc:sqlite:" + paths.secretsDatabase()); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("PRAGMA secure_delete")) { assertThat(result.getInt(1)).isEqualTo(1); }
            try (var result = statement.executeQuery("SELECT encrypted_value FROM temporary_credentials")) { cipher = result.getBytes(1); }
        }
        var encoded = new String(cipher, StandardCharsets.ISO_8859_1);
        assertThat(new String(Files.readAllBytes(paths.secretsDatabase()), StandardCharsets.ISO_8859_1)).contains(encoded);
        service.clear(42, null);
        assertThat(new String(Files.readAllBytes(paths.secretsDatabase()), StandardCharsets.ISO_8859_1)).doesNotContain(encoded);
        var journal = data.resolve("secrets.db-journal");
        assertThat(!Files.exists(journal) || Files.size(journal) == 0).isTrue();
        assertThat(Files.exists(data.resolve("secrets.db-wal"))).isFalse();
    }

    @Test
    void rejectsOversizedAndDuplicateSecretFields() throws Exception {
        var service = new SecretService(new StoragePaths(data.toString()), Clock.systemUTC()); service.initialize();
        var value = new TemporaryValue("code", "Code", "x".repeat(1025));
        assertThatThrownBy(() -> service.replace(1, List.of(value))).isInstanceOf(AppException.class);
        var valid = new TemporaryValue("code", "Code", "1234");
        assertThatThrownBy(() -> service.replace(1, List.of(valid, valid))).isInstanceOf(AppException.class);
        assertThat(service.list(1)).isEmpty();
    }

    @Test
    void eachValueExpiresFromItsOwnLastSaveAndDatabaseDoesNotContainPlaintext() throws Exception {
        var clock = new MutableClock(Instant.parse("2026-09-04T10:00:00Z"));
        var paths = new StoragePaths(data.toString());
        var service = new SecretService(paths, clock);
        service.initialize();
        service.replace(42, List.of(new TemporaryValue("simPin", "SIM-PIN", "HEMMELIG-1111")));
        clock.advance(Duration.ofHours(1));
        service.replace(42, List.of(new TemporaryValue("password", "Passord", "HEMMELIG-2222")));

        var raw = new String(Files.readAllBytes(paths.secretsDatabase()), StandardCharsets.ISO_8859_1);
        assertThat(raw).doesNotContain("HEMMELIG-1111", "HEMMELIG-2222");

        clock.advance(Duration.ofHours(23).plusMinutes(1));
        assertThat(service.list(42)).extracting(value -> value.key()).containsExactly("password");
        clock.advance(Duration.ofHours(1));
        assertThat(service.list(42)).isEmpty();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
