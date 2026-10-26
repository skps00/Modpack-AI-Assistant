package com.skps9.packai.client.routes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.skps9.packai.client.routes.RouteStoreCodec.SavedRoute;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.loading.FMLPaths;

/** Per-world persisted obtain routes under {@code <gamedir>/packai/routes/} (up to {@link RouteStoreCodec#MAX_ROUTES} per world). */
public final class RouteStore {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final DateTimeFormatter CORRUPT_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int WORLD_KEY_MAX = 64;
    private static final int WORLD_KEY_PREFIX_MAX = 24;

    private RouteStore() {}

    public static String currentWorldKey() {
        return worldKeyFor(currentWorldRaw());
    }

    /** Stable per-world file key: {@code <prefix>-<hash8>} from raw world name or server address. */
    public static String worldKeyFor(String raw) {
        String normalized = raw == null ? "" : raw;
        String prefix = cleanWorldKeyPrefix(worldKeyPrefix(normalized));
        return prefix + "-" + sha256Hex8(normalized);
    }

    public static List<SavedRoute> load() {
        try {
            Path file = routeFile();
            if (Files.isRegularFile(file)) {
                return loadFromFile(file, currentWorldKey());
            }
            List<SavedRoute> migrated = tryMigrateLegacyRoutes(file);
            if (migrated != null) {
                return migrated;
            }
            return List.of();
        } catch (IOException e) {
            LOGGER.warn("packai routes: load failed for {}", currentWorldKey(), e);
            return List.of();
        } catch (Throwable t) {
            LOGGER.warn("packai routes: load failed", t);
            return List.of();
        }
    }

    /**
     * Load routes from a JSON file (headless harness + {@link #load()}).
     * Oversize files are renamed, not deleted.
     */
    public static List<SavedRoute> loadFromFile(Path file, String worldKey) throws IOException {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        long bytes = Files.size(file);
        if (bytes > RouteStoreCodec.MAX_FILE_BYTES) {
            String stamp = CORRUPT_STAMP.format(LocalDateTime.now());
            String key = worldKey == null || worldKey.isBlank() ? "unknown" : worldKey;
            String backupName = "routes-" + key + ".json.corrupt-" + stamp;
            Path backup = file.getParent().resolve(backupName);
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn(
                    "Pack AI routes load_oversize bytes={} limit={} backup={}",
                    bytes,
                    RouteStoreCodec.MAX_FILE_BYTES,
                    backupName);
            return List.of();
        }
        String json = Files.readString(file, StandardCharsets.UTF_8);
        return RouteStoreCodec.decode(json);
    }

    public static void save(SavedRoute route) {
        try {
            if (route == null) {
                return;
            }
            List<SavedRoute> next = RouteStoreCodec.capByBytes(RouteStoreCodec.put(load(), route));
            writeAll(next);
        } catch (Throwable t) {
            LOGGER.warn("packai routes: save failed", t);
        }
    }

    public static void delete(String itemId) {
        try {
            if (itemId == null || itemId.isBlank()) {
                return;
            }
            List<SavedRoute> kept = new ArrayList<>();
            for (SavedRoute r : load()) {
                if (r != null && !itemId.equals(r.itemId())) {
                    kept.add(r);
                }
            }
            writeAll(kept);
        } catch (Throwable t) {
            LOGGER.warn("packai routes: delete failed", t);
        }
    }

    public static void clear() {
        try {
            writeAll(List.of());
        } catch (Throwable t) {
            LOGGER.warn("packai routes: clear failed", t);
        }
    }

    public static String modsStamp() {
        try {
            Path modsDir = FMLPaths.MODSDIR.get();
            if (modsDir == null || !Files.isDirectory(modsDir)) {
                return "unknown";
            }
            List<String> lines = new ArrayList<>();
            try (Stream<Path> stream = Files.list(modsDir)) {
                stream.filter(p -> {
                            String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return name.endsWith(".jar") && Files.isRegularFile(p);
                        })
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(
                                p -> {
                                    try {
                                        long size = Files.size(p);
                                        lines.add(p.getFileName().toString() + ":" + size);
                                    } catch (IOException ignored) {
                                        // skip unreadable jar
                                    }
                                });
            }
            if (lines.isEmpty()) {
                return "unknown";
            }
            lines.sort(String::compareTo);
            return sha1Hex16(String.join("\n", lines));
        } catch (Throwable t) {
            LOGGER.warn("packai routes: modsStamp failed", t);
            return "unknown";
        }
    }

