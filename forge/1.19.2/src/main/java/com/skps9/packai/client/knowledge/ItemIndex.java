package com.skps9.packai.client.knowledge;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.jei.JeiUniversalSpam;
import com.skps9.packai.client.jei.PackAiJeiPlugin;
import com.skps9.packai.client.service.AskService;
import com.skps9.packai.logic.ItemIndexCache;
import com.skps9.packai.logic.ItemResolver;
import com.skps9.packai.logic.ItemVariantKeys;
import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.ReplyLang;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

/**
 * In-memory Ask item index with disk cache under {@code config/packai/item-index/}.
 * Kick stays async; JEI wait on daemon; hydrate/build (hoverName / JEI / registry) on client
 * thread via {@link Minecraft#execute}; disk write stays off-thread. Search falls back to live scan.
 */
public final class ItemIndex {
    public static final ItemIndex INSTANCE = new ItemIndex();

    private static final int JEI_WAIT_MS = 12_000;
    private static final int JEI_POLL_MS = 400;
    private static final int SCAN_CANDIDATE_CAP = 80;
    private static final long META_TTL_MS = 2000L;
    private static final long BUILD_TICK_BUDGET_NS = 8_000_000L;
    private static final int BUILD_CHUNK_ITEMS = 500;

    private final AtomicBoolean building = new AtomicBoolean(false);
    private volatile ItemIndexCache.Meta cachedMeta;
    private volatile long cachedMetaAtMs;
    private volatile boolean ready;
    private volatile ItemIndexCache.Meta loadedMeta;
    private volatile List<MemEntry> entries = List.of();

    private record MemEntry(
            String id, String label, List<String> schem, String dedupe, ItemStack stack) {}

    private Path buildGameDir;
    private ItemIndexCache.Meta buildWant;
    private Path buildCacheFile;
    private Map<String, MemEntry> buildByDedupe;
    private Iterator<ItemStack> buildJeiIter;
    private Iterator<Map.Entry<ResourceKey<Item>, Item>> buildRegIter;
    private boolean buildJeiPhaseDone;

    private ItemIndex() {}

    public boolean isReady() {
        return ready && !entries.isEmpty();
    }

    /** Drop memory so next {@link #ensureAsync} reloads / rebuilds. Disk kept. */
    public void invalidate() {
        ready = false;
        loadedMeta = null;
        entries = List.of();
        cachedMeta = null;
    }

