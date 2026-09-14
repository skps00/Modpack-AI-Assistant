package com.skps9.packai.logic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Headless knowledge facts: {@code item:<id> -[obtain|use|worn]-> … (source:… tier:A|B|C)}.
 */
public final class KnowledgeLookup {
    public static final int MAX_FACTS = 8;
    public static final String C_DISCLAIMER = "（任務描述，可能未涵蓋全部）";
    public static final String MC_DEFAULT = "1.19.2";
    public static final String LOADER_DEFAULT = "forge";

    private KnowledgeLookup() {}

    public static List<String> factsForItem(String itemId, int maxFacts) {
        return factsForItem(liveGameDir(), itemId, maxFacts);
    }

    public static List<String> factsForItem(Path gameDir, String itemId, int maxFacts) {
        return factsForItem(
                gameDir, itemId, maxFacts, liveEnabled(), MC_DEFAULT, LOADER_DEFAULT,
                liveModVersion(namespaceOf(itemId)));
    }

    static List<String> factsForItem(
            Path gameDir,
            String itemId,
            int maxFacts,
            boolean enabled,
            String mc,
            String loader,
            String installedModVersion
    ) {
        if (!enabled || gameDir == null || itemId == null || itemId.isBlank()) {
            return List.of();
        }
        int cap = maxFacts <= 0 ? MAX_FACTS : Math.min(MAX_FACTS, maxFacts);
        KnowledgeEntry e = KnowledgeStore.get(gameDir, itemId);
        if (e == null) {
            return List.of();
        }
        String id = e.item.isBlank() ? itemId.trim().toLowerCase(Locale.ROOT) : e.item;
        String verNote = versionNote(e, mc, loader, installedModVersion);
        List<Scored> scored = new ArrayList<>();
        addFacts(scored, id, e.obtain, verNote);
        addFacts(scored, id, e.use, verNote);
        addFacts(scored, id, e.worn, verNote);
        scored.sort(Comparator
                .comparingInt((Scored s) -> s.rank)
                .thenComparingInt(s -> s.ord));
        List<String> out = new ArrayList<>();
        for (Scored s : scored) {
            if (out.size() >= cap) {
                break;
            }
            out.add(s.line);
        }
        return List.copyOf(out);
    }

    private static void addFacts(List<Scored> out, String item, List<KnowledgeEntry.Fact> facts, String verNote) {
        if (facts == null) {
            return;
        }
        int i = 0;
        for (KnowledgeEntry.Fact f : facts) {
            String line = format(item, f, verNote);
            if (line != null && !line.isBlank()) {
                out.add(new Scored(tierRank(f.tier), out.size() * 10 + i, line));
            }
            i++;
        }
    }

    static String format(String item, KnowledgeEntry.Fact f, String verNote) {
        if (f == null) {
            return "";
        }
        String kind = f.kind == null || f.kind.isBlank() ? "use" : f.kind;
        StringBuilder body = new StringBuilder();
        appendKv(body, "type", f.type);
        appendKv(body, "mob", f.mob);
        appendKv(body, "container", f.container);
        appendKv(body, "trigger", f.trigger);
        appendKv(body, "effect", f.effect);
        appendKv(body, "slot", f.slot);
        if (f.effects != null && !f.effects.isEmpty()) {
            appendKv(body, "effects", String.join(",", f.effects));
        }
        appendKv(body, "chance", f.chance);
        if (f.requires != null && !f.requires.isEmpty()) {
            appendKv(body, "requires", String.join(",", f.requires));
        }
        if (body.isEmpty()) {
            body.append("listed");
        }
        String tier = normalizeTier(f.tier);
        String source = f.source == null || f.source.isBlank() ? "unknown" : f.source;
        StringBuilder sb = new StringBuilder();
        sb.append("item:").append(item).append(" -[").append(kind).append("]-> ").append(body);
        if (f.conflict) {
            sb.append(" conflict");
        }
        if ("C".equals(tier)) {
            sb.append(' ').append(C_DISCLAIMER);
        }
        sb.append(" (source:").append(source).append(" tier:").append(tier).append(')');
        if (verNote != null && !verNote.isBlank()) {
            sb.append(' ').append(verNote);
        }
        return sb.toString();
    }

    private static void appendKv(StringBuilder sb, String k, String v) {
        if (v == null || v.isBlank()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(' ');
        }
        sb.append(k).append(':').append(v);
    }

    static String normalizeTier(String tier) {
        if (tier == null) {
            return "C";
        }
        String t = tier.trim().toUpperCase(Locale.ROOT);
        if ("A".equals(t) || "B".equals(t) || "C".equals(t)) {
            return t;
        }
        return "C";
    }

