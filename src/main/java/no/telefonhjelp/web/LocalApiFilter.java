package no.telefonhjelp.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import no.telefonhjelp.config.DesktopSession;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.*;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Semaphore;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalApiFilter extends OncePerRequestFilter {
    public static final int MAX_BODY_BYTES = 65_536;
    private final DesktopSession session;
    private final Semaphore requests = new Semaphore(16);
    private final Semaphore reports = new Semaphore(1);

    public LocalApiFilter(DesktopSession session) { this.session = session; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store");
        var host = "127.0.0.1:" + request.getLocalPort();
        var origin = "http://" + host;
        var origins = Collections.list(request.getHeaders("Origin"));
        if (!Collections.list(request.getHeaders("Host")).equals(java.util.List.of(host))
                || origins.size() > 1 || (!origins.isEmpty() && !origins.getFirst().equals(origin))
                || "cross-site".equals(request.getHeader("Sec-Fetch-Site"))) {
            reject(response, 403, "Forespørselen er ikke tillatt."); return;
        }
        // Authenticate all non-static routes as well, including error and unknown API paths.
        var path = request.getServletPath();
        // Tomcat normalizes dot segments/path parameters before routing. Never let an
        // unnormalized /assets/ prefix turn into an authenticated controller route.
        if (!request.getRequestURI().equals(path)) { reject(response, 400, "Ugyldig adresse."); return; }
        var staticPage = (path.equals("/") || path.equals("/index.html") || path.startsWith("/assets/"))
                && Set.of("GET", "HEAD").contains(request.getMethod());
        if (staticPage) { chain.doFilter(request, response); return; }
        var tokens = Collections.list(request.getHeaders("X-Desktop-Token"));
        if (tokens.size() != 1 || !session.accepts(tokens.getFirst())) {
            reject(response, 401, "Åpne Telefonhjelp fra skrivebordsprogrammet."); return;
        }
        var mutation = !Set.of("GET", "HEAD").contains(request.getMethod());
        if (mutation && !origins.equals(java.util.List.of(origin))) {
            reject(response, 403, "Forespørselen er ikke tillatt."); return;
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES) { reject(response, 413, "Forespørselen er for stor."); return; }
        if (request.getHeader("Content-Encoding") != null) { reject(response, 415, "Ugyldig innholdsformat."); return; }
        if (!requests.tryAcquire()) { reject(response, 429, "Prøv igjen om litt."); return; }
        var report = path.startsWith("/api/reports/");
        var reportAcquired = !report || reports.tryAcquire();
        try {
            if (!reportAcquired) { reject(response, 429, "En rapport lages allerede. Prøv igjen om litt."); return; }
            // Read a bounded body before JSON parsing; this also covers chunked requests.
            var body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) { reject(response, 413, "Forespørselen er for stor."); return; }
            if (body.length > 0 && (request.getContentType() == null || !request.getContentType().split(";", 2)[0].trim().equalsIgnoreCase("application/json"))) {
                reject(response, 415, "Bruk JSON-format."); return;
            }
            chain.doFilter(new BufferedRequest(request, body), response);
        } finally {
            if (report && reportAcquired) reports.release();
            requests.release();
        }
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }

    private static final class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        BufferedRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public ServletInputStream getInputStream() {
            var stream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return stream.read(); }
                @Override public boolean isFinished() { return stream.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8)); }
    }
}