    /** Kick async ensure using current client game dir (no-op if already matching). */
    public void ensureAsync() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null) {
            return;
        }
        ensureAsync(mc.gameDirectory.toPath());
    }

    public void ensureAsync(Path gameDir) {
        if (gameDir == null) {
            return;
        }
        ItemIndexCache.Meta want = currentMeta();
        if (want.modFp().isEmpty()) {
            return;
        }
        ItemIndexCache.Meta have = loadedMeta;
        boolean jeiNow = ModList.get().isLoaded("jei") && PackAiJeiPlugin.runtime().isPresent();
        if (ready
                && ItemIndexCache.metaMatches(have, want)
                && !entries.isEmpty()
                && !ItemIndexCache.shouldUpgradeForJei(have, jeiNow)) {
            return;
        }
        if (!building.compareAndSet(false, true)) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                runEnsure(gameDir, want);
            } catch (Exception e) {
                PackAiMod.LOGGER.debug("ItemIndex ensure failed: {}", e.toString());
                building.set(false);
            }
        }, "packai-item-index");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Score over in-memory index. {@code null} = not ready (caller should live-scan).
     */
    public List<ItemSearch.Hit> searchReady(String queryNorm, int limit) {
        if (!isReady()) {
            return null;
        }
        // Lang / mod fingerprint drift while screen open → force rebuild, fall back live.
        ItemIndexCache.Meta want = currentMetaCached();
        boolean jeiNow = ModList.get().isLoaded("jei") && PackAiJeiPlugin.runtime().isPresent();
        if (ItemIndexCache.shouldRebuild(loadedMeta, want)
                || ItemIndexCache.shouldUpgradeForJei(loadedMeta, jeiNow)) {
            invalidate();
            ensureAsync();
            return null;
        }
        String q = queryNorm == null ? "" : queryNorm;
        if (q.isEmpty() || limit <= 0) {
            return List.of();
        }
        int cap = Math.min(Math.max(limit, 1), ItemSearch.DEFAULT_LIMIT);
        Map<String, Scored> best = new LinkedHashMap<>();
        for (MemEntry e : entries) {
            int score = ItemSearch.score(q, e.id(), e.label(), e.schem());
            if (score >= 99) {
                continue;
            }
            String dedupe = e.dedupe();
            if (dedupe.isEmpty()) {
                dedupe = e.id().toLowerCase(Locale.ROOT) + "|" + normLabel(e.label());
            }
            Scored prev = best.get(dedupe);
            if (prev != null && score >= prev.score()) {
                continue;
            }
            if (prev == null && best.size() >= SCAN_CANDIDATE_CAP && !admitOverWorst(best, score, e.label())) {
                continue;
            }
            best.put(dedupe, new Scored(e.stack(), e.id(), e.label(), score));
        }
        List<Scored> ranked = new ArrayList<>(best.values());
        ranked.sort(Comparator.comparingInt(Scored::score).thenComparing(Scored::label));
        List<ItemSearch.Hit> out = new ArrayList<>(cap);
        for (Scored s : ranked) {
            if (out.size() >= cap) {
                break;
            }
            out.add(new ItemSearch.Hit(s.stack(), s.id(), s.label()));
        }
        return List.copyOf(out);
    }

    private void runEnsure(Path gameDir, ItemIndexCache.Meta want) {
        Path file = ItemIndexCache.cacheFile(gameDir, want);
        ItemIndexCache.Document disk = ItemIndexCache.load(file);
        boolean jeiNow = ModList.get().isLoaded("jei") && PackAiJeiPlugin.runtime().isPresent();
        boolean tryDisk = !ItemIndexCache.shouldRebuild(disk.meta(), want)
                && !disk.entries().isEmpty()
                && !ItemIndexCache.shouldUpgradeForJei(disk.meta(), jeiNow);
        if (!tryDisk) {
            waitForJeiQuietly();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            building.set(false);
            return;
        }
        final boolean loadDisk = tryDisk;
        final Path cacheFile = file;
        CompletableFuture.runAsync(() -> {
            List<MemEntry> hydrated = null;
            if (loadDisk && !disk.entries().isEmpty()) {
                try {
                    hydrated = hydrate(disk.entries());
                } catch (Exception e) {
                    PackAiMod.LOGGER.debug("ItemIndex hydrate failed: {}", e.toString());
                }
            }
            final List<MemEntry> mem = hydrated;
            Minecraft client = Minecraft.getInstance();
            if (client == null) {
                building.set(false);
                return;
            }
            client.execute(() -> applyEnsureOnClient(client, gameDir, want, disk, mem, cacheFile));
        });
    }

    private void applyEnsureOnClient(
            Minecraft mc,
            Path gameDir,
            ItemIndexCache.Meta want,
            ItemIndexCache.Document disk,
            List<MemEntry> hydrated,
            Path cacheFile) {
        try {
            if (hydrated != null && !hydrated.isEmpty()) {
                entries = List.copyOf(hydrated);
                loadedMeta = disk.meta();
                ready = true;
                installNbtVariantProbe();
                PackAiMod.LOGGER.info(
                        "ItemIndex loaded from disk entries={} key={}",
                        hydrated.size(),
                        ItemIndexCache.cacheKey(want));
                building.set(false);
                return;
            }
            startChunkedBuild(gameDir, want, cacheFile);
        } catch (Exception e) {
            PackAiMod.LOGGER.debug("ItemIndex client build failed: {}", e.toString());
            building.set(false);
        }
    }

    private void startChunkedBuild(Path gameDir, ItemIndexCache.Meta want, Path cacheFile) {
        buildGameDir = gameDir;
        buildWant = want;
        buildCacheFile = cacheFile;
        buildByDedupe = new LinkedHashMap<>();
        buildJeiPhaseDone = false;
        buildJeiIter = null;
        buildRegIter = Registry.ITEM.entrySet().iterator();
        if (ModList.get().isLoaded("jei")) {
            try {
                Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
                if (opt.isPresent()) {
                    Collection<ItemStack> all =
                            opt.get().getIngredientManager().getAllIngredients(VanillaTypes.ITEM_STACK);
                    buildJeiIter = all.iterator();
                }
            } catch (NoClassDefFoundError | Exception e) {
                PackAiMod.LOGGER.debug("ItemIndex JEI ingest init failed: {}", e.toString());
            }
        }
        if (buildJeiIter == null) {
            buildJeiPhaseDone = true;
        }
        mcContinueBuild();
    }

    private void mcContinueBuild() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(this::continueBuildTick);
        } else {
            building.set(false);
        }
    }

    private void continueBuildTick() {
        if (buildByDedupe == null) {
            building.set(false);
            return;
        }
        long deadline = System.nanoTime() + BUILD_TICK_BUDGET_NS;
        int processed = 0;
        try {
            outer:
            while (System.nanoTime() < deadline) {
                if (!buildJeiPhaseDone && buildJeiIter != null) {
                    while (buildJeiIter.hasNext()) {
                        if (buildByDedupe.size() >= ItemIndexCache.MAX_ENTRIES) {
                            finishChunkedBuild();
                            return;
                        }
                        ingest(buildByDedupe, buildJeiIter.next());
                        processed++;
                        if (processed >= BUILD_CHUNK_ITEMS || System.nanoTime() >= deadline) {
                            break outer;
                        }
                    }
                    buildJeiPhaseDone = true;
                    continue;
                }
                if (buildRegIter != null) {
                    while (buildRegIter.hasNext()) {
                        if (buildByDedupe.size() >= ItemIndexCache.MAX_ENTRIES) {
                            finishChunkedBuild();
                            return;
                        }
                        var entry = buildRegIter.next();
                        ingest(buildByDedupe, new ItemStack(entry.getValue()));
                        processed++;
                        if (processed >= BUILD_CHUNK_ITEMS || System.nanoTime() >= deadline) {
                            break outer;
                        }
                    }
                    buildRegIter = null;
                    finishChunkedBuild();
                    return;
                }
                finishChunkedBuild();
                return;
            }
            if (buildByDedupe != null) {
                mcContinueBuild();
            }
        } catch (Exception e) {
            PackAiMod.LOGGER.debug("ItemIndex chunked build failed: {}", e.toString());
            building.set(false);
            buildByDedupe = null;
        }
    }

    private void finishChunkedBuild() {
        Map<String, MemEntry> byDedupe = buildByDedupe;
        buildByDedupe = null;
        buildJeiIter = null;
        buildRegIter = null;
        Path gameDir = buildGameDir;
        ItemIndexCache.Meta want = buildWant;
        Path file = buildCacheFile;
        try {
            if (byDedupe == null || byDedupe.isEmpty()) {
                PackAiMod.LOGGER.info("ItemIndex build empty — Ask search stays on live scan");
                return;
            }
            List<MemEntry> built = new ArrayList<>(byDedupe.values());
            boolean usedJei = ModList.get().isLoaded("jei") && PackAiJeiPlugin.runtime().isPresent();
            ItemIndexCache.Meta builtMeta =
                    new ItemIndexCache.Meta(want.mc(), want.loader(), want.lang(), want.modFp(), usedJei);
            entries = List.copyOf(built);
            loadedMeta = builtMeta;
            ready = true;
            installNbtVariantProbe();
            List<ItemIndexCache.Entry> rows = new ArrayList<>(built.size());
            for (MemEntry m : built) {
                rows.add(new ItemIndexCache.Entry(
                        m.id(), m.label(), snbtOf(m.stack()), m.schem(), m.dedupe()));
            }
            ItemIndexCache.Document doc = new ItemIndexCache.Document(builtMeta, rows);
            Thread saver = new Thread(() -> {
                boolean saved = ItemIndexCache.save(file, doc);
                PackAiMod.LOGGER.info(
                        "ItemIndex built entries={} jei={} saved={} key={}",
                        built.size(),
                        usedJei,
                        saved,
                        ItemIndexCache.cacheKey(builtMeta));
            }, "packai-item-index-save");
            saver.setDaemon(true);
            saver.start();
        } catch (Exception e) {
            PackAiMod.LOGGER.debug("ItemIndex finish build failed: {}", e.toString());
        } finally {
            building.set(false);
        }
    }

    private void installNbtVariantProbe() {
        ItemResolver.setNbtVariantIdProbe(this::registryIdHasNbtVariants);
    }

    private boolean registryIdHasNbtVariants(String registryId) {
        if (registryId == null || registryId.isBlank() || entries.isEmpty()) {
            return false;
        }
        int hits = 0;
        for (MemEntry m : entries) {
            if (m.id().equalsIgnoreCase(registryId)) {
                hits++;
                if (hits > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void waitForJeiQuietly() {
        if (!ModList.get().isLoaded("jei")) {
            return;
        }
        long deadline = System.currentTimeMillis() + JEI_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (PackAiJeiPlugin.runtime().isPresent()) {
                return;
            }
            try {
                Thread.sleep(JEI_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void ingest(Map<String, MemEntry> byDedupe, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
        if (key == null) {
            return;
        }
        String id = key.toString();
        if (JeiUniversalSpam.isSpamItemId(id)) {
            return;
        }
        String label = Plainify.stripMcFormat(stack.getHoverName().getString());
        List<String> schem = ItemVariantKeys.schematicTokens(stack);
        String dedupe = AskService.selectionKey(AskService.fromStack(stack));
        if (dedupe.isEmpty()) {
            dedupe = id.toLowerCase(Locale.ROOT) + "|" + normLabel(label);
        }
        if (byDedupe.containsKey(dedupe)) {
            return;
        }
        byDedupe.put(dedupe, new MemEntry(id, label, schem, dedupe, stack.copy()));
    }

    private static List<MemEntry> hydrate(List<ItemIndexCache.Entry> rows) {
        List<MemEntry> out = new ArrayList<>(rows.size());
        for (ItemIndexCache.Entry e : rows) {
            String embed = e.id();
            if (e.nbt() != null && !e.nbt().isBlank()) {
                String body = e.nbt().trim();
                if (!body.startsWith("{")) {
                    body = "{" + body + "}";
                }
                embed = e.id() + body;
            }
            ItemStack stack = ItemResolver.stackFromId(embed);
            if (stack.isEmpty()) {
                stack = ItemResolver.stackFromId(e.id());
            }
            if (stack.isEmpty()) {
                continue;
            }
            out.add(new MemEntry(e.id(), e.label(), e.schem(), e.dedupe(), stack));
        }
        return out;
    }

    private ItemIndexCache.Meta currentMetaCached() {
        long now = System.currentTimeMillis();
        ItemIndexCache.Meta m = cachedMeta;
        if (m != null && now - cachedMetaAtMs < META_TTL_MS) {
            return m;
        }
        m = currentMeta();
        cachedMeta = m;
        cachedMetaAtMs = now;
        return m;
    }

    private static ItemIndexCache.Meta currentMeta() {
        String mc = SharedConstants.getCurrentVersion().getName();
        String lang = ReplyLang.current();
        if (lang == null || lang.isBlank()) {
            lang = "en_us";
        }
        List<String> lines = new ArrayList<>();
        for (IModInfo info : ModList.get().getMods()) {
            lines.add(info.getModId() + "@" + info.getVersion());
        }
        boolean jei = ModList.get().isLoaded("jei") && PackAiJeiPlugin.runtime().isPresent();
        return new ItemIndexCache.Meta(mc, "forge", lang, ItemIndexCache.fingerprintMods(lines), jei);
    }

    private static String snbtOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        CompoundTag tag = stack.getTag();
        return tag == null || tag.isEmpty() ? "" : tag.toString();
    }

    private static String normLabel(String s) {
        if (s == null) {
            return "";
        }
        return Plainify.stripMcFormat(s).trim().replace('「', '"').replace('」', '"').toLowerCase(Locale.ROOT);
    }

    private static boolean admitOverWorst(Map<String, Scored> best, int score, String label) {
        String worstKey = null;
        Scored worst = null;
        for (var e : best.entrySet()) {
            Scored s = e.getValue();
            if (worst == null
                    || s.score() > worst.score()
                    || (s.score() == worst.score() && s.label().compareTo(worst.label()) > 0)) {
                worst = s;
                worstKey = e.getKey();
            }
        }
        if (worst == null) {
            return true;
        }
        if (score > worst.score()) {
            return false;
        }
        if (score == worst.score() && label.compareTo(worst.label()) >= 0) {
            return false;
        }
        best.remove(worstKey);
        return true;
    }

    private record Scored(ItemStack stack, String id, String label, int score) {}
}