    static int tierRank(String tier) {
        String t = normalizeTier(tier);
        if ("A".equals(t)) {
            return 0;
        }
        if ("B".equals(t)) {
            return 1;
        }
        return 2;
    }

    static String versionNote(KnowledgeEntry e, String mc, String loader, String installedModVersion) {
        if (e == null) {
            return "";
        }
        boolean mcOk = e.mc == null || e.mc.isBlank()
                || e.mc.equalsIgnoreCase(blank(mc, MC_DEFAULT));
        boolean loaderOk = e.loader == null || e.loader.isBlank()
                || e.loader.equalsIgnoreCase(blank(loader, LOADER_DEFAULT));
        boolean verOk = matchesModVersions(e.modVersions, installedModVersion);
        if (mcOk && loaderOk && verOk) {
            return "";
        }
        StringBuilder shown = new StringBuilder();
        if (!e.mc.isBlank()) {
            shown.append(e.mc);
        }
        if (!e.modVersions.isEmpty()) {
            if (!shown.isEmpty()) {
                shown.append(' ');
            }
            shown.append(String.join(",", e.modVersions));
        }
        if (shown.isEmpty()) {
            shown.append(e.loader.isBlank() ? "other" : e.loader);
        }
        return "(注意：entry 為 " + shown + " 版本寫)";
    }

    static boolean matchesModVersions(List<String> patterns, String installed) {
        if (patterns == null || patterns.isEmpty()) {
            return true;
        }
        if (installed == null || installed.isBlank()) {
            return true;
        }
        String have = installed.trim();
        for (String p : patterns) {
            if (p == null || p.isBlank()) {
                continue;
            }
            if (versionMatches(have, p.trim())) {
                return true;
            }
        }
        return false;
    }

    static boolean versionMatches(String installed, String pattern) {
        if (installed.equalsIgnoreCase(pattern)) {
            return true;
        }
        String p = pattern.toLowerCase(Locale.ROOT);
        String have = installed.toLowerCase(Locale.ROOT);
        // ponytail: prefix glob (0.5.x); ceiling = 0.50 false-positive; upgrade = maven ComparableVersion
        if (p.endsWith(".x") || p.endsWith(".*")) {
            String prefix = p.substring(0, p.length() - 1);
            return have.startsWith(prefix) || have.equals(p.substring(0, p.length() - 2));
        }
        return false;
    }

    static String namespaceOf(String itemId) {
        if (itemId == null) {
            return "";
        }
        int c = itemId.indexOf(':');
        return c <= 0 ? "" : itemId.substring(0, c).trim();
    }

    static boolean liveEnabled() {
        try {
            Class<?> c = Class.forName("com.skps9.packai.config.PackAiConfig");
            Object v = c.getMethod("knowledgeEnabled").invoke(null);
            return !Boolean.FALSE.equals(v);
        } catch (Throwable t) {
            return true;
        }
    }

    static Path liveGameDir() {
        try {
            Class<?> mcCl = Class.forName("net.minecraft.client.Minecraft");
            Object mc = mcCl.getMethod("getInstance").invoke(null);
            if (mc == null) {
                return null;
            }
            Object dir = mcCl.getField("gameDirectory").get(mc);
            if (dir instanceof java.io.File f) {
                return f.toPath();
            }
        } catch (Throwable ignored) {
            // headless
        }
        return null;
    }

    static String liveModVersion(String modId) {
        if (modId == null || modId.isBlank() || "minecraft".equals(modId)) {
            return "";
        }
        try {
            Class<?> modListCl = Class.forName("net.minecraftforge.fml.ModList");
            Object list = modListCl.getMethod("get").invoke(null);
            if (list == null) {
                return "";
            }
            @SuppressWarnings("unchecked")
            Optional<Object> opt = (Optional<Object>) modListCl
                    .getMethod("getModContainerById", String.class)
                    .invoke(list, modId);
            if (opt == null || opt.isEmpty()) {
                return "";
            }
            Object container = opt.get();
            Object info = container.getClass().getMethod("getModInfo").invoke(container);
            Object ver = info.getClass().getMethod("getVersion").invoke(info);
            return ver == null ? "" : ver.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String blank(String v, String d) {
        return v == null || v.isBlank() ? d : v;
    }

    private static final class Scored {
        final int rank;
        final int ord;
        final String line;

        Scored(int rank, int ord, String line) {
            this.rank = rank;
            this.ord = ord;
            this.line = line;
        }
    }
}
