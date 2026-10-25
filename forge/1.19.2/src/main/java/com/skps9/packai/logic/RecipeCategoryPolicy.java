package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.client.jei.JeiCategoryCatalog;

import mezz.jei.api.recipe.category.IRecipeCategory;

/**
 * JEI category uid policy: drop non-obtain paths (anvil/fuel) and defer mirror
 * replicator categories when a non-mirror card already exists for the same item+role.
 */
public final class RecipeCategoryPolicy {
    private static final Set<String> NON_OBTAIN_EXACT = Set.of(
            "minecraft:anvil",
            "minecraft:fuel",
            "minecraft:compostable",
            "thermal:gourmand_fuel",
            "thermal:disenchantment_fuel",
            "thermal:stirling_fuel",
            "ironfurnaces:generator_regular",
            "ironfurnaces:generator_smoking");

    private static final String INFO_ONLY_EXACT = "jei:information";

    private static final Set<String> MIRROR_EXACT = Set.of(
            "create:automatic_shaped",
            "create:automatic_shapeless",
            "create:automatic_packing",
            "create:automatic_brewing",
            "create:block_cutting",
            "minecraft:mason_table",
            "minecraft:carpenters_table",
            "minecraft:loom_table",
            "minecraft:glassblower",
            "minecraft:alchemy_bench",
            "minecraft:botanist_workbench",
            "corail_woodcutter:woodcutting");

    private static final String MYCELIAL_PREFIX = "industrialforegoing:mycelial_";

    private static final IdentityHashMap<RecipeCard, String> harnessUid = new IdentityHashMap<>();

    private static int nonObtainSkipped;
    private static int mirrorDropped;
    private static int infoDropped;
    private static int kept;

    /**
     * When false, mirror-only buckets are cleared ({@link CategoryPolicyCheck} negative control).
     */
    static volatile boolean keepMirrorWhenOnlySource = true;

    /**
     * When false, info-only buckets are cleared ({@link CategoryPolicyCheck} negative control).
     */
    static volatile boolean keepInfoWhenOnlySource = true;

    private RecipeCategoryPolicy() {}

    public static void resetAskCounters() {
        nonObtainSkipped = 0;
        mirrorDropped = 0;
        infoDropped = 0;
        kept = 0;
    }

    public static void logAskSummary() {
        PackAiMod.LOGGER.info(
                "Pack AI cat_policy nonobtain_skipped={} mirror_dropped={} info_dropped={} kept={}",
                nonObtainSkipped,
                mirrorDropped,
                infoDropped,
                kept);
    }

    public static void noteNonObtainCategorySkipped() {
        nonObtainSkipped++;
    }

    static void harnessBindUid(RecipeCard card, String uid) {
        if (card == null || uid == null || uid.isBlank()) {
            return;
        }
        harnessUid.put(card, uid.trim().toLowerCase(Locale.ROOT));
    }

    static void harnessClearUids() {
        harnessUid.clear();
    }

    public static boolean isNonObtain(String uid) {
        if (uid == null || uid.isBlank()) {
            return false;
        }
        String u = uid.trim().toLowerCase(Locale.ROOT);
        if (NON_OBTAIN_EXACT.contains(u)) {
            return true;
        }
        return u.startsWith(MYCELIAL_PREFIX);
    }

