package no.telefonhjelp.config;

import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Process-local credential. Never persisted, logged or returned by an HTTP endpoint. */
@Component
public final class DesktopSession {
    private final String token = newToken();

    public static String newToken() {
        var bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String credential() { return token; }

    public boolean accepts(String candidate) {
        return candidate != null && MessageDigest.isEqual(token.getBytes(StandardCharsets.US_ASCII), candidate.getBytes(StandardCharsets.US_ASCII));
    }

    public static boolean trustedPage(String location, String origin) {
        return location != null && (location.equals(origin + "/") || location.startsWith(origin + "/#"));
    }
}
