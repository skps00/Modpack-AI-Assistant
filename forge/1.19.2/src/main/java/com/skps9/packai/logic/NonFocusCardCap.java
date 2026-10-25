package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.skps9.packai.PackAiMod;

/**
 * Per-item card budget when {@code render_recipe_cards} / catalog targets a non-ask-focus id.
 * Focus item keeps {@link com.skps9.packai.config.PackAiConfig} / tool {@code PER_CALL_CAP}.
 */
public final class NonFocusCardCap {
    public static final int MAX_OUTPUT = 2;
    public static final int MAX_USES = 1;
    public static final int MAX_TOTAL = 2;

    /** Harness negative control in {@link NonFocusCardCapCheck}. */
    static volatile boolean enabled = true;

    private NonFocusCardCap() {}

    public static boolean applies(String focusItemId, String itemId) {
        return enabled
                && itemId != null
                && !itemId.isBlank()
                && !sameItem(focusItemId, itemId);
    }

    static boolean sameItem(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String x = a.trim().toLowerCase(Locale.ROOT);
        String y = b.trim().toLowerCase(Locale.ROOT);
        return !x.isEmpty() && x.equals(y);
    }

    /** Mixed OUTPUT + INPUT list from {@code collectAskRecipeCards} for one extra item. */
    public static List<RecipeCard> capItemCards(String focusItemId, String itemId, List<RecipeCard> cards) {
        if (cards == null || cards.isEmpty() || !applies(focusItemId, itemId)) {
            return cards == null ? List.of() : List.copyOf(cards);
        }
        List<RecipeCard> outputs = new ArrayList<>();
        List<RecipeCard> uses = new ArrayList<>();
        for (RecipeCard c : cards) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            if (c.isInputUse()) {
                uses.add(c);
            } else {
                outputs.add(c);
            }
        }
        CapSlice slice = applyBudget(outputs, uses, MAX_OUTPUT, MAX_USES, MAX_TOTAL);
        log(itemId, "mixed", slice.kept.size(), slice.dropped);
        return List.copyOf(slice.kept);
    }

    /** Single {@code render_recipe_cards} role, respecting prior emissions for the same item id. */
    public static List<RecipeCard> capToolRole(
            String focusItemId,
            String itemId,
            String role,
            List<RecipeCard> matched,
            int priorOutput,
            int priorUses
    ) {
        if (matched == null || matched.isEmpty() || !applies(focusItemId, itemId)) {
            return matched == null ? List.of() : List.copyOf(matched);
        }
        int room = MAX_TOTAL - priorOutput - priorUses;
        if (room <= 0) {
            log(itemId, role, 0, matched.size());
            return List.of();
        }
        int roleCap = roleCap(role, room, priorOutput, priorUses);
        int keep = Math.min(matched.size(), roleCap);
        List<RecipeCard> kept = List.copyOf(matched.subList(0, keep));
        log(itemId, role, keep, matched.size() - keep);
        return kept;
    }

    private static int roleCap(String role, int room, int priorOutput, int priorUses) {
        String r = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        if ("uses".equals(r)) {
            return Math.min(room, Math.max(0, MAX_USES - priorUses));
        }
        if ("output".equals(r) || "upgrade".equals(r)) {
            return Math.min(room, Math.max(0, MAX_OUTPUT - priorOutput));
        }
        return room;
    }

    static CapSlice applyBudget(
            List<RecipeCard> outputs,
            List<RecipeCard> uses,
            int maxOut,
            int maxUse,
            int maxTotal
    ) {
        int outN = Math.min(outputs.size(), maxOut);
        int useN = Math.min(uses.size(), maxUse);
        while (outN + useN > maxTotal) {
            if (useN > 0) {
                useN--;
            } else if (outN > 0) {
                outN--;
            } else {
                break;
            }
        }
        List<RecipeCard> kept = new ArrayList<>(outN + useN);
        for (int i = 0; i < outN; i++) {
            kept.add(outputs.get(i));
        }
        for (int i = 0; i < useN; i++) {
            kept.add(uses.get(i));
        }
        int dropped = (outputs.size() - outN) + (uses.size() - useN);
        return new CapSlice(kept, dropped);
    }

    /** [outputCount, usesCount] for {@code itemId} already queued this ask. */
    public static int[] countEmissionsForItem(AskToolEnv env, String itemId) {
        if (env == null || itemId == null || itemId.isBlank()) {
            return new int[]{0, 0};
        }
        int out = 0;
        int uses = 0;
        if (env.loop != null) {
            int[] t = tallyList(env.loop.cardEmissions(), itemId);
            out += t[0];
            uses += t[1];
        }
        int[] pending = tallyList(env.pendingEmissions, itemId);
        out += pending[0];
        uses += pending[1];
        return new int[]{out, uses};
    }

    private static int[] tallyList(List<CardEmission> list, String itemId) {
        int out = 0;
        int uses = 0;
        if (list == null || list.isEmpty()) {
            return new int[]{0, 0};
        }
        String want = itemId.trim().toLowerCase(Locale.ROOT);
        for (CardEmission em : list) {
            if (em == null || !em.itemId().equalsIgnoreCase(want)) {
                continue;
            }
            if ("uses".equals(em.role())) {
                uses++;
            } else {
                out++;
            }
        }
        return new int[]{out, uses};
    }

    private static void log(String itemId, String role, int kept, int dropped) {
        if (dropped <= 0) {
            return;
        }
        PackAiMod.LOGGER.info(
                "Pack AI cards nonfocus item={} role={} kept={} dropped={}",
                itemId,
                role == null ? "" : role,
                kept,
                dropped);
    }

    record CapSlice(List<RecipeCard> kept, int dropped) {}
}
