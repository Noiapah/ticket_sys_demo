package no.telefonhjelp.service;

import no.telefonhjelp.config.DesktopSession;
import no.telefonhjelp.config.PrivateFiles;
import no.telefonhjelp.config.StoragePaths;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Issued only by the native chooser, never by an HTTP endpoint. */
@Component
public final class FileAuthorizations {
    public enum Operation { BACKUP, RESTORE }
    public record Grant(Path path, Operation operation, boolean overwrite, Object fileKey, long size, java.nio.file.attribute.FileTime modified, Instant expires, char[] password) implements AutoCloseable {
        @Override public void close() { Arrays.fill(password, '\0'); }
    }
    private final Map<String, Grant> grants = new HashMap<>();
    private final StoragePaths paths;
    private final Clock clock;
    public FileAuthorizations(StoragePaths paths, Clock clock) { this.paths = paths; this.clock = clock; }

    public synchronized String issue(Path selected, Operation operation, boolean overwrite, char[] password) throws Exception {
        clearExpired();
        var path = checkPath(selected);
        var exists = Files.exists(path, LinkOption.NOFOLLOW_LINKS);
        if (exists && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw AppException.badRequest("Velg en vanlig fil.");
        if (operation == Operation.BACKUP && exists && !overwrite) throw AppException.badRequest("Overskriving må bekreftes i filvelgeren.");
        if (operation == Operation.RESTORE && !exists) throw AppException.badRequest("Fant ikke sikkerhetskopien.");
        if (operation == Operation.BACKUP && (!path.toString().endsWith(".thbackup") || password.length < 12)) throw AppException.badRequest("Bruk .thbackup og et passord på minst 12 tegn.");
        if (password.length > 256) throw AppException.badRequest("Passordet er for langt.");
        var attrs = exists ? Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS) : null;
        // One pending native selection at a time; previous secrets are discarded.
        grants.values().forEach(Grant::close); grants.clear();
        var token = DesktopSession.newToken();
        grants.put(token, new Grant(path, operation, overwrite, attrs == null ? null : attrs.fileKey(), attrs == null ? -1 : attrs.size(), attrs == null ? null : attrs.lastModifiedTime(), clock.instant().plusSeconds(60), password.clone()));
        return token;
    }

    public synchronized Grant consume(String token, Operation operation) throws Exception {
        clearExpired();
        var grant = token == null ? null : grants.remove(token);
        if (grant == null) throw AppException.badRequest("Velg filen på nytt i skrivebordsprogrammet.");
        try {
            if (grant.operation() != operation) throw AppException.badRequest("Ugyldig filvalg.");
            recheck(grant);
            return grant;
        } catch (Exception exception) { grant.close(); throw exception; }
    }

    public void recheck(Grant grant) throws Exception {
        checkPath(grant.path());
        if (grant.size() < 0) {
            if (Files.exists(grant.path(), LinkOption.NOFOLLOW_LINKS)) throw AppException.conflict("Filen er endret. Velg den på nytt.");
        } else {
            var attrs = Files.readAttributes(grant.path(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attrs.isRegularFile() || !Objects.equals(attrs.fileKey(), grant.fileKey()) || attrs.size() != grant.size() || !attrs.lastModifiedTime().equals(grant.modified())) throw AppException.conflict("Filen er endret. Velg den på nytt.");
        }
    }

    private Path checkPath(Path path) throws Exception {
        path = PrivateFiles.localPath(path);
        if (path.startsWith(paths.root().toRealPath())) throw AppException.badRequest("Velg en fil utenfor programmets datamappe.");
        if (Files.exists(path)) {
            for (var protectedFile : List.of(paths.database(), paths.secretsDatabase(), paths.lockFile(), paths.pendingRestore())) {
                if (Files.exists(protectedFile) && Files.isSameFile(path, protectedFile)) throw AppException.badRequest("Programfiler kan ikke velges.");
            }
            // Hard links are rejected on filesystems that expose their link count.
            if (Files.getFileStore(path).supportsFileAttributeView("unix") && ((Number) Files.getAttribute(path, "unix:nlink")).intValue() != 1) throw AppException.badRequest("Lenker er ikke tillatt.");
        }
        return path;
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 15_000)
    public synchronized void clearExpired() {
        grants.values().removeIf(grant -> { if (!grant.expires().isAfter(clock.instant())) { grant.close(); return true; } return false; });
    }
}
