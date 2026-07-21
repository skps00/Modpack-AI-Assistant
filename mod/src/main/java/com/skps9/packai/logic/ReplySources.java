package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/** Ensure every player-facing answer ends with a 【來源】 line. */
public final class ReplySources {
    private static final Pattern MARKER = Pattern.compile("(?m)【來源】");

    private ReplySources() {}

    public static List<String> build(
            boolean jei,
            boolean questBook,
            boolean localScripts,
            boolean acquireTables,
            boolean webSearch
    ) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (jei) {
            out.add("JEI");
        }
        if (questBook) {
            out.add("整合包任務書");
        }
        if (localScripts) {
            out.add("整合包本地配方");
        }
        if (acquireTables) {
            out.add("整合包掉落表／釣魚／交易");
        }
        if (webSearch) {
            out.add("網搜（模組資料）");
        }
        if (out.isEmpty()) {
            out.add("AI 模型（未引用 JEI／任務書／本地資料）");
        }
        return List.copyOf(out);
    }

    /** Append 【來源】 when the answer does not already include one. */
    public static String ensure(String answer, List<String> labels) {
        if (answer == null || answer.isBlank()) {
            return format(labels);
        }
        if (MARKER.matcher(answer).find()) {
            return answer;
        }
        return answer.trim() + "\n\n" + format(labels);
    }

    private static String format(List<String> labels) {
        List<String> use = labels == null || labels.isEmpty()
                ? List.of("AI 模型")
                : labels;
        return "【來源】" + String.join("、", use);
    }
}
