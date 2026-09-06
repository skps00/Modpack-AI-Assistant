package com.skps9.packai.logic;

import java.util.List;
import java.util.Locale;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;
import com.skps9.packai.client.knowledge.ItemSearch;

/**
 * Fuzzy item id lookup for AI mode (index-first + live fallback).
 * Does <b>not</b> apply {@code AskNameResolve.labelMatches} hard gate.
 */
public final class ItemSearchAskTool implements AskTool {
    private static final int LIMIT = 5;

    @Override
    public String name() {
        return "item_search";
    }

    @Override
    public String description() {
        return "Find pack item ids by display name or partial id. "
                + "query=player wording (e.g. 附魔金苹果). Returns top hits; pick an id then call "
                + "render_recipe_cards. Does not attach cards.";
    }

    @Override
    public String argsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},"
                + "\"item\":{\"type\":\"string\"},\"variant_keys\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"dump_level\":{\"type\":\"string\"},\"card_index\":{\"type\":\"string\"}},"
                + "\"required\":[\"query\"],\"additionalProperties\":false}";
    }

    @Override
    public String run(AskToolArgs args) {
        String query = resolveQuery(args);
        if (query.isBlank()) {
            return "找不到，試改名/英文 id";
        }
        try {
            List<ItemSearch.Hit> hits = ItemSearch.search(query, LIMIT);
            if (hits == null || hits.isEmpty()) {
                return "找不到，試改名/英文 id";
            }
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (ItemSearch.Hit hit : hits) {
                if (hit == null || hit.id() == null || hit.id().isBlank()) {
                    continue;
                }
                n++;
                String label = hit.label() == null || hit.label().isBlank() ? hit.id() : hit.label();
                String mod = modOf(hit.id());
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append('[').append(n).append("] ").append(label).append(' ')
                        .append(hit.id()).append(" (").append(mod).append(')');
            }
            return sb.length() == 0 ? "找不到，試改名/英文 id" : sb.toString();
        } catch (Throwable t) {
            return "找不到，試改名/英文 id";
        }
    }

    private static String resolveQuery(AskToolArgs args) {
        if (args == null) {
            return "";
        }
        String fromJson = jsonString(args.argumentsJson, "query");
        if (!fromJson.isBlank()) {
            return fromJson.trim();
        }
        if (args.dumpLevel != null && !args.dumpLevel.isBlank()
                && !AskToolLoop.isDumpLevel(args.dumpLevel)) {
            return args.dumpLevel.trim();
        }
        if (args.itemId != null && !args.itemId.isBlank()) {
            return args.itemId.trim();
        }
        return args.question == null ? "" : args.question.trim();
    }

    private static String jsonString(String argsJson, String key) {
        if (argsJson == null || argsJson.isBlank() || key == null) {
            return "";
        }
        try {
            JsonObject o = JsonParser.parseString(argsJson).getAsJsonObject();
            if (o != null && o.has(key) && o.get(key).isJsonPrimitive()) {
                return o.get(key).getAsString();
            }
        } catch (Exception ignored) {
            // malformed — fall through
        }
        return "";
    }

    private static String modOf(String id) {
        if (id == null) {
            return "?";
        }
        int colon = id.indexOf(':');
        if (colon <= 0) {
            return id.toLowerCase(Locale.ROOT);
        }
        return id.substring(0, colon).toLowerCase(Locale.ROOT);
    }
}
