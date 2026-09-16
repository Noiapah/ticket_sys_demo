package no.telefonhjelp.service;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Arrays;

public final class BackupEncryption {
    private static final byte[] MAGIC = "THBACKUP1".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private BackupEncryption() {}

    public static void encrypt(Path source, Path target, char[] password) throws Exception {
        byte[] salt = new byte[16], nonce = new byte[12];
        var random = new SecureRandom(); random.nextBytes(salt); random.nextBytes(nonce);
        var cipher = cipher(Cipher.ENCRYPT_MODE, password, salt, nonce);
        try (var output = Files.newOutputStream(target); var input = Files.newInputStream(source)) {
            output.write(MAGIC); output.write(salt); output.write(nonce);
            try (var encrypted = new CipherOutputStream(output, cipher)) { input.transferTo(encrypted); }
        }
    }

    public static void decrypt(Path source, Path target, char[] password) throws Exception {
        try (var input = Files.newInputStream(source); var output = Files.newOutputStream(target)) {
            if (!Arrays.equals(input.readNBytes(MAGIC.length), MAGIC)) throw AppException.badRequest("Ugyldig sikkerhetskopi.");
            var salt = input.readNBytes(16); var nonce = input.readNBytes(12);
            if (salt.length != 16 || nonce.length != 12) throw AppException.badRequest("Ugyldig sikkerhetskopi.");
            var cipher = cipher(Cipher.DECRYPT_MODE, password, salt, nonce);
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) { var bytes = cipher.update(buffer, 0, count); if (bytes != null) output.write(bytes); }
            output.write(cipher.doFinal()); // Authenticate before the caller opens any SQLite connection.
        } catch (javax.crypto.AEADBadTagException exception) { throw AppException.badRequest("Feil passord eller skadet sikkerhetskopi."); }
    }

    private static Cipher cipher(int mode, char[] password, byte[] salt, byte[] nonce) throws Exception {
        var spec = new PBEKeySpec(password, salt, 600_000, 256);
        byte[] key = null;
        try {
            key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(MAGIC);
            return cipher;
        } finally { spec.clearPassword(); if (key != null) Arrays.fill(key, (byte) 0); }
    }
}
