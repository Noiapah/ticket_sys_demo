package no.telefonhjelp;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.web.WebView;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.concurrent.Worker;
import com.sun.jna.platform.win32.KnownFolders;
import com.sun.jna.platform.win32.Shell32Util;
import no.telefonhjelp.config.StoragePaths;
import no.telefonhjelp.config.DesktopSession;
import no.telefonhjelp.service.FileAuthorizations;
import netscape.javascript.JSObject;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.io.File;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Locale;

@SpringBootApplication
@EnableScheduling
public class PhoneSupportApplication extends Application {
    private static FileChannel lockChannel;
    private static FileLock appLock;
    private static volatile String restartCommand;
    private ConfigurableApplicationContext context;
    private DesktopBridge desktopBridge;

    public static void main(String[] args) throws Exception {
        launchDesktop(args);
    }

    public static void launchDesktop(String[] args) throws Exception {
        for (var argument : args) {
            if (argument.startsWith("--phone.support.data-dir=")) System.setProperty("phone.support.data-dir", argument.substring(argument.indexOf('=') + 1));
        }
        acquireSingleInstanceLock();
        Application.launch(PhoneSupportApplication.class, args);
    }

    private static void acquireSingleInstanceLock() throws Exception {
        var paths = new StoragePaths();
        lockChannel = FileChannel.open(paths.lockFile(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        appLock = lockChannel.tryLock();
        if (appLock == null) throw new IllegalStateException("Telefonhjelp kjører allerede.");
    }

    @Override
    public void init() {
        context = new SpringApplicationBuilder(PhoneSupportApplication.class)
                .headless(false)
                .properties("logging.file.name=" + logPath())
                .run(getParameters().getRaw().toArray(String[]::new));
    }

    private String logPath() {
        try { return new StoragePaths().root().resolve("telefonhjelp.log").toString(); }
        catch (IOException exception) { throw new IllegalStateException("Kunne ikke opprette privat loggmappe.", exception); }
    }

    @Override
    public void start(Stage stage) {
        var port = ((WebServerApplicationContext) context).getWebServer().getPort();
        var origin = "http://127.0.0.1:" + port;
        var webView = new WebView();
        webView.setContextMenuEnabled(false);
        webView.getEngine().setConfirmHandler(message -> {
            var confirm = new ButtonType("Ja", ButtonData.OK_DONE);
            var cancel = new ButtonType("Avbryt", ButtonData.CANCEL_CLOSE);
            var dialog = new Alert(Alert.AlertType.CONFIRMATION, message, confirm, cancel);
            dialog.initOwner(stage);
            dialog.setTitle("Telefonhjelp");
            dialog.setHeaderText(null);
            return dialog.showAndWait().orElse(cancel) == confirm;
        });
        desktopBridge = connectDesktop(webView, stage, origin, context.getBean(DesktopSession.class), context.getBean(FileAuthorizations.class));
        webView.getEngine().load(origin + "/");
        stage.setTitle("Telefonhjelp");
        stage.setMinWidth(800);
        stage.setMinHeight(560);
        var bounds = Screen.getPrimary().getVisualBounds();
        var scene = new Scene(webView, Math.min(1360, bounds.getWidth()), Math.min(840, bounds.getHeight()));
        stage.setScene(scene);
        Runnable fitContent = () -> {
            var widthScale = Math.max(0.72, scene.getWidth() / 1280.0);
            var heightScale = Math.max(0.72, scene.getHeight() / 720.0);
            webView.setZoom(Math.min(1.0, Math.min(widthScale, heightScale)));
        };
        scene.widthProperty().addListener((observable, oldValue, newValue) -> fitContent.run());
        scene.heightProperty().addListener((observable, oldValue, newValue) -> fitContent.run());
        stage.setOnCloseRequest(event -> Platform.exit());
        stage.show();
        stage.setMaximized(true);
        fitContent.run();
    }

    static DesktopBridge connectDesktop(WebView webView, Stage stage, String origin, DesktopSession session, FileAuthorizations authorizations) {
        webView.getEngine().setCreatePopupHandler(features -> null);
        webView.getEngine().locationProperty().addListener((observable, oldLocation, newLocation) -> {
            if (!DesktopSession.trustedPage(newLocation, origin)) {
                webView.getEngine().getLoadWorker().cancel();
                Platform.runLater(() -> webView.getEngine().load(origin + "/"));
            }
        });
        var bridge = new DesktopBridge(stage, authorizations,
                () -> DesktopSession.trustedPage(webView.getEngine().getLocation(), origin)
                        && DesktopSession.trustedPage(String.valueOf(webView.getEngine().executeScript("window.location.href")), origin));
        // Vue's hash-history initialization can leave WebKit's load worker RUNNING.
        // Bootstrap when the trusted document's module is ready, independently of that state.
        var bootstrap = new javafx.animation.Timeline();
        bootstrap.setCycleCount(400);
        bootstrap.getKeyFrames().add(new javafx.animation.KeyFrame(javafx.util.Duration.millis(50), event -> {
            if (!DesktopSession.trustedPage(webView.getEngine().getLocation(), origin)) return;
            var documentLocation = String.valueOf(webView.getEngine().executeScript("window.location.href"));
            if (!DesktopSession.trustedPage(documentLocation, origin)
                    || !Boolean.TRUE.equals(webView.getEngine().executeScript("document.readyState !== 'loading' && typeof window.acceptDesktopSession === 'function'"))) return;
            var window = (JSObject) webView.getEngine().executeScript("window");
            window.setMember("desktop", bridge);
            bootstrap.stop();
            window.call("acceptDesktopSession", session.credential());
        }));
        webView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldState, state) -> {
            if (state == Worker.State.SCHEDULED) bootstrap.playFromStart();
            if (state == Worker.State.CANCELLED || state == Worker.State.FAILED) bootstrap.stop();
        });
        return bridge;
    }

