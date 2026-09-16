package no.telefonhjelp.config;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.EnumSet;
import java.util.List;

public final class PrivateFiles {
    private PrivateFiles() {}

    public static void restrict(Path path) throws IOException {
        var acl = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl != null) {
            var builder = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path))
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class));
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) builder.setFlags(AclEntryFlag.DIRECTORY_INHERIT, AclEntryFlag.FILE_INHERIT);
            acl.setAcl(List.of(builder.build()));
        } else if (Files.getFileAttributeView(path, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(Files.isDirectory(path) ? "rwx------" : "rw-------"));
        } else throw new IOException("Lagringsstedet støtter ikke private filrettigheter.");
    }

    public static Path localPath(Path path) throws IOException {
        path = path.toAbsolutePath().normalize();
        if (path.toString().startsWith("\\\\") || path.toString().indexOf(':', 2) >= 0) throw new IOException("Velg en lokal fil.");
        if (com.sun.jna.Platform.isWindows()
                && com.sun.jna.platform.win32.Kernel32.INSTANCE.GetDriveType(path.getRoot().toString()) == 4) throw new IOException("Nettverksdisker er ikke tillatt.");
        for (var part = path; part != null; part = part.getParent()) {
            if (Files.exists(part, LinkOption.NOFOLLOW_LINKS)
                    && (Files.isSymbolicLink(part) || !part.toRealPath().equals(part))) throw new IOException("Lenker er ikke tillatt.");
        }
        return path;
    }
}
