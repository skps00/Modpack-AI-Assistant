package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Pure core: drop empty modular-tool frame craft cards (focus output == card output,
 * not input-use / not trailing optional). Shared by emission gate and display suppress.
 * No Minecraft types — headless harnessable.
 */
public final class ModularFrameCards {
    private ModularFrameCards() {}

    /**
     * @param dropId focus registry id to suppress as frame craft; blank/null → never drop
     * @param primaryOutputId card primary output id (may be blank)
     * @param inputUse {@link RecipeCard#isInputUse()}
     * @param trailingOptional {@link RecipeCard#isTrailingOptional()}
     * @return true when this card must not enter model digest / strip
     */
    public static boolean shouldDropFrameCard(
            String dropId,
            String primaryOutputId,
            boolean inputUse,
            boolean trailingOptional
    ) {
        if (dropId == null || dropId.isBlank()) {
            return false;
        }
        if (inputUse || trailingOptional) {
            return false;
        }
        if (primaryOutputId == null || primaryOutputId.isBlank()) {
            return false;
        }
        return dropId.equalsIgnoreCase(primaryOutputId);
    }

    /** 命中＝同一張框架 output 卡，而且 input id 集合包含 recipe 材料（大小寫不敏感）。 */
    public static boolean isStandardKeepCard(
            String primaryOutputId,
            Collection<String> cardInputIds,
            String keepOutputId,
            Collection<String> keepInputIds
    ) {
        if (keepOutputId == null || keepOutputId.isBlank()) {
            return false;
        }
        if (primaryOutputId == null || !primaryOutputId.equalsIgnoreCase(keepOutputId)) {
            return false;
        }
        if (keepInputIds == null || keepInputIds.isEmpty()) {
            return true;
        }
        for (String need : keepInputIds) {
            if (need == null || need.isBlank()) {
                continue;
            }
            if (!containsId(cardInputIds, need)) {
                return false;
            }
        }
        return true;
    }

    /** 過濾結果（純型別，唔准含 MC class）。 */
    public record KeepResult<T>(List<T> kept, int dropped, boolean fallback) {}

    /**
     * 只保留「對應 recipe 嗰張」框架卡。守衛（缺一不可）：
     * ① keepOutputId 空白／null → 即刻原樣回傳（dropped=0, fallback=false）
     * ② 只可以過濾 outIdOf(c) 大小寫不敏感 == keepOutputId 嘅卡（其他卡一律保留）
     * ③ mustKeep.test(c) 為真（caller 傳 input-use／trailing-optional）→ 永遠保留
     * ④ 池內冇任何框架 output 卡 → 原樣回傳（dropped=0）
     * 命中規則：outIdOf 命中 且 inIdsOf(c) 包含全部 keepInputIds → 精確命中；
     * 否則 fallback＝取第一張 outIdOf 命中嘅卡（fallback=true）。
     */
    public static <T> KeepResult<T> keepOnlyStandardRecipeCard(
            List<T> matched,
            Function<T, String> outIdOf,
            Function<T, List<String>> inIdsOf,
            Predicate<T> mustKeep,
            String keepOutputId,
            Collection<String> keepInputIds
    ) {
        List<T> src = matched == null ? List.of() : matched;
        if (keepOutputId == null || keepOutputId.isBlank() || outIdOf == null) {
            return new KeepResult<>(copyOf(src), 0, false);
        }
        int firstFrame = -1;
        int exact = -1;
        for (int i = 0; i < src.size(); i++) {
            T c = src.get(i);
            if (c == null) {
                continue;
            }
            String out = outIdOf.apply(c);
            if (out == null || !out.equalsIgnoreCase(keepOutputId)) {
                continue;
            }
            if (firstFrame < 0) {
                firstFrame = i;
            }
            if (exact < 0) {
                List<String> ins = inIdsOf == null ? List.of() : inIdsOf.apply(c);
                if (isStandardKeepCard(out, ins, keepOutputId, keepInputIds)) {
                    exact = i;
                }
            }
        }
        if (firstFrame < 0) {
            return new KeepResult<>(copyOf(src), 0, false);
        }
        int chosen = exact >= 0 ? exact : firstFrame;
        boolean fallback = exact < 0;
        ArrayList<T> kept = new ArrayList<>();
        int dropped = 0;
        for (int i = 0; i < src.size(); i++) {
            T c = src.get(i);
            if (c == null) {
                kept.add(null);
                continue;
            }
            String out = outIdOf.apply(c);
            boolean frame = out != null && out.equalsIgnoreCase(keepOutputId);
            boolean pin = mustKeep != null && mustKeep.test(c);
            if (frame && !pin && i != chosen) {
                dropped++;
                continue;
            }
            kept.add(c);
        }
        return new KeepResult<>(copyOf(kept), dropped, fallback);
    }

    /** Id membership. Not {@link String#contains} — that would match id prefixes. */
    private static boolean containsId(Collection<String> ids, String want) {
        if (ids == null || want == null) {
            return false;
        }
        for (String id : ids) {
            if (id != null && id.equalsIgnoreCase(want)) {
                return true;
            }
        }
        return false;
    }

    private static <T> List<T> copyOf(List<T> src) {
        ArrayList<T> out = new ArrayList<>(src.size());
        out.addAll(src);
        return Collections.unmodifiableList(out);
    }
}
