package dev.surgelyrics.core;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

/** Spotify tokens live in their own file (owner-only on POSIX), never in the shareable settings file. */
public final class TokenStore {
    public volatile String access = "";
    public volatile String refresh = "";
    public volatile long expiresAtMs = 0;

    public static TokenStore load(Path f) {
        TokenStore t = new TokenStore();
        try {
            if (Files.exists(f)) {
                Map<String, Object> m = Json.obj(Json.parse(Files.readString(f)));
                t.access = Json.str(m.get("access"), "");
                t.refresh = Json.str(m.get("refresh"), "");
                t.expiresAtMs = (long) Json.num(m.get("expiresAtMs"), 0);
            }
        } catch (Exception ignored) { }
        return t;
    }

    public synchronized void save(Path f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("access", access); m.put("refresh", refresh); m.put("expiresAtMs", expiresAtMs);
        try {
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.createDirectories(f.toAbsolutePath().getParent());
            Files.writeString(tmp, Json.write(m));
            try { Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------")); }
            catch (UnsupportedOperationException | IOException ignored) { }   // Windows: skip
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) { }
    }

    public boolean loggedIn() { return !refresh.isEmpty(); }
    public void clear() { access = ""; refresh = ""; expiresAtMs = 0; }
}
