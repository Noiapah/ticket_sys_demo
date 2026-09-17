package no.telefonhjelp.security;

import no.telefonhjelp.config.StoragePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class PinAccessTest {
    @TempDir Path directory;
    private final TestClock clock = new TestClock();
    private PinAccess access() throws Exception { return new PinAccess(new StoragePaths(directory.toString()), clock); }
    private char[] correct() { return "482916".toCharArray(); }
    private char[] wrong() { return "482917".toCharArray(); }
    private PinAccess setup() throws Exception {
        var access = access(); access.setup(correct(), correct()); return access;
    }

    @Test void allNineStagesPersistAcrossRestartsAndCannotBeSkippedWithCorrectPin() throws Exception {
        var access = setup();
        long[] delays = {60, 120, 300, 600, 1800, 3600, 18000, 86400};
        for (int stage = 0; stage < 9; stage++) {
            for (int attempt = 1; attempt <= 5; attempt++) {
                access = access(); // Restart after every attempt must not reset the counters.
                assertThat(access.unlock(wrong()).unlocked()).isFalse();
                if (attempt < 5) {
                    assertThat(access.status().mode()).isEqualTo(PinAccess.Mode.READY);
                    assertThat(access.status().attemptsRemaining()).isEqualTo(5 - attempt);
                }
            }
            access = access();
            assertThat(access.status().level()).isEqualTo(stage + 1);
            if (stage == 8) {
                assertThat(access.status().mode()).isEqualTo(PinAccess.Mode.PERMANENT);
                clock.advance(365L * 24 * 3600);
                assertThat(access().unlock(correct()).unlocked()).isFalse();
                assertThat(access().status().mode()).isEqualTo(PinAccess.Mode.PERMANENT);
            } else {
                var locked = access.status();
                assertThat(locked.mode()).isEqualTo(PinAccess.Mode.LOCKED);
                assertThat(locked.remainingSeconds()).isEqualTo(delays[stage]);
                assertThat(access.unlock(correct()).unlocked()).isFalse();
                assertThat(access.unlock(wrong()).status()).isEqualTo(locked);
                clock.advance(delays[stage] - 1);
                assertThat(access().unlock(correct()).status().remainingSeconds()).isEqualTo(1);
                clock.advance(1);
                assertThat(access().status().mode()).isEqualTo(PinAccess.Mode.READY);
            }
        }
    }

    @Test void successfulUnlockResetsPartialFailuresAndEscalation() throws Exception {
        var access = setup();
        for (int i = 0; i < 5; i++) access.unlock(wrong());
        clock.advance(60);
        access.unlock(wrong());
        assertThat(access.unlock(correct()).unlocked()).isTrue();
        access = access();
        assertThat(access.status()).isEqualTo(new PinAccess.Status(PinAccess.Mode.READY, 5, 0, 0));
        for (int i = 0; i < 5; i++) access.unlock(wrong());
        assertThat(access.status().remainingSeconds()).isEqualTo(60);
    }

    @Test void rejectsInvalidSetupAndDoesNotStorePlainPinOrAllowSetupAgain() throws Exception {
        var access = access();
        assertThat(access.status().mode()).isEqualTo(PinAccess.Mode.SETUP);
        assertThatThrownBy(() -> access.setup("12345".toCharArray(), "12345".toCharArray())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> access.setup("12345x".toCharArray(), "12345x".toCharArray())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> access.setup(correct(), wrong())).isInstanceOf(IllegalArgumentException.class);
        access.setup(correct(), correct());
        assertThat(new String(Files.readAllBytes(directory.resolve("pin-access.dat")), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("482916");
        assertThatThrownBy(() -> access.setup(wrong(), wrong())).isInstanceOf(IllegalStateException.class);
        assertThat(access().unlock(correct()).unlocked()).isTrue();
    }

    @Test void missingOrTamperedStateFailsClosed() throws Exception {
        setup();
        var state = directory.resolve("pin-access.dat");
        byte[] stored = Files.readAllBytes(state);
        Files.delete(state);
        assertThatThrownBy(this::access).isInstanceOf(java.io.IOException.class);
        stored[stored.length - 1] ^= 1;
        Files.write(state, stored);
        assertThatThrownBy(this::access).isInstanceOf(java.io.IOException.class);
    }

    @Test void failedPersistenceCannotGrantAccess() throws Exception {
        var access = setup();
        access.unlock(wrong());
        // The state remains readable, but Windows refuses replacement of a read-only file.
        var state = directory.resolve("pin-access.dat");
        Files.setAttribute(state, "dos:readonly", true);
        try {
            assertThatThrownBy(() -> access.unlock(correct())).isInstanceOf(java.io.IOException.class);
            assertThat(access().status().attemptsRemaining()).isEqualTo(4);
        } finally { Files.setAttribute(state, "dos:readonly", false); }
    }

    @Test void malformedAttemptsCountAsFailures() throws Exception {
        var access = setup();
        assertThat(access.unlock("1".toCharArray()).unlocked()).isFalse();
        assertThat(access.unlock(null).status().attemptsRemaining()).isEqualTo(3);
    }

    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-09-17T12:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
