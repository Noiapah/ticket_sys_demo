package no.telefonhjelp;

import javafx.application.Platform;
import javafx.scene.web.WebView;
import no.telefonhjelp.config.DesktopSession;
import no.telefonhjelp.config.StoragePaths;
import no.telefonhjelp.security.PinAccess;
import no.telefonhjelp.security.PinGate;
import no.telefonhjelp.service.FileAuthorizations;
import no.telefonhjelp.service.SecretService;
import no.telefonhjelp.domain.ApiModels.TemporaryValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;

/** Exercises the production bundle and native bootstrap in WebKit without opening a window. */
@SpringBootTest(classes = PhoneSupportApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DesktopPageIntegrationTest {
    @TempDir static Path data;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("phone.support.data-dir", data::toString); }
    @Value("${local.server.port}") int port;
    @Autowired DesktopSession session;
    @Autowired FileAuthorizations grants;
    @Autowired SecretService secrets;
    private WebView view;
    private javafx.stage.Stage window;
    private PhoneSupportApplication.DesktopBridge bridge;

    @Test void desktopBootstrapsPrivatelyAndSecretsRequireReveal() throws Exception {
        secrets.replace(1, List.of(new TemporaryValue("code", "Code", "synthetic-native-secret")));
        var started = new CompletableFuture<Void>();
        Platform.startup(() -> { Platform.setImplicitExit(false); started.complete(null); });
        started.get(10, TimeUnit.SECONDS);
        var origin = "http://127.0.0.1:" + port;
        try {
            nativePinGateRequiresUnlock();
            fx(() -> {
                view = new WebView();
                window = new javafx.stage.Stage(javafx.stage.StageStyle.UTILITY);
                window.setOpacity(0);
                window.setScene(new javafx.scene.Scene(view, 1280, 800));
                window.show(); // Invisible window supplies the WebKit rendering pulses.
                bridge = PhoneSupportApplication.connectDesktop(view, null, origin, session, grants);
                view.getEngine().load(origin + "/"); return null;
            });
            until("document.querySelector('.ticket-table') !== null");
            assertThat(fx(() -> view.getEngine().executeScript("typeof window.acceptDesktopSession"))).isEqualTo("undefined");
            assertThat(fx(() -> view.getEngine().executeScript("document.documentElement.outerHTML"))).asString().doesNotContain(session.credential());
            fx(() -> view.getEngine().executeScript("location.hash = '#/sak/1'"));
            until("document.querySelector('.sensitive') !== null");
            assertThat(fx(() -> view.getEngine().executeScript("document.querySelectorAll('.sensitive input').length"))).isEqualTo(0);
            fx(() -> view.getEngine().executeScript("document.querySelector('.sensitive > button').click()"));
            until("document.querySelectorAll('.sensitive input[type=password]').length === 4");
            assertThat(fx(() -> view.getEngine().executeScript("document.querySelectorAll('.sensitive input')[1].value"))).isEqualTo("synthetic-native-secret");
            fx(() -> view.getEngine().executeScript("window.dispatchEvent(new Event('session-ended'))"));
            until("document.querySelectorAll('.sensitive input').length === 0");
            fx(() -> view.getEngine().executeScript("var injected=document.createElement('script'); injected.textContent='window.inlineExecuted=true'; document.head.appendChild(injected)"));
            assertThat(fx(() -> view.getEngine().executeScript("window.inlineExecuted === true"))).isEqualTo(false);
            // Navigation to another path is cancelled and returns to the app page.
            fx(() -> { view.getEngine().load(origin + "/api/bootstrap"); return null; });
            until("document.querySelector('.ticket-table') !== null && location.pathname === '/' && location.hash === '#/'");
        } finally {
            fx(() -> { if (view != null) view.getEngine().getLoadWorker().cancel(); if (window != null) window.close(); return null; });
            Platform.exit();
        }
    }

    private void nativePinGateRequiresUnlock() throws Exception {
        var clock = new GateClock();
        var paths = new StoragePaths(data.resolve("pin-gate-test").toString());
        var access = new PinAccess(paths, clock);
        var unlocked = new java.util.concurrent.atomic.AtomicInteger();
        var gate = new java.util.concurrent.atomic.AtomicReference<PinGate>();
        fx(() -> {
            window = new javafx.stage.Stage(javafx.stage.StageStyle.UTILITY); window.setOpacity(0);
            gate.set(new PinGate(window, access, unlocked::incrementAndGet));
            pin("pin-input").setText("048291"); pin("pin-confirm").setText("048292");
            assertThat(window.getScene().lookup("#pin-submit").isDisabled()).isTrue();
            assertThat(unlocked.get()).isZero();
            pin("pin-confirm").setText("048291");
            ((javafx.scene.control.Button) window.getScene().lookup("#pin-submit")).fire(); return null;
        });
        awaitFx(() -> unlocked.get() == 1);
        fx(() -> { gate.get().close(); window.close(); return null; });
        unlocked.set(0);
        var reopened = new PinAccess(paths, clock);
        fx(() -> {
            window = new javafx.stage.Stage(javafx.stage.StageStyle.UTILITY); window.setOpacity(0);
            gate.set(new PinGate(window, reopened, unlocked::incrementAndGet)); return null;
        });
        try {
            for (int i = 0; i < 5; i++) {
                fx(() -> {
                    pin("pin-input").setText("048292");
                    ((javafx.scene.control.Button) window.getScene().lookup("#pin-submit")).fire(); return null;
                });
                awaitFx(() -> !((javafx.scene.control.Button) window.getScene().lookup("#pin-submit")).getText().equals("Kontrollerer …"));
                assertThat(unlocked.get()).isZero();
            }
            assertThat(fx(() -> pin("pin-input").isDisabled())).isTrue();
            assertThat(fx(() -> ((javafx.scene.control.Label) window.getScene().lookup("#pin-status")).getText())).contains("midlertidig låst");
            fx(() -> {
                pin("pin-input").setText("048291");
                ((javafx.scene.control.Button) window.getScene().lookup("#pin-submit")).fire(); return null;
            });
            assertThat(unlocked.get()).isZero();
            clock.now = clock.now.plusSeconds(60);
            awaitFx(() -> !pin("pin-input").isDisabled());
            fx(() -> { ((javafx.scene.control.Button) window.getScene().lookup("#pin-submit")).fire(); return null; });
            awaitFx(() -> unlocked.get() == 1);
        } finally { fx(() -> { gate.get().close(); window.close(); return null; }); }
    }

    private javafx.scene.control.PasswordField pin(String id) { return (javafx.scene.control.PasswordField) window.getScene().lookup("#" + id); }

    private void awaitFx(Supplier<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            if (fx(condition)) return;
            Thread.sleep(50);
        }
        fail("Native PIN gate did not reach its expected state");
    }

    private static final class GateClock extends java.time.Clock {
        volatile java.time.Instant now = java.time.Instant.parse("2026-09-17T12:00:00Z");
        @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public java.time.Instant instant() { return now; }
    }

    private <T> T fx(Supplier<T> operation) throws Exception {
        var future = new CompletableFuture<T>();
        Platform.runLater(() -> { try { future.complete(operation.get()); } catch (Throwable error) { future.completeExceptionally(error); } });
        return future.get(10, TimeUnit.SECONDS);
    }
    private void until(String expression) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(fx(() -> view.getEngine().executeScript(expression)))) return;
            Thread.sleep(50);
        }
        fail("Desktop page did not reach expected state: " + expression + "; state=" + fx(() -> view.getEngine().getLoadWorker().getState())
                + "; location=" + fx(() -> view.getEngine().getLocation())
                + "; bootstrap=" + fx(() -> view.getEngine().executeScript("typeof window.acceptDesktopSession"))
                + "; body=" + fx(() -> view.getEngine().executeScript("document.body ? document.body.innerText.slice(0, 500) : 'missing'")));
    }
}