    public static boolean isInfoOnly(String uid) {
        if (uid == null || uid.isBlank()) {
            return false;
        }
        return INFO_ONLY_EXACT.equals(uid.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isMirror(String uid) {
        if (uid == null || uid.isBlank()) {
            return false;
        }
        String u = uid.trim().toLowerCase(Locale.ROOT);
        if (MIRROR_EXACT.contains(u)) {
            return true;
        }
        return u.startsWith("minecolonies:");
    }

    public static List<RecipeCard> skipNonObtain(List<RecipeCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        List<RecipeCard> out = new ArrayList<>(cards.size());
        int dropped = 0;
        for (RecipeCard c : cards) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            if (isNonObtain(categoryUidOf(c))) {
                dropped++;
                continue;
            }
            out.add(c);
        }
        nonObtainSkipped += dropped;
        return List.copyOf(out);
    }

    /**
     * Same {@code sourceItemId + promptRole}: non-mirror cards win; mirrors fill only when
     * no non-mirror exists for that bucket. {@link AskFocusPrefetch#recipeIdKey} dedupes mirrors.
     */
    public static List<RecipeCard> dropMirrored(List<RecipeCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> bucketHasNonMirror = new LinkedHashSet<>();
        for (RecipeCard c : cards) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            if (!isMirror(categoryUidOf(c))) {
                bucketHasNonMirror.add(itemRoleKey(c));
            }
        }
        List<RecipeCard> out = new ArrayList<>();
        LinkedHashSet<String> seenRecipe = new LinkedHashSet<>();
        int dropped = 0;
        for (RecipeCard c : cards) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            String uid = categoryUidOf(c);
            boolean mirror = isMirror(uid);
            if (!mirror) {
                String rid = AskFocusPrefetch.recipeIdKey(c);
                if (!rid.isEmpty() && !seenRecipe.add(rid)) {
                    dropped++;
                    continue;
                }
                out.add(c);
                continue;
            }
            String bucket = itemRoleKey(c);
            if (bucketHasNonMirror.contains(bucket)) {
                dropped++;
                continue;
            }
            if (!keepMirrorWhenOnlySource) {
                dropped++;
                continue;
            }
            String rid = AskFocusPrefetch.recipeIdKey(c);
            if (!rid.isEmpty() && !seenRecipe.add(rid)) {
                dropped++;
                continue;
            }
            out.add(c);
        }
        mirrorDropped += dropped;
        return List.copyOf(out);
    }

    /**
     * Same {@code sourceItemId + promptRole}: non-info cards win; info pages fill only when
     * no other card exists for that bucket.
     */
    public static List<RecipeCard> dropInfoWhenOthers(List<RecipeCard> cards) {
        if (cards == null || cards.isEmpty()) {
            kept = 0;
            return List.of();
        }
        LinkedHashSet<String> bucketHasNonInfo = new LinkedHashSet<>();
        for (RecipeCard c : cards) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            if (!isInfoOnly(categoryUidOf(c))) {
                bucketHasNonInfo.add(itemRoleKey(c));
            }
        }
        List<RecipeCard> out = new ArrayList<>();
        int dropped = 0;
        for (RecipeCard c : cards) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            String uid = categoryUidOf(c);
            if (!isInfoOnly(uid)) {
                out.add(c);
                continue;
            }
            String bucket = itemRoleKey(c);
            if (bucketHasNonInfo.contains(bucket)) {
                dropped++;
                continue;
            }
            if (!keepInfoWhenOnlySource) {
                dropped++;
                continue;
            }
            out.add(c);
        }
        infoDropped += dropped;
        kept = out.size();
        return List.copyOf(out);
    }

    static String itemRoleKey(RecipeCard c) {
        String item = c.sourceItemId() == null ? "" : c.sourceItemId().trim().toLowerCase(Locale.ROOT);
        String role = c.promptRole() == null ? "" : c.promptRole().trim().toLowerCase(Locale.ROOT);
        return item + "|" + role;
    }

    static String categoryUidOf(RecipeCard c) {
        if (c == null) {
            return "";
        }
        String bound = harnessUid.get(c);
        if (bound != null && !bound.isEmpty()) {
            return bound;
        }
        Object layout = c.jeiLayout();
        if (layout == null) {
            return "";
        }
        try {
            Object cat = layout.getClass().getMethod("getRecipeCategory").invoke(layout);
            if (cat instanceof IRecipeCategory<?> ic) {
                String uid = JeiCategoryCatalog.categoryUid(ic);
                return uid == null ? "" : uid.trim().toLowerCase(Locale.ROOT);
            }
        } catch (ReflectiveOperationException ignored) {
            // headless card without JEI drawable
        }
        return "";
    }
}
