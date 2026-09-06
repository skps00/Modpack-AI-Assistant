package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skps9.packai.PackAiMod;
import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;
import com.skps9.packai.client.jei.AskJeiClient;

import net.minecraft.world.item.ItemStack;

/**
 * Collect JEI cards for an item+role and schedule them on the AI card strip (emission channel).
 * role: output | upgrade | uses (uses → INPUT / purpose-as-material).
 */
public final class RenderRecipeCardsAskTool implements AskTool {
    private static final int PER_CALL_CAP = 6;
    /** Scan wider than emit cap so filterRole can still see non-craft categories. */
    private static final int SCAN_CAP = 24;

    @Override
    public String name() {
        return "render_recipe_cards";
    }

    @Override
    public String description() {
        return "Show JEI recipe cards under the answer (card strip). "
                + "item_id=mod:id (or item=); role=output|upgrade|uses; machine=optional category title "
                + "substring (e.g. 奥术砧). Prefer item_search first when the id is unknown. "
                + "Do NOT write [[recipe_card…]] markers in the answer text.";
    }

    @Override
    public String argsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"item_id\":{\"type\":\"string\"},\"item\":{\"type\":\"string\"},"
                + "\"role\":{\"type\":\"string\"},\"machine\":{\"type\":\"string\"},"
                + "\"variant_keys\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"dump_level\":{\"type\":\"string\"},\"query\":{\"type\":\"string\"},"
                + "\"card_index\":{\"type\":\"string\"}},"
                + "\"required\":[\"role\"],\"additionalProperties\":false}";
    }

    @Override
    public String run(AskToolArgs args) {
        AskToolEnv env = AskToolEnv.current();
        String itemId = resolveItemId(args, env);
        String role = resolveRole(args);
        String machine = resolveMachine(args);
        if (role.isBlank() || !isKnownRole(role)) {
            return "role must be output, upgrade, or uses";
        }
        if (itemId.isBlank()) {
            return missEmpty(itemId, role, 0, 0, 0);
        }
        ItemStack stack = ItemResolver.stackFromId(itemId);
        // Explicit item_id must not silently fall back to held (typed apple while holding wuren).
        if (stack.isEmpty()) {
            return missEmpty(itemId, role, 0, 0, 0);
        }
        long deadline = args == null ? System.currentTimeMillis() + 5_000L : args.deadlineMs;
        int maxOut = "uses".equals(role) ? 0 : SCAN_CAP;
        int maxIn = "uses".equals(role) ? SCAN_CAP : ("upgrade".equals(role) ? SCAN_CAP : 0);
        if ("upgrade".equals(role)) {
            // upgrade cards live in maintenance pass — need both sides probed
            maxOut = SCAN_CAP;
            maxIn = SCAN_CAP;
        }
        List<RecipeCard> pool;
        try {
            pool = AskJeiClient.recipeCardsForItem(stack, maxOut, maxIn, deadline);
        } catch (Throwable t) {
            PackAiMod.LOGGER.info(
                    "Pack AI renderCards item={} role={} scannedCats=? foundOutput=? afterFilter=0 err={}",
                    itemId, role, t.toString());
            return missEmpty(itemId, role, 0, 0, 0);
        }
        int scanned = pool == null ? 0 : pool.size();
        int foundOutput = countRole(pool, "output");
        List<RecipeCard> matched = filterRole(pool, role, machine);
        if (matched.isEmpty()) {
            PackAiMod.LOGGER.info(
                    "Pack AI renderCards item={} role={} scannedCats={} foundOutput={} afterFilter=0",
                    itemId, role, scanned, foundOutput);
            return missEmpty(itemId, role, scanned, foundOutput, 0);
        }
        int total = matched.size();
        PackAiMod.LOGGER.info(
                "Pack AI renderCards item={} role={} scannedCats={} foundOutput={} afterFilter={}",
                itemId, role, scanned, foundOutput, total);
        if (matched.size() > PER_CALL_CAP) {
            matched = List.copyOf(matched.subList(0, PER_CALL_CAP));
        }
        if (env == null) {
            return digest(matched, role, total);
        }
        int room = AskLoopState.MAX_CARD_EMISSIONS - env.pendingEmissions.size();
        if (room <= 0) {
            return "累計卡數已達上限 " + AskLoopState.MAX_CARD_EMISSIONS + "，唔再出卡";
        }
        List<RecipeCard> emitted = new ArrayList<>();
        for (RecipeCard card : matched) {
            if (emitted.size() >= room) {
                break;
            }
            CardEmission em = new CardEmission(itemId, role, card);
            if (env.offerEmission(em)) {
                emitted.add(card);
            }
        }
        if (emitted.isEmpty()) {
            if (env.pendingEmissions.size() >= AskLoopState.MAX_CARD_EMISSIONS) {
                return "累計卡數已達上限 " + AskLoopState.MAX_CARD_EMISSIONS + "，唔再出卡";
            }
            return missEmpty(itemId, role, scanned, foundOutput, 0);
        }
        String dig = digest(emitted, role, total);
        if (total > PER_CALL_CAP) {
            dig = dig + "\n有 " + total + " 張，已出頭 " + PER_CALL_CAP + " 張，可加 machine=… 收窄";
        }
        return dig;
    }

    private static String missEmpty(String itemId, String role, int scanned, int foundOut, int after) {
        return "該 item 冇 role=" + role + " 嘅 JEI 卡（已掃完；勿用相同 args 重試；可改 role 或 machine=）";
    }

    private static int countRole(List<RecipeCard> pool, String role) {
        if (pool == null || pool.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (RecipeCard c : pool) {
            if (c != null && !c.isEmpty() && roleMatches(c, role)) {
                n++;
            }
        }
        return n;
    }

    static List<RecipeCard> filterRole(List<RecipeCard> pool, String role, String machine) {
        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        String m = machine == null ? "" : machine.trim().toLowerCase(Locale.ROOT);
        List<RecipeCard> out = new ArrayList<>();
        for (RecipeCard c : pool) {
            if (c == null || c.isEmpty()) {
                continue;
            }
            if (!roleMatches(c, role)) {
                continue;
            }
            if (!m.isEmpty()) {
                String cat = c.categoryTitle() == null ? "" : c.categoryTitle().toLowerCase(Locale.ROOT);
                if (!cat.contains(m)) {
                    continue;
                }
            }
            out.add(c);
        }
        return out;
    }

    private static boolean roleMatches(RecipeCard c, String role) {
        return switch (role) {
            case "output" -> "output".equals(c.promptRole()) || "quest".equals(c.promptRole());
            case "upgrade" -> c.isUpgrade();
            case "uses" -> c.isInputUse();
            default -> false;
        };
    }

    private static boolean isKnownRole(String role) {
        return "output".equals(role) || "upgrade".equals(role) || "uses".equals(role);
    }

    private static String digest(List<RecipeCard> cards, String role, int totalFound) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (RecipeCard c : cards) {
            i++;
            if (sb.length() > 0) {
                sb.append('\n');
            }
            String machine = c.categoryTitle() == null || c.categoryTitle().isBlank()
                    ? "?" : c.categoryTitle();
            String brief = cardBrief(c);
            sb.append("[卡").append(i).append("] 機器=").append(machine)
                    .append(" role=").append(role).append("：").append(brief);
        }
        return sb.toString();
    }

    private static String cardBrief(RecipeCard c) {
        StringBuilder b = new StringBuilder();
        if (c.inputs() != null) {
            int n = 0;
            for (ItemStack s : c.inputs()) {
                if (s == null || s.isEmpty()) {
                    continue;
                }
                if (n > 0) {
                    b.append('+');
                }
                b.append(Plainify.stripMcFormat(s.getHoverName().getString()));
                n++;
                if (n >= 4) {
                    break;
                }
            }
        }
        if (c.outputs() != null && !c.outputs().isEmpty()) {
            ItemStack o = c.outputs().get(0);
            if (o != null && !o.isEmpty()) {
                if (b.length() > 0) {
                    b.append('→');
                }
                b.append(Plainify.stripMcFormat(o.getHoverName().getString()));
            }
        }
        return b.length() == 0 ? c.promptRole() : b.toString();
    }

    private static String resolveItemId(AskToolArgs args, AskToolEnv env) {
        if (args != null) {
            String id = jsonString(args.argumentsJson, "item_id");
            if (id.isBlank()) {
                id = jsonString(args.argumentsJson, "item");
            }
            if (!id.isBlank()) {
                return id.trim();
            }
            if (args.itemId != null && !args.itemId.isBlank()) {
                return args.itemId.trim();
            }
        }
        if (env != null && env.held != null && env.held.isPresent()) {
            return env.held.id();
        }
        return "";
    }

    private static String resolveRole(AskToolArgs args) {
        if (args == null) {
            return "";
        }
        String role = jsonString(args.argumentsJson, "role");
        if (role.isBlank() && args.dumpLevel != null && !args.dumpLevel.isBlank()) {
            String d = args.dumpLevel.trim().toLowerCase(Locale.ROOT);
            // output/input overlap jei dump levels — still valid render roles
            if (isKnownRole(d)) {
                role = d;
            } else if ("input".equals(d)) {
                role = "uses";
            } else if (!AskToolLoop.isDumpLevel(args.dumpLevel)) {
                role = d;
            }
        }
        return role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
    }

    private static String resolveMachine(AskToolArgs args) {
        if (args == null) {
            return "";
        }
        String m = jsonString(args.argumentsJson, "machine");
        if (m.isBlank()) {
            m = jsonString(args.argumentsJson, "query");
        }
        return m == null ? "" : m.trim();
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
            // malformed
        }
        return "";
    }
}
