package no.telefonhjelp.security;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Arrays;
import java.util.concurrent.Executors;

/** Native UI: no web page, HTTP listener, or desktop token exists before unlocking. */
public final class PinGate implements AutoCloseable {
    private final PinAccess access;
    private final Runnable onUnlocked;
    private final PasswordField pin = field("pin-input", "PIN-kode");
    private final PasswordField repeated = field("pin-confirm", "Gjenta PIN-koden");
    private final Label title = new Label();
    private final Label description = new Label();
    private final Label status = new Label();
    private final Label error = new Label();
    private final Button submit = new Button();
    private final Timeline countdown;
    private final java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        var thread = new Thread(runnable, "pin-verification"); thread.setDaemon(true); return thread;
    });
    private boolean busy;
    private boolean closed;
    private boolean failed;

    public PinGate(Stage stage, PinAccess access, Runnable onUnlocked) {
        this.access = access;
        this.onUnlocked = onUnlocked;
        title.getStyleClass().add("title");
        description.setWrapText(true);
        description.getStyleClass().add("description");
        status.setId("pin-status"); status.setWrapText(true);
        error.setId("pin-error"); error.setWrapText(true); error.getStyleClass().add("error");
        submit.setId("pin-submit"); submit.getStyleClass().add("primary"); submit.setMaxWidth(Double.MAX_VALUE);
        submit.setDefaultButton(true); submit.setOnAction(event -> submit());
        pin.textProperty().addListener((observable, oldValue, value) -> refresh());
        repeated.textProperty().addListener((observable, oldValue, value) -> refresh());
        var brand = new Label("TELEFONHJELP"); brand.getStyleClass().add("brand");
        var warning = new Label("Fem feil gir en ventetid som øker ved nye feil. Etter 45 feil uten vellykket opplåsing blir tilgangen permanent sperret.");
        warning.setWrapText(true); warning.getStyleClass().add("hint");
        var exit = new Button("Avslutt"); exit.getStyleClass().add("secondary"); exit.setOnAction(event -> stage.close());
        var card = new VBox(14, brand, title, description, pin, repeated, submit, error, status, warning, exit);
        card.getStyleClass().add("card"); card.setMaxWidth(430); card.setPadding(new Insets(32));
        var root = new StackPane(card); root.setPadding(new Insets(24)); root.setAlignment(Pos.CENTER);
        var scene = new Scene(root, 580, 660);
        scene.getStylesheets().add(PinGate.class.getResource("/pin-gate.css").toExternalForm());
        stage.setTitle("Telefonhjelp – Lås opp"); stage.setMinWidth(480); stage.setMinHeight(640);
        stage.setScene(scene);
        countdown = new Timeline(new KeyFrame(Duration.seconds(1), event -> refresh()));
        countdown.setCycleCount(Timeline.INDEFINITE);
        countdown.play();
        refresh();
        stage.show();
        Platform.runLater(pin::requestFocus);
    }

    private static PasswordField field(String id, String label) {
        var field = new PasswordField(); field.setId(id); field.setPromptText(label); field.setAccessibleText(label);
        field.setTextFormatter(new TextFormatter<String>(change -> change.getControlNewText().matches("[0-9]{0,12}") ? change : null));
        return field;
    }

    private void refresh() {
        if (closed || busy || failed) return;
        var current = access.status();
        boolean setup = current.mode() == PinAccess.Mode.SETUP;
        boolean ready = setup || current.mode() == PinAccess.Mode.READY;
        title.setText(setup ? "Opprett PIN-kode" : "Lås opp programmet");
        description.setText(setup ? "Velg en PIN-kode med 6–12 sifre. Den må oppgis hver gang programmet starter."
                : "Oppgi PIN-koden for å åpne Telefonhjelp.");
        repeated.setVisible(setup); repeated.setManaged(setup);
        pin.setDisable(!ready); repeated.setDisable(!ready);
        submit.setText(setup ? "Opprett PIN og åpne" : "Lås opp");
        submit.setDisable(!ready || pin.getLength() == 0 || (setup && (pin.getLength() < 6 || !pin.getText().equals(repeated.getText()))));
        status.setText(switch (current.mode()) {
            case SETUP -> "Oppbevar PIN-koden trygt. Den kan ikke gjenopprettes.";
            case READY -> current.attemptsRemaining() + " forsøk igjen før neste sperre.";
            case LOCKED -> "Programmet er midlertidig låst. Prøv igjen om " + duration(current.remainingSeconds()) + ".";
            case PERMANENT -> "Programmet er permanent låst. PIN-koden kan ikke lenger brukes.";
        });
    }

    private void submit() {
        if (busy || closed || failed || submit.isDisabled()) return;
        boolean setup = access.status().mode() == PinAccess.Mode.SETUP;
        char[] candidate = pin.getText().toCharArray(), confirmation = repeated.getText().toCharArray();
        busy = true; pin.clear(); repeated.clear(); pin.setDisable(true); repeated.setDisable(true); submit.setDisable(true);
        error.setText(""); submit.setText("Kontrollerer …");
        var task = new Task<Boolean>() {
            @Override protected Boolean call() throws Exception {
                try {
                    if (setup) { access.setup(candidate, confirmation); return true; }
                    return access.unlock(candidate).unlocked();
                } finally { Arrays.fill(candidate, '\0'); Arrays.fill(confirmation, '\0'); }
            }
        };
        task.setOnSucceeded(event -> {
            if (closed) return;
            busy = false;
            if (task.getValue()) { close(); onUnlocked.run(); }
            else { error.setText("Feil PIN-kode."); refresh(); pin.requestFocus(); }
        });
        task.setOnFailed(event -> {
            if (closed) return;
            busy = false;
            if (task.getException() instanceof IllegalArgumentException) {
                error.setText(task.getException().getMessage()); refresh();
            } else {
                failed = true; countdown.stop(); submit.setText("Låst");
                status.setText("PIN-beskyttelsen kan ikke leses eller lagres. Programmet forblir låst. Start programmet på nytt for å prøve igjen.");
            }
        });
        worker.execute(task);
    }

    static String duration(long seconds) {
        long hours = seconds / 3600, minutes = (seconds % 3600) / 60;
        return hours > 0 ? "%d t %02d min %02d sek".formatted(hours, minutes, seconds % 60)
                : "%d min %02d sek".formatted(minutes, seconds % 60);
    }

    @Override public void close() {
        closed = true; countdown.stop(); worker.shutdown(); pin.clear(); repeated.clear();
    }
}
