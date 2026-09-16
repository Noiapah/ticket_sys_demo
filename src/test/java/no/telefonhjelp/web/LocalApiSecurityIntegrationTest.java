package no.telefonhjelp.web;

import no.telefonhjelp.PhoneSupportApplication;
import no.telefonhjelp.config.DesktopSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = PhoneSupportApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LocalApiSecurityIntegrationTest {
    @TempDir static Path data;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("phone.support.data-dir", data::toString); }
    @Value("${local.server.port}") int port;
    @Autowired DesktopSession session;
    private String origin() { return "http://127.0.0.1:" + port; }

    private HttpResponse<String> request(String method, String path, String token, String origin, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(origin() + path)).timeout(java.time.Duration.ofSeconds(10));
        if (token != null) request.header("X-Desktop-Token", token);
        if (origin != null) request.header("Origin", origin);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try (var client = HttpClient.newHttpClient()) { return client.send(request.build(), HttpResponse.BodyHandlers.ofString()); }
    }

    @Test void protectsEveryDataEndpointAndDoesNotExposeTheCredential() throws Exception {
        for (var path : new String[]{"/api/bootstrap", "/api/employees", "/api/tickets", "/api/tickets/1/temporary-info", "/api/customers/match?phone=123", "/api/reports/summary"}) {
            assertThat(request("GET", path, null, null, null).statusCode()).as(path).isEqualTo(401);
        }
        assertThat(request("POST", "/api/maintenance/backup", null, origin(), "{}").statusCode()).isEqualTo(401);
        assertThat(request("GET", "/api/bootstrap", new DesktopSession().credential(), null, null).statusCode()).isEqualTo(401);
        var bootstrap = request("GET", "/api/bootstrap", session.credential(), null, null);
        assertThat(bootstrap.statusCode()).isEqualTo(200);
        assertThat(bootstrap.body()).doesNotContain(session.credential());
        var secret = request("GET", "/api/tickets/1/temporary-info", session.credential(), null, null);
        assertThat(secret.statusCode()).isEqualTo(200);
        assertThat(secret.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(secret.headers().firstValue("Content-Security-Policy").orElseThrow()).contains("frame-ancestors 'none'");
    }

    @Test void rejectsForeignOriginsAndRequiresOriginForWrites() throws Exception {
        for (var origin : new String[]{"https://attacker.invalid", "null", origin() + ".attacker.invalid"}) {
            assertThat(request("GET", "/api/bootstrap", session.credential(), origin, null).statusCode()).isEqualTo(403);
            assertThat(request("POST", "/api/maintenance/backup", session.credential(), origin, null).statusCode()).isEqualTo(403);
        }
        assertThat(request("POST", "/api/maintenance/backup", session.credential(), null, "{}").statusCode()).isEqualTo(403);
        // Raw paths never substitute for a single-use native authorization.
        assertThat(request("POST", "/api/maintenance/backup", session.credential(), origin(), "{\"path\":\"C:/victim.txt\"}").statusCode()).isEqualTo(400);
    }

    @Test void rejectsRebindingHostAndChunkedOversizedBodies() throws Exception {
        assertThat(raw("GET /api/bootstrap HTTP/1.1\r\nHost: attacker.invalid:" + port + "\r\nConnection: close\r\n\r\n")).startsWith("HTTP/1.1 403");
        var body = "x".repeat(LocalApiFilter.MAX_BODY_BYTES + 1);
        var response = raw("POST /api/employees HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\nOrigin: " + origin() + "\r\nX-Desktop-Token: " + session.credential() + "\r\nContent-Type: application/json\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n" + Integer.toHexString(body.length()) + "\r\n" + body + "\r\n0\r\n\r\n");
        assertThat(response).startsWith("HTTP/1.1 413");
    }

    @Test void staticPathNormalizationCannotBypassApiAuthentication() throws Exception {
        for (var path : new String[]{"/assets/../api/bootstrap", "/assets/%2e%2e/api/bootstrap", "/assets/..;/api/bootstrap", "/api;ignored/bootstrap", "/assets/%2e%2e/api/tickets/1/temporary-info"}) {
            var response = raw("GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\nConnection: close\r\n\r\n");
            assertThat(response).as(path).doesNotStartWith("HTTP/1.1 200").doesNotContain("currentEmployeeId", "expiresAt");
        }
    }

    @Test void boundsInputsAndReportsWithoutEchoingInvalidData() throws Exception {
        var malformed = request("POST", "/api/tickets", session.credential(), origin(), "{\"deviceType\":\"PRIVATE-INVALID-VALUE\"}");
        assertThat(malformed.statusCode()).isEqualTo(400);
        assertThat(malformed.body()).doesNotContain("PRIVATE-INVALID-VALUE", "Exception", "java.");
        assertThat(request("POST", "/api/employees", session.credential(), origin(), "{\"name\":\"" + "a".repeat(121) + "\"}").statusCode()).isEqualTo(400);
        for (var range : new String[]{"from=2000-01-01&to=2100-01-01", "from=2026-09-02&to=2026-09-01", "from=%2B999999999-12-31&to=%2B999999999-12-31"}) {
            assertThat(request("GET", "/api/reports/summary?" + range, session.credential(), null, null).statusCode()).isEqualTo(400);
        }
        assertThat(request("GET", "/api/tickets?size=101", session.credential(), null, null).statusCode()).isEqualTo(400);
    }

    @Test void desktopBridgeAllowsOnlyTheExactApplicationPage() {
        assertThat(DesktopSession.trustedPage(origin() + "/#/sak/1", origin())).isTrue();
        for (var location : new String[]{origin() + ".evil/", origin() + "/api/bootstrap", origin() + "/?token=x", "https://example.com", "file:///C:/test.html"}) assertThat(DesktopSession.trustedPage(location, origin())).isFalse();
    }

    private String raw(String request) throws Exception {
        try (var socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(10_000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