    private static String currentWorldRaw() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return "";
            }
            ServerData serverData = mc.getCurrentServer();
            if (serverData != null && serverData.ip != null && !serverData.ip.isBlank()) {
                return serverData.ip;
            }
            MinecraftServer sp = mc.getSingleplayerServer();
            if (sp != null) {
                String name = sp.getWorldData().getLevelName();
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("packai routes: world raw failed", t);
        }
        return "";
    }

    private static Path routeFile() throws IOException {
        Minecraft mc = Minecraft.getInstance();
        Path base = mc.gameDirectory.toPath().resolve("packai").resolve("routes");
        Files.createDirectories(base);
        return base.resolve(currentWorldKey() + ".json");
    }

    private static List<SavedRoute> tryMigrateLegacyRoutes(Path newFile) {
        try {
            String raw = currentWorldRaw();
            String oldKey = sanitizeWorldKey(raw);
            Path oldFile = newFile.getParent().resolve(oldKey + ".json");
            if (!Files.isRegularFile(oldFile)) {
                return null;
            }
            String json = Files.readString(oldFile, StandardCharsets.UTF_8);
            List<SavedRoute> routes = RouteStoreCodec.decode(json);
            writeAll(routes);
            LOGGER.info(
                    "packai routes: migrated {} -> {}",
                    oldFile.getFileName(),
                    newFile.getFileName());
            return routes;
        } catch (Throwable t) {
            LOGGER.warn("packai routes: legacy migrate failed", t);
            return null;
        }
    }

    private static void writeAll(List<SavedRoute> routes) throws IOException {
        Path file = routeFile();
        String json = RouteStoreCodec.encode(routes);
        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailed) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String sanitizeWorldKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        StringBuilder sb = new StringBuilder(Math.min(raw.length(), WORLD_KEY_MAX));
        for (int i = 0; i < raw.length() && sb.length() < WORLD_KEY_MAX; i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '.'
                    || c == '_'
                    || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String out = sb.toString();
        return out.isEmpty() ? "unknown" : out;
    }

    private static String worldKeyPrefix(String raw) {
        if (raw == null || raw.isBlank()) {
            return "world";
        }
        StringBuilder sb = new StringBuilder(Math.min(raw.length(), WORLD_KEY_PREFIX_MAX));
        for (int i = 0; i < raw.length() && sb.length() < WORLD_KEY_PREFIX_MAX; i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '.'
                    || c == '_'
                    || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String out = sb.toString();
        while (out.endsWith("_") && !out.isEmpty()) {
            out = out.substring(0, out.length() - 1);
        }
        return out.isEmpty() ? "world" : out;
    }

    /** Strip unsafe prefix edges and collapse {@code ..} before appending hash. */
    private static String cleanWorldKeyPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "world";
        }
        int start = 0;
        int end = prefix.length();
        while (start < end && (prefix.charAt(start) == '.' || prefix.charAt(start) == '_')) {
            start++;
        }
        while (end > start && (prefix.charAt(end - 1) == '.' || prefix.charAt(end - 1) == '_')) {
            end--;
        }
        if (start >= end) {
            return "world";
        }
        String trimmed = prefix.substring(start, end);
        StringBuilder sb = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '.') {
                sb.append('.');
                while (i + 1 < trimmed.length() && trimmed.charAt(i + 1) == '.') {
                    i++;
                }
            } else {
                sb.append(c);
            }
        }
        String out = sb.toString();
        return out.isEmpty() ? "world" : out;
    }

    private static String sha256Hex8(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String normalized = raw == null ? "" : raw;
            byte[] dig = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            return "00000000";
        }
    }

    private static String sha1Hex16(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] dig = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        }
    }
}
