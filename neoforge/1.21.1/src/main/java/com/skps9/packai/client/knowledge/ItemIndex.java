package com.skps9.packai.client.knowledge;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * In-memory Ask item index with disk cache under {@code config/packai/item-index/}.
 * Built async after join; same fingerprint → load skip rebuild. Search falls back to live scan.
 */
public final class ItemIndex {
    public static final ItemIndex INSTANCE = new ItemIndex();

    private static final int JEI_WAIT_MS = 12_000;
    private static final int JEI_POLL_MS = 400;
    private static final int SCAN_CANDIDATE_CAP = 80;

    private final AtomicBoolean building = new AtomicBoolean(false);
    private volatile boolean ready;
    private volatile ItemIndexCache.Meta loadedMeta;
    private volatile List<MemEntry> entries = List.of();

    private record MemEntry(
            String id, String label, List<String> schem, String dedupe, ItemStack stack) {}

    private ItemIndex() {}

    public boolean isReady() {
        return ready && !entries.isEmpty();
    }

    /** Drop memory so next {@link #ensureAsync} reloads / rebuilds. Disk kept. */
    public void invalidate() {
        ready = false;
        loadedMeta = null;
        entries = List.of();
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
            } finally {
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
        ItemIndexCache.Meta want = currentMeta();
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
        boolean identityOk = !ItemIndexCache.shouldRebuild(disk.meta(), want) && !disk.entries().isEmpty();
        if (identityOk && !ItemIndexCache.shouldUpgradeForJei(disk.meta(), jeiNow)) {
            List<MemEntry> mem = hydrate(disk.entries());
            if (!mem.isEmpty()) {
                entries = List.copyOf(mem);
                loadedMeta = disk.meta();
                ready = true;
                PackAiMod.LOGGER.info(
                        "ItemIndex loaded from disk entries={} key={}",
                        mem.size(),
                        ItemIndexCache.cacheKey(want));
                return;
            }
        }
        waitForJeiQuietly();
        boolean usedJei = ModList.get().isLoaded("jei") && PackAiJeiPlugin.runtime().isPresent();
        List<MemEntry> built = buildFromGame();
        if (built.isEmpty()) {
            PackAiMod.LOGGER.info("ItemIndex build empty — Ask search stays on live scan");
            return;
        }
        ItemIndexCache.Meta builtMeta = new ItemIndexCache.Meta(
                want.mc(), want.loader(), want.lang(), want.modFp(), usedJei);
        entries = List.copyOf(built);
        loadedMeta = builtMeta;
        ready = true;
        List<ItemIndexCache.Entry> rows = new ArrayList<>(built.size());
        for (MemEntry m : built) {
            rows.add(new ItemIndexCache.Entry(
                    m.id(), m.label(), snbtOf(m.stack()), m.schem(), m.dedupe()));
        }
        boolean saved = ItemIndexCache.save(file, new ItemIndexCache.Document(builtMeta, rows));
        PackAiMod.LOGGER.info(
                "ItemIndex built entries={} jei={} saved={} key={}",
                built.size(),
                usedJei,
                saved,
                ItemIndexCache.cacheKey(builtMeta));
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

    private List<MemEntry> buildFromGame() {
        Map<String, MemEntry> byDedupe = new LinkedHashMap<>();
        if (ModList.get().isLoaded("jei")) {
            try {
                Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
                if (opt.isPresent()) {
                    Collection<ItemStack> all =
                            opt.get().getIngredientManager().getAllIngredients(VanillaTypes.ITEM_STACK);
                    for (ItemStack stack : all) {
                        ingest(byDedupe, stack);
                        if (byDedupe.size() >= ItemIndexCache.MAX_ENTRIES) {
                            break;
                        }
                    }
                }
            } catch (NoClassDefFoundError | Exception e) {
                PackAiMod.LOGGER.debug("ItemIndex JEI ingest failed: {}", e.toString());
            }
        }
        if (byDedupe.size() < ItemIndexCache.MAX_ENTRIES) {
            for (var entry : BuiltInRegistries.ITEM.entrySet()) {
                ingest(byDedupe, new ItemStack(entry.getValue()));
                if (byDedupe.size() >= ItemIndexCache.MAX_ENTRIES) {
                    break;
                }
            }
        }
        return new ArrayList<>(byDedupe.values());
    }

    private static void ingest(Map<String, MemEntry> byDedupe, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
        return new ItemIndexCache.Meta(mc, "neoforge", lang, ItemIndexCache.fingerprintMods(lines), jei);
    }

    private static String snbtOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().toString();
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
