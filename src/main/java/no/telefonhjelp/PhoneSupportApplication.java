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
        catch (IOException exception) { return System.getProperty("java.io.tmpdir") + "/telefonhjelp.log"; }
    }

    @Override
    public void start(Stage stage) {
        var port = ((WebServerApplicationContext) context).getWebServer().getPort();
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
        desktopBridge = new DesktopBridge(stage);
        webView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldState, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                var window = (JSObject) webView.getEngine().executeScript("window");
                window.setMember("desktop", desktopBridge);
                webView.getEngine().executeScript("window.dispatchEvent(new Event('desktop-ready'))");
            }
        });
        webView.getEngine().load("http://127.0.0.1:" + port + "/");
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
        DesktopBridge(Stage owner) { this.owner = owner; }

        public String chooseBackupPath() {
            var chooser = databaseChooser("Lagre sikkerhetskopi");
            chooser.setInitialFileName("telefonhjelp-" + LocalDate.now() + ".db");
            var selected = chooser.showSaveDialog(owner);
            return selected == null ? "" : selected.getAbsolutePath();
        }

        public String chooseRestorePath() {
            var selected = databaseChooser("Velg sikkerhetskopi").showOpenDialog(owner);
            return selected == null ? "" : selected.getAbsolutePath();
        }

        public String saveDownload(String fileName, String base64Data) throws IOException {
            var safeName = Path.of(fileName).getFileName().toString();
            if (!safeName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) throw new IOException("Rapporten må være en Excel-fil.");
            var downloads = downloadsDirectory();
            Files.createDirectories(downloads);
            var extensionIndex = safeName.length() - ".xlsx".length();
            var baseName = safeName.substring(0, extensionIndex);
            var content = Base64.getDecoder().decode(base64Data);
            for (var copy = 0; ; copy++) {
                var candidateName = copy == 0 ? safeName : baseName + " (" + copy + ").xlsx";
                var destination = downloads.resolve(candidateName);
                try {
                    Files.write(destination, content, StandardOpenOption.CREATE_NEW);
                    return destination.toString();
                } catch (FileAlreadyExistsException ignored) { }
            }
        }

        public void exitApplication() { Platform.runLater(Platform::exit); }

        private Path downloadsDirectory() {
            try { return Path.of(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Downloads)); }
            catch (RuntimeException | LinkageError ignored) { return Path.of(System.getProperty("user.home"), "Downloads"); }
        }

        private FileChooser databaseChooser(String title) {
            var chooser = new FileChooser();
            chooser.setTitle(title);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Telefonhjelp-sikkerhetskopi (*.db)", "*.db"));
            return chooser;
        }
    }
}
