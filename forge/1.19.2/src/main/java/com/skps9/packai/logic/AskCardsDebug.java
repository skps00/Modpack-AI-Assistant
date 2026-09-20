package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Permanent per-ask card debug log helpers (plan B1: card attribution forensics).
 * Format only — callers emit via {@code Pack AI cards emitted=} / {@code Pack AI toolParts path=}.
 */
public final class AskCardsDebug {
    private AskCardsDebug() {}

    /** Line-start parts heading in model body (zh / en), not UI strip caption. */
    private static final Pattern PARTS_HEAD = Pattern.compile(
            "(?im)^[ \\t]*(?:##[ \\t]*)?(?:\\d+[.、．)）]\\s*)?(?:零件|Parts)\\s*[:：]?");

    /** UI section bucket for log {@code section=} (plan B1). */
    public static String sectionLabel(RecipeCard c) {
        if (c == null) {
            return "?";
        }
        if (c.isScrollMaterialStrip()) {
            return "零件";
        }
        if (c.isUpgrade()) {
            return "強化";
        }
        if (c.isMaintenance()) {
            return "維修";
        }
        if (c.isInputUse()) {
            return "用作材料";
        }
        // output / quest → obtain
        return "取得";
    }

    /** Compact registry ids for {@code outputs=[]}/{@code grid=[]} (cap {@code max}). */
    public static String stackIdsBrief(List<ItemStack> stacks, int max) {
        if (stacks == null || stacks.isEmpty() || max <= 0) {
            return "[]";
        }
        List<String> ids = new ArrayList<>(Math.min(stacks.size(), max));
        for (ItemStack s : stacks) {
            if (ids.size() >= max) {
                break;
            }
            String id = itemIdSafe(s);
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return "[]";
        }
        StringBuilder b = new StringBuilder(ids.size() * 24);
        b.append('[');
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                b.append(',');
            }
            b.append(ids.get(i));
        }
        if (stacks.size() > max || countNonEmpty(stacks) > ids.size()) {
            b.append(",…");
        }
        b.append(']');
        return b.toString();
    }

    /**
     * One permanent log line:
     * {@code #i cat=… out=… role=… src=… section=… ref=… outputs=… grid=…}.
     */
    public static String formatEmittedLine(int index, RecipeCard c, String ref) {
        String cat = c == null || c.categoryTitle() == null ? "" : c.categoryTitle().replace('\n', ' ');
        String out = c == null ? "" : nullToEmpty(c.primaryOutputId());
        String role = c == null ? "" : nullToEmpty(c.promptRole());
        String src = c == null ? "" : nullToEmpty(c.sourceItemId());
        String section = sectionLabel(c);
        String refTok = ref == null || ref.isBlank() ? "auto" : ref.trim();
        String outputs = c == null ? "[]" : stackIdsBrief(c.outputs(), 6);
        List<ItemStack> gridOrIn = c == null ? List.of()
                : (c.grid() != null && !c.grid().isEmpty() ? c.grid() : c.inputs());
        String grid = stackIdsBrief(gridOrIn, 9);
        return "#" + index
                + " cat=" + cat
                + " out=" + out
                + " role=" + role
                + " src=" + src
                + " section=" + section
                + " ref=" + refTok
                + " outputs=" + outputs
                + " grid=" + grid;
    }

    public static String formatSuppressedLine(RecipeCard c, String reason) {
        String cat = c == null || c.categoryTitle() == null ? "" : c.categoryTitle().replace('\n', ' ');
        String out = c == null ? "" : nullToEmpty(c.primaryOutputId());
        String role = c == null ? "" : nullToEmpty(c.promptRole());
        return "reason=" + (reason == null ? "?" : reason)
                + " cat=" + cat
                + " out=" + out
                + " role=" + role
                + " section=" + sectionLabel(c);
    }

    /** Resolve {@code [card:N]} from emissions; else {@code auto}. */
    public static String refToken(RecipeCard c, List<CardEmission> emissions) {
        if (c == null || emissions == null || emissions.isEmpty()) {
            return "auto";
        }
        for (CardEmission em : emissions) {
            if (em == null || em.card() == null) {
                continue;
            }
            if (em.card() == c && em.refId() > 0) {
                return "[card:" + em.refId() + "]";
            }
        }
        String want = dedupeKey(c);
        if (!want.isEmpty()) {
            for (CardEmission em : emissions) {
                if (em == null || em.card() == null || em.refId() <= 0) {
                    continue;
                }
                if (want.equals(dedupeKey(em.card()))) {
                    return "[card:" + em.refId() + "]";
                }
            }
        }
        return "auto";
    }

    /** Cards present in {@code before} but not in {@code after} (identity). */
    public static List<RecipeCard> missingByIdentity(List<RecipeCard> before, List<RecipeCard> after) {
        if (before == null || before.isEmpty()) {
            return List.of();
        }
        Map<RecipeCard, Boolean> keep = identitySet(after);
        List<RecipeCard> out = new ArrayList<>();
        for (RecipeCard c : before) {
            if (c != null && !keep.containsKey(c)) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * Ask-scope {@code [card:N]} refIds still backed by a card in {@code shown} (identity).
     * Drop-only — never renumbers (plan B9). Skips unset {@code refId<=0} and null cards.
     */
    public static List<Integer> visibleEmissionRefIds(List<CardEmission> emissions, List<RecipeCard> shown) {
        if (emissions == null || emissions.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = new ArrayList<>(emissions.size());
        List<RecipeCard> cards = new ArrayList<>(emissions.size());
        for (CardEmission em : emissions) {
            if (em == null || em.refId() <= 0 || em.card() == null) {
                continue;
            }
            ids.add(em.refId());
            cards.add(em.card());
        }
        return filterRefsByIdentity(ids, cards, shown);
    }

    /**
     * Identity keep for parallel refId／card lists (harness uses plain Objects; production uses RecipeCard).
     * Drop-only — never renumbers.
     */
    static <T> List<Integer> filterRefsByIdentity(List<Integer> refIds, List<T> cards, List<T> shown) {
        if (refIds == null || cards == null || refIds.isEmpty() || refIds.size() != cards.size()) {
            return List.of();
        }
        Map<T, Boolean> keep = new IdentityHashMap<>();
        if (shown != null) {
            for (T c : shown) {
                if (c != null) {
                    keep.put(c, Boolean.TRUE);
                }
            }
        }
        List<Integer> out = new ArrayList<>(refIds.size());
        for (int i = 0; i < refIds.size(); i++) {
            Integer id = refIds.get(i);
            T card = cards.get(i);
            if (id == null || id <= 0 || card == null) {
                continue;
            }
            if (keep.containsKey(card)) {
                out.add(id);
            }
        }
        return out;
    }

    private static Map<RecipeCard, Boolean> identitySet(List<RecipeCard> cards) {
        Map<RecipeCard, Boolean> keep = new IdentityHashMap<>();
        if (cards != null) {
            for (RecipeCard c : cards) {
                if (c != null) {
                    keep.put(c, Boolean.TRUE);
                }
            }
        }
        return keep;
    }

    public static boolean bodyHasPartsHeading(String body) {
        return body != null && !body.isBlank() && PARTS_HEAD.matcher(body).find();
    }

    /**
     * {@code strip} = toolPartsStrip drawn; {@code body_text} = model wrote 零件／Parts heading
     * without strip; {@code none} = neither.
     */
    public static String toolPartsPath(boolean stripDrawn, boolean bodyHasHeading) {
        if (stripDrawn) {
            return "strip";
        }
        if (bodyHasHeading) {
            return "body_text";
        }
        return "none";
    }

    private static String dedupeKey(RecipeCard c) {
        if (c == null) {
            return "";
        }
        String cat = c.categoryTitle() == null ? "" : c.categoryTitle();
        String out = c.primaryOutputId() == null ? "" : c.primaryOutputId();
        String src = c.sourceItemId() == null ? "" : c.sourceItemId();
        return (src + "|" + cat + "|" + out).toLowerCase(Locale.ROOT);
    }

    private static String itemIdSafe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        try {
            ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
            return key == null ? "" : key.toString().toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return "";
        }
    }

    private static int countNonEmpty(List<ItemStack> stacks) {
        int n = 0;
        for (ItemStack s : stacks) {
            if (s != null && !s.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
