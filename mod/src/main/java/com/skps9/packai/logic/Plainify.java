package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Best-effort recipe → plain Chinese (no raw code as main answer). */
public final class Plainify {
    private static final Pattern ITEM = Pattern.compile("['\"]([a-z0-9_.:/#-]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHAPED = Pattern.compile(
            "event\\.shaped\\(\\s*([^,\\n]+)\\s*,\\s*\\[([\\s\\S]*?)\\]\\s*,\\s*\\{([\\s\\S]*?)\\}\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SHAPELESS = Pattern.compile(
            "event\\.shapeless\\(\\s*([^,\\n]+)\\s*,\\s*\\[([\\s\\S]*?)\\]\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REMOVE = Pattern.compile(
            "event\\.remove\\(\\s*\\{([^}]*)\\}\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    private Plainify() {}

    public static String plainify(List<String> snippets, List<String> sources) {
        List<String> parts = new ArrayList<>();
        for (String snip : snippets) {
            String body = snip;
            if (body.startsWith("// file:") && body.contains("\n")) {
                body = body.substring(body.indexOf('\n') + 1);
            }
            String p = one(body);
            if (p != null) {
                parts.add(p);
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        String src = sources == null || sources.isEmpty() ? "(未知)" : String.join("、", sources);
        return String.join("\n\n", parts)
                + "\n\n【來源】" + src
                + "\n【注意】此為整合包本地設定，可能與通用 wiki 不同。";
    }

    public static String friendlyOffline(List<String> sources, String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("找到相關整合包設定，但需要 API key 或本機 Ollama 才能完整 AI 說明。\n");
        sb.append("也可先看任務書／下列檔案：\n");
        if (sources == null || sources.isEmpty()) {
            sb.append("- （沒有找到檔案）\n");
        } else {
            int n = Math.min(8, sources.size());
            for (int i = 0; i < n; i++) {
                sb.append("- ").append(sources.get(i)).append('\n');
            }
        }
        sb.append("問題：").append(question);
        return sb.toString();
    }

    private static String one(String text) {
        Matcher m = SHAPED.matcher(text);
        if (m.find()) {
            String out = clean(m.group(1));
            Map<String, String> keys = keyMap(m.group(3));
            Map<String, Integer> mats = matsFromPattern(m.group(2), keys);
            return "【作法】用工作台（有序）合成 " + out
                    + "\n【材料】" + fmt(mats)
                    + "\n【步驟】1. 打開工作台 2. 依配方擺放 3. 取出 " + out;
        }
        m = SHAPELESS.matcher(text);
        if (m.find()) {
            String out = clean(m.group(1));
            Map<String, Integer> mats = new LinkedHashMap<>();
            Matcher im = ITEM.matcher(m.group(2));
            while (im.find()) {
                String id = im.group(1);
                mats.merge(id, 1, Integer::sum);
            }
            return "【作法】用工作台（無序）合成 " + out
                    + "\n【材料】" + fmt(mats)
                    + "\n【步驟】1. 打開工作台 2. 放入材料 3. 取出 " + out;
        }
        m = REMOVE.matcher(text);
        if (m.find()) {
            return "【作法】整合包已移除／封鎖某些配方（" + m.group(1).trim() + "）\n【步驟】請改用任務書或其他方式取得";
        }
        return null;
    }

    private static Map<String, String> keyMap(String blob) {
        Map<String, String> map = new LinkedHashMap<>();
        Matcher m = Pattern.compile("([A-Za-z0-9])\\s*:\\s*(?:Item\\.of\\()?['\"]([^'\"]+)['\"]")
                .matcher(blob);
        while (m.find()) {
            map.put(m.group(1), m.group(2));
        }
        return map;
    }

    private static Map<String, Integer> matsFromPattern(String pattern, Map<String, String> keys) {
        Map<String, Integer> mats = new LinkedHashMap<>();
        for (String line : pattern.split(",")) {
            String s = line.trim().replace("'", "").replace("\"", "");
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == ' ' || c == '.' || c == '_') {
                    continue;
                }
                String name = keys.getOrDefault(String.valueOf(c), String.valueOf(c));
                mats.merge(name, 1, Integer::sum);
            }
        }
        return mats;
    }

    private static String clean(String raw) {
        Matcher m = ITEM.matcher(raw);
        if (m.find()) {
            return m.group(1);
        }
        return raw.trim().replace("'", "").replace("\"", "");
    }

    private static String fmt(Map<String, Integer> mats) {
        if (mats.isEmpty()) {
            return "（未解析到材料）";
        }
        List<String> bits = new ArrayList<>();
        mats.forEach((k, v) -> bits.add(k + " ×" + v));
        return String.join("、", bits);
    }
}
