package no.telefonhjelp;

import javafx.application.Platform;
import javafx.scene.web.WebView;
import no.telefonhjelp.config.DesktopSession;
import no.telefonhjelp.config.StoragePaths;
import no.telefonhjelp.security.PinAccess;
import no.telefonhjelp.security.PinGate;
import no.telefonhjelp.service.FileAuthorizations;
import no.telefonhjelp.service.SecretService;
import no.telefonhjelp.service.TicketService;
import no.telefonhjelp.service.EmployeeService;
import no.telefonhjelp.domain.ApiModels.*;
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
    @Autowired TicketService tickets;
    @Autowired EmployeeService employees;
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
            customerHistoryAndResolutionNotes();
            // Navigation to another path is cancelled and returns to the app page.
            fx(() -> { view.getEngine().load(origin + "/api/bootstrap"); return null; });
            until("document.querySelector('.ticket-table') !== null && location.pathname === '/' && location.hash === '#/'");
        } finally {
            fx(() -> { if (view != null) view.getEngine().getLoadWorker().cancel(); if (window != null) window.close(); return null; });
            Platform.exit();
        }
    }

    private void customerHistoryAndResolutionNotes() throws Exception {
        var actor = employees.list().stream().filter(Employee::active).findFirst().orElseThrow();
        var previous = tickets.create(new TicketDraft("History UI customer", "98044339", DeviceType.PHONE, "Apple", "iPhone", "", OperatingSystem.IOS, "E-post", "Previous issue", actor.id()));
        tickets.status(previous.id(), TicketStatus.CLOSED, actor.id(), previous.version(), "Previous resolution");
        var current = tickets.create(new TicketDraft("History UI customer", "98044339", DeviceType.TABLET, "Apple", "iPad", "", OperatingSystem.IOS, "E-post", "Current issue", actor.id()));
        fx(() -> view.getEngine().executeScript("location.hash = '#/ny'"));
        until("document.querySelector('input[inputmode=tel]') !== null");
        fx(() -> view.getEngine().executeScript("var phone=document.querySelector('input[inputmode=tel]'); phone.value='98044339'; phone.dispatchEvent(new Event('input', {bubbles:true})); var draft=document.querySelector('textarea'); draft.value='Unsaved draft'; draft.dispatchEvent(new Event('input', {bubbles:true}))"));
        until("document.querySelector('.customer-history > button') !== null");
        fx(() -> view.getEngine().executeScript("document.querySelector('.customer-history > button').click()"));
        until("document.querySelectorAll('.customer-history__ticket').length === 2");
        assertThat(fx(() -> view.getEngine().executeScript("document.querySelector('.customer-history').textContent"))).asString().contains("Previous resolution", "Previous issue", "Current issue");
        assertThat(fx(() -> view.getEngine().executeScript("document.querySelector('textarea').value"))).isEqualTo("Unsaved draft");

        fx(() -> view.getEngine().executeScript("location.hash = '#/sak/" + current.id() + "'"));
        until("document.querySelector('.detail-side .button--danger') !== null");
        fx(() -> view.getEngine().executeScript("document.querySelector('.customer-history > button').click()"));
        until("document.querySelectorAll('.customer-history__ticket').length === 1");
        assertThat(fx(() -> view.getEngine().executeScript("document.querySelector('.customer-history').textContent"))).asString().contains("Previous resolution").doesNotContain("Current issue");
        fx(() -> view.getEngine().executeScript("document.querySelector('.detail-side .button--danger').click()"));
        until("document.querySelector('.resolution-form textarea') !== null");
        assertThat(fx(() -> view.getEngine().executeScript("document.querySelector('.resolution-form .button--danger').disabled"))).isEqualTo(true);
        fx(() -> view.getEngine().executeScript("var note=document.querySelector('.resolution-form textarea'); note.value='Resolved in desktop UI'; note.dispatchEvent(new Event('input', {bubbles:true}))"));
        until("document.querySelector('.resolution-form .button--danger').disabled === false");
        fx(() -> view.getEngine().executeScript("document.querySelector('.resolution-form .button--danger').click()"));
        until("document.querySelector('.resolution-form') === null && document.querySelector('.detail-side select').disabled");
        assertThat(tickets.get(current.id()).resolutionNote()).isEqualTo("Resolved in desktop UI");
        fx(() -> view.getEngine().executeScript("document.querySelector('.detail-side .button--primary').click()"));
        until("document.querySelector('.detail-side .button--danger') !== null");
        assertThat(fx(() -> view.getEngine().executeScript("document.querySelector('.detail-main').textContent"))).asString().contains("Resolved in desktop UI");

        // A stale write must keep both the case open and the typed note available for retry.
        var fresh = tickets.get(current.id());
        tickets.comment(current.id(), "Concurrent change", actor.id(), fresh.version());
        fx(() -> view.getEngine().executeScript("document.querySelector('.detail-side .button--danger').click()"));
        until("document.querySelector('.resolution-form textarea') !== null");
        fx(() -> view.getEngine().executeScript("var retry=document.querySelector('.resolution-form textarea'); retry.value='Keep this note'; retry.dispatchEvent(new Event('input', {bubbles:true}))"));
        until("document.querySelector('.resolution-form .button--danger').disabled === false");
        fx(() -> view.getEngine().executeScript("document.querySelector('.resolution-form .button--danger').click()"));
        until("document.querySelector('.alert--error') !== null");
        assertThat(fx(() -> view.getEngine().executeScript("document.querySelector('.resolution-form textarea').value"))).isEqualTo("Keep this note");
        assertThat(tickets.get(current.id()).status()).isEqualTo(TicketStatus.IN_PROGRESS);
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
            var submit = (javafx.scene.control.Button) window.getScene().lookup("#pin-submit");
            assertThat(submit.isDisabled()).isFalse();
            submit.fire();
            assertThat(((javafx.scene.control.Label) window.getScene().lookup("#pin-error")).getText()).contains("4–12");
            pin("pin-input").setText("048"); pin("pin-confirm").setText("048");
            submit.fire();
            assertThat(((javafx.scene.control.Label) window.getScene().lookup("#pin-error")).getText()).contains("4–12");
            pin("pin-input").setText("0482"); pin("pin-confirm").setText("0483");
            assertThat(submit.isDisabled()).isFalse();
            submit.fire();
            assertThat(((javafx.scene.control.Label) window.getScene().lookup("#pin-error")).getText()).contains("ikke like");
            assertThat(unlocked.get()).isZero();
            assertThat(access.status().mode()).isEqualTo(PinAccess.Mode.SETUP);
            pin("pin-confirm").clear();
            submit.fire();
            assertThat(((javafx.scene.control.Label) window.getScene().lookup("#pin-error")).getText()).contains("andre feltet");
            pin("pin-confirm").setText("0482");
            assertThat(((javafx.scene.control.Label) window.getScene().lookup("#pin-error")).getText()).isEmpty();
            pin("pin-confirm").fireEvent(new javafx.event.ActionEvent()); return null;
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
            fx(() -> {
                ((javafx.scene.control.Button) window.getScene().lookup("#pin-submit")).fire();
                assertThat(((javafx.scene.control.Label) window.getScene().lookup("#pin-error")).getText()).contains("Oppgi PIN-koden");
                assertThat(reopened.status().attemptsRemaining()).isEqualTo(5);
                return null;
            });
            for (int i = 0; i < 5; i++) {
                fx(() -> {
                    pin("pin-input").setText("0483");
                    ((javafx.scene.control.Button) window.getScene().lookup("#pin-submit")).fire(); return null;
                });
                awaitFx(() -> !((javafx.scene.control.Button) window.getScene().lookup("#pin-submit")).getText().equals("Kontrollerer …"));
                assertThat(unlocked.get()).isZero();
            }
            assertThat(fx(() -> pin("pin-input").isDisabled())).isTrue();
            assertThat(fx(() -> ((javafx.scene.control.Label) window.getScene().lookup("#pin-status")).getText())).contains("midlertidig låst");
            fx(() -> {
                pin("pin-input").setText("0482");
                ((javafx.scene.control.Button) window.getScene().lookup("#pin-submit")).fire(); return null;
            });
            assertThat(unlocked.get()).isZero();
            clock.now = clock.now.plusSeconds(60);
            awaitFx(() -> !pin("pin-input").isDisabled());
            fx(() -> { pin("pin-input").fireEvent(new javafx.event.ActionEvent()); return null; });
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
