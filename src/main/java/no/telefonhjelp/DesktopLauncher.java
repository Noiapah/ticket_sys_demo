package no.telefonhjelp;

/** Plain Java entry point avoids the JDK's module-only JavaFX launcher path. */
public final class DesktopLauncher {
    private DesktopLauncher() {}
    public static void main(String[] args) throws Exception { PhoneSupportApplication.launchDesktop(args); }
}
