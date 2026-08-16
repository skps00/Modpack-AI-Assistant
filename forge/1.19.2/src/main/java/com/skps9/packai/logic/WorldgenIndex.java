package com.skps9.packai.logic;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Pack-local worldgen index. Loose datapack / kubejs / openloader / overrides win the same
 * id over {@code mods/*.jar}. No coordinates. Jar scan is capped (no disk cache).
 * <p>
 * Public API: {@link #ensure(Path)}, {@link #lookup(String, Path, String)}.
 */
public final class WorldgenIndex {
    public static final WorldgenIndex INSTANCE = new WorldgenIndex();

    static final int MAX_FILES_PER_JAR = 250;
    static final int MAX_ENTRY_BYTES = 256_000;
    static final int MAX_LOOSE_FILES = 4000;
    static final int MAX_JARS = 400;
    static final int MAX_ASK_LINES = 20;

    private static final String[] LOOSE_ROOTS = {
        "datapacks",
        "kubejs/data",
        "openloader",
        "global_packs",
        "overrides"
    };

    private final Object lock = new Object();
    private volatile Path loadedDir;
    private volatile boolean ready;
    private WorldgenFacts.Store store = new WorldgenFacts.Store();

    private WorldgenIndex() {}

    /** Idempotent per {@code gameDir}. Missing dirs = empty index, not an error. */
    public static void ensure(Path gameDir) {
        INSTANCE.doEnsure(gameDir);
    }

    /**
     * Lines each start with {@code [WORLDGEN]}, or one honest miss (no coordinates).
     * Calls {@link #ensure(Path)} first.
     */
    public static List<String> lookup(String query, Path gameDir, String lang) {
        return INSTANCE.doLookup(query, gameDir, lang);
    }

    /** Tests / reload. */
    public static void reset() {
        INSTANCE.doReset();
    }

    private void doEnsure(Path gameDir) {
        if (gameDir == null || !Files.isDirectory(gameDir)) {
            return;
        }
        Path abs = gameDir.toAbsolutePath().normalize();
        synchronized (lock) {
            if (ready && abs.equals(loadedDir)) {
                return;
            }
            WorldgenFacts.Store next = new WorldgenFacts.Store();
            scanJars(abs.resolve("mods"), next);
            scanLoose(abs, next);
            this.store = next;
            this.loadedDir = abs;
            this.ready = true;
        }
    }

    private List<String> doLookup(String query, Path gameDir, String lang) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of(WorldgenFacts.missLine("", lang));
        }
        doEnsure(gameDir);
        List<String> lines;
        synchronized (lock) {
            lines = store.formatMatches(q, MAX_ASK_LINES);
        }
        if (lines.isEmpty()) {
            return List.of(WorldgenFacts.missLine(q, lang));
        }
        return List.copyOf(lines);
    }

    private void doReset() {
        synchronized (lock) {
            store = new WorldgenFacts.Store();
            loadedDir = null;
            ready = false;
        }
    }

    private static void scanLoose(Path gameDir, WorldgenFacts.Store dest) {
        int n = 0;
        for (String rel : LOOSE_ROOTS) {
            Path root = gameDir.resolve(rel);
            if (!Files.isDirectory(root)) {
                continue;
            }
            n += walkTree(root, dest, MAX_LOOSE_FILES - n, true);
            if (n >= MAX_LOOSE_FILES) {
                return;
            }
        }
    }

    private static int walkTree(Path start, WorldgenFacts.Store dest, int remaining, boolean overwrite) {
        if (remaining <= 0 || start == null || !Files.isDirectory(start)) {
            return 0;
        }
        int n = 0;
        try (var walk = Files.walk(start, 16)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                if (n >= remaining) {
                    break;
                }
                String path = p.toString().replace('\\', '/');
                if (!WorldgenFacts.isWorldgenPath(path)) {
                    continue;
                }
                n++;
                try {
                    byte[] bytes = Files.readAllBytes(p);
                    if (bytes.length == 0 || bytes.length > MAX_ENTRY_BYTES) {
                        continue;
                    }
                    WorldgenFacts.ingest(dest, path, new String(bytes, StandardCharsets.UTF_8), overwrite);
                } catch (Exception ignored) {
                    // soft per file
                }
            }
        } catch (Exception ignored) {
            // soft per tree
        }
        return n;
    }

    private static void scanJars(Path mods, WorldgenFacts.Store dest) {
        if (mods == null || !Files.isDirectory(mods)) {
            return;
        }
        int jars = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(mods, "*.jar")) {
            for (Path jar : ds) {
                if (jars >= MAX_JARS) {
                    return;
                }
                String name = jar.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.startsWith("packai-") || name.contains("packai")) {
                    continue;
                }
                jars++;
                try {
                    scanJarFile(jar, dest);
                } catch (Exception ignored) {
                    // soft-skip bad jar
                }
            }
        } catch (IOException ignored) {
            // missing / unreadable mods
        }
    }

    static void scanJarFile(Path jar, WorldgenFacts.Store dest) throws IOException {
        int files = 0;
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) {
                    continue;
                }
                String path = e.getName().replace('\\', '/');
                if (!WorldgenFacts.isWorldgenPath(path)) {
                    continue;
                }
                if (files >= MAX_FILES_PER_JAR) {
                    break;
                }
                if (e.getSize() > MAX_ENTRY_BYTES) {
                    continue;
                }
                String text = readZipEntry(zf, e);
                if (text == null || text.isBlank()) {
                    continue;
                }
                files++;
                WorldgenFacts.ingest(dest, path, text, false);
            }
        }
    }

    private static String readZipEntry(ZipFile zf, ZipEntry e) {
        try (InputStream in = zf.getInputStream(e);
             InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            int total = 0;
            while ((n = r.read(buf)) >= 0) {
                total += n;
                if (total > MAX_ENTRY_BYTES) {
                    return null;
                }
                sb.append(buf, 0, n);
            }
            return sb.toString();
        } catch (IOException ex) {
            return null;
        }
    }
}
