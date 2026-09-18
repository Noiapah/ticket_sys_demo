package no.telefonhjelp.security;

import com.sun.jna.platform.win32.Crypt32Util;
import no.telefonhjelp.config.PrivateFiles;
import no.telefonhjelp.config.StoragePaths;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;

/** Native startup gate. Its state is deliberately separate from restorable ticket data. */
public final class PinAccess {
    private static final long[] LOCK_SECONDS = {60, 120, 300, 600, 1800, 3600, 18000, 86400};
    private static final int ITERATIONS = 600_000;
    private static final int MAGIC = 0x50494E31;
    private final Path file;
    private final Path marker;
    private final Clock clock;
    private State state;

    public enum Mode { SETUP, READY, LOCKED, PERMANENT }
    public record Status(Mode mode, int attemptsRemaining, int level, long remainingSeconds) {}
    public record Attempt(boolean unlocked, Status status) {}

    public PinAccess(StoragePaths paths, Clock clock) throws IOException {
        this.file = paths.root().resolve("pin-access.dat");
        this.marker = paths.root().resolve("pin-access.initialized");
        this.clock = clock;
        this.state = read();
    }

    public synchronized Status status() {
        if (state == null) return new Status(Mode.SETUP, 5, 0, 0);
        if (state.level > LOCK_SECONDS.length) return new Status(Mode.PERMANENT, 0, state.level, 0);
        long remaining = Math.max(0, state.blockedUntil - clock.millis());
        return new Status(remaining > 0 ? Mode.LOCKED : Mode.READY, 5 - state.failures,
                state.level, (remaining + 999) / 1000);
    }

    public synchronized void setup(char[] pin, char[] repeated) throws Exception {
        state = read();
        if (state != null) throw new IllegalStateException("PIN-koden er allerede opprettet.");
        if (!validPin(pin)) throw new IllegalArgumentException("PIN-koden må bestå av 4–12 sifre.");
        if (!Arrays.equals(pin, repeated)) throw new IllegalArgumentException("PIN-kodene er ikke like.");
        byte[] salt = new byte[32];
        new SecureRandom().nextBytes(salt);
        var initial = new State(salt, hash(pin, salt), 0, 0, 0);
        // A missing/corrupt state after setup must never silently enable new PIN setup.
        Files.createFile(marker);
        PrivateFiles.restrict(marker);
        write(initial);
        state = initial;
    }

    public synchronized Attempt unlock(char[] pin) throws Exception {
        state = read();
        var current = status();
        if (current.mode != Mode.READY) return new Attempt(false, current);
        byte[] candidate = validPin(pin) ? hash(pin, state.salt) : new byte[32];
        boolean correct;
        try { correct = MessageDigest.isEqual(candidate, state.hash); }
        finally { Arrays.fill(candidate, (byte) 0); }
        State next;
        if (correct) {
            next = new State(state.salt, state.hash, 0, 0, 0);
        } else {
            int failures = state.failures + 1;
            int level = state.level;
            long blockedUntil = 0;
            if (failures == 5) {
                failures = 0;
                level++;
                if (level <= LOCK_SECONDS.length) blockedUntil = Math.addExact(clock.millis(), LOCK_SECONDS[level - 1] * 1000);
            }
            next = new State(state.salt, state.hash, failures, level, blockedUntil);
        }
        // Never report successful access or a completed attempt before persistence succeeds.
        write(next);
        state = next;
        return new Attempt(correct, status());
    }

    private static boolean validPin(char[] pin) {
        if (pin == null || pin.length < 4 || pin.length > 12) return false;
        for (char digit : pin) if (digit < '0' || digit > '9') return false;
        return true;
    }

    private static byte[] hash(char[] pin, byte[] salt) throws Exception {
        var spec = new PBEKeySpec(pin, salt, ITERATIONS, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }

    private State read() throws IOException {
        PrivateFiles.localPath(file);
        PrivateFiles.localPath(marker);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) throw unavailable();
            return null;
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 16_384) throw unavailable();
        byte[] plain = null;
        try {
            plain = Crypt32Util.cryptUnprotectData(Files.readAllBytes(file));
            try (var input = new DataInputStream(new ByteArrayInputStream(plain))) {
                if (input.readInt() != MAGIC) throw unavailable();
                byte[] salt = input.readNBytes(32), hash = input.readNBytes(32);
                int failures = input.readInt(), level = input.readInt();
                long blockedUntil = input.readLong();
                if (salt.length != 32 || hash.length != 32 || failures < 0 || failures > 4
                        || level < 0 || level > 9 || blockedUntil < 0 || input.read() != -1
                        || (level == 0 && blockedUntil != 0) || (level == 9 && (failures != 0 || blockedUntil != 0))) throw unavailable();
                return new State(salt, hash, failures, level, blockedUntil);
            }
        } catch (Exception exception) { throw unavailable(); }
        finally { if (plain != null) Arrays.fill(plain, (byte) 0); }
    }

    private void write(State next) throws IOException {
        PrivateFiles.localPath(file);
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.write(next.salt); output.write(next.hash);
            output.writeInt(next.failures); output.writeInt(next.level); output.writeLong(next.blockedUntil);
        }
        byte[] plain = bytes.toByteArray();
        byte[] protectedState;
        try { protectedState = Crypt32Util.cryptProtectData(plain); }
        catch (RuntimeException exception) { throw unavailable(); }
        finally { Arrays.fill(plain, (byte) 0); }
        var temporary = Files.createTempFile(file.getParent(), "pin-access-", ".tmp");
        try {
            PrivateFiles.restrict(temporary);
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var buffer = java.nio.ByteBuffer.wrap(protectedState);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static IOException unavailable() { return new IOException("PIN-beskyttelsen kan ikke leses eller lagres. Programmet forblir låst."); }
    private static final class State {
        final byte[] salt, hash;
        final int failures, level;
        final long blockedUntil;
        State(byte[] salt, byte[] hash, int failures, int level, long blockedUntil) {
            this.salt = salt; this.hash = hash; this.failures = failures; this.level = level; this.blockedUntil = blockedUntil;
        }
    }
}