    @Override
    public void stop() throws Exception {
        if (context != null) context.close();
        if (appLock != null) appLock.release();
        if (lockChannel != null) lockChannel.close();
        if (restartCommand != null) new ProcessBuilder(restartCommand).start();
    }

    public static boolean requestRestart() {
        var command = ProcessHandle.current().info().command().orElse("");
        var fileName = new File(command).getName().toLowerCase();
        if (command.isBlank() || fileName.equals("java.exe") || fileName.equals("javaw.exe")) return false;
        restartCommand = command;
        Thread.startVirtualThread(() -> {
            try { Thread.sleep(800); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            Platform.runLater(Platform::exit);
        });
        return true;
    }

    public static final class DesktopBridge {
        private final Stage owner;
        private final FileAuthorizations authorizations;
        private final java.util.function.BooleanSupplier trusted;
        DesktopBridge(Stage owner, FileAuthorizations authorizations, java.util.function.BooleanSupplier trusted) {
            this.owner = owner; this.authorizations = authorizations; this.trusted = trusted;
        }

        private void requireTrusted() { if (!trusted.getAsBoolean()) throw new SecurityException("Siden er ikke tillatt."); }

        public String chooseBackupPath() throws Exception {
            requireTrusted();
            var chooser = databaseChooser("Lagre sikkerhetskopi");
            chooser.setInitialFileName("telefonhjelp-" + LocalDate.now() + ".thbackup");
            var selected = chooser.showSaveDialog(owner);
            if (selected == null) return "";
            var overwrite = selected.exists();
            if (overwrite && !confirm("Erstatt den valgte filen? Eksisterende innhold blir slettet.")) return "";
            var password = password(true);
            if (password == null) return "";
            try { requireTrusted(); return authorizations.issue(selected.toPath(), FileAuthorizations.Operation.BACKUP, overwrite, password); }
            finally { java.util.Arrays.fill(password, '\0'); }
        }

        public String chooseRestorePath() throws Exception {
            requireTrusted();
            var chooser = databaseChooser("Velg sikkerhetskopi");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Eldre sikkerhetskopi (*.db)", "*.db"));
            var selected = chooser.showOpenDialog(owner);
            if (selected == null || !confirm("Gjenoppretting erstatter alle vanlige data og sletter midlertidig informasjon. Fortsette?")) return "";
            var password = selected.getName().endsWith(".thbackup") ? password(false) : new char[0];
            if (password == null) return "";
            try { requireTrusted(); return authorizations.issue(selected.toPath(), FileAuthorizations.Operation.RESTORE, false, password); }
            finally { java.util.Arrays.fill(password, '\0'); }
        }

        public String saveDownload(String fileName, String base64Data) throws IOException {
            requireTrusted();
            if (fileName == null || fileName.length() > 120 || base64Data == null || base64Data.length() > 24_000_000) throw new IOException("Rapporten er for stor.");
            var safeName = Path.of(fileName).getFileName().toString();
            if (!safeName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) throw new IOException("Rapporten må være en Excel-fil.");
            var downloads = no.telefonhjelp.config.PrivateFiles.localPath(downloadsDirectory());
            Files.createDirectories(downloads);
            var extensionIndex = safeName.length() - ".xlsx".length();
            var baseName = safeName.substring(0, extensionIndex);
            var content = Base64.getDecoder().decode(base64Data);
            for (var copy = 0; copy < 1000; copy++) {
                var candidateName = copy == 0 ? safeName : baseName + " (" + copy + ").xlsx";
                var destination = downloads.resolve(candidateName);
                try {
                    Files.write(destination, content, StandardOpenOption.CREATE_NEW);
                    return destination.toString();
                } catch (FileAlreadyExistsException ignored) { }
            }
            throw new IOException("For mange rapportfiler med samme navn.");
        }

        public void exitApplication() { requireTrusted(); Platform.runLater(Platform::exit); }

        private boolean confirm(String message) {
            var dialog = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
            dialog.initOwner(owner); dialog.setHeaderText(null);
            return dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
        }

        private char[] password(boolean creating) {
            var dialog = new javafx.scene.control.Dialog<char[]>();
            dialog.initOwner(owner); dialog.setTitle(creating ? "Beskytt sikkerhetskopien" : "Åpne sikkerhetskopien");
            var password = new javafx.scene.control.PasswordField();
            var repeated = new javafx.scene.control.PasswordField();
            var fields = new javafx.scene.layout.VBox(10, new javafx.scene.control.Label(creating ? "Passord (minst 12 tegn). Oppbevar det trygt; det kan ikke gjenopprettes." : "Passord for sikkerhetskopien"), password);
            if (creating) fields.getChildren().addAll(new javafx.scene.control.Label("Gjenta passordet"), repeated);
            dialog.getDialogPane().setContent(fields);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            var okay = dialog.getDialogPane().lookupButton(ButtonType.OK);
            okay.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                    () -> password.getLength() < (creating ? 12 : 1) || password.getLength() > 256 || (creating && !password.getText().equals(repeated.getText())), password.textProperty(), repeated.textProperty()));
            dialog.setResultConverter(button -> button == ButtonType.OK ? password.getText().toCharArray() : null);
            var result = dialog.showAndWait().orElse(null); password.clear(); repeated.clear();
            return result;
        }

        private Path downloadsDirectory() {
            try { return Path.of(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Downloads)); }
            catch (RuntimeException | LinkageError ignored) { return Path.of(System.getProperty("user.home"), "Downloads"); }
        }

        private FileChooser databaseChooser(String title) {
            var chooser = new FileChooser();
            chooser.setTitle(title);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Kryptert sikkerhetskopi (*.thbackup)", "*.thbackup"));
            return chooser;
        }
    }
}
