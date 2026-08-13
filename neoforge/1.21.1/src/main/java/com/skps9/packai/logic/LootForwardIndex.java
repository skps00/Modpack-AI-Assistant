package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * D13 / #5b — universal Gateways + loot-table forward index.
 *
 * <ul>
 *   <li>Gateways rewards: {@code loot_table} / {@code entity_loot} → table id;
 *       {@code stack} / {@code stack_list} → item ids (direct gateway reward)</li>
 *   <li>Datapack / KubeJS loot JSON (and JS {@code addJson} loot blobs) → item ids</li>
 *   <li>Stack with LootJS {@code + table:} acquire facts when present in same script text</li>
 * </ul>
 *
 * <p>Accuracy &gt; completeness: never invents drops not present in indexed text.
 */
public final class LootForwardIndex {
    private static final int MAX_ITEMS_PER_TABLE = 40;
    private static final int MAX_FACTS = 80;
    private static final int MAX_STACK_ITEMS = 40;

    /** {@code "name": "ns:path"} with nearby item-ish type (loot entry). */
    private static final Pattern LOOT_ITEM_NAME = Pattern.compile(
            "\"name\"\\s*:\\s*\"([a-z0-9_]+:[a-z0-9_./-]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LOOT_TABLE_FIELD = Pattern.compile(
            "\"loot_table\"\\s*:\\s*\"([a-z0-9_]+:[a-z0-9_./-]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GATEWAY_LOOT_TYPE = Pattern.compile(
            "\"type\"\\s*:\\s*\"(?:gateways:)?loot_table\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GATEWAY_ENTITY_LOOT_TYPE = Pattern.compile(
            "\"type\"\\s*:\\s*\"(?:gateways:)?entity_loot\"",
            Pattern.CASE_INSENSITIVE);
    /** Single ItemStack reward ({@code gateways:stack} or bare {@code stack}). */
    private static final Pattern GATEWAY_STACK_TYPE = Pattern.compile(
            "\"type\"\\s*:\\s*\"(?:gateways:)?stack\"",
            Pattern.CASE_INSENSITIVE);
    /** List of stacks ({@code gateways:stack_list} / {@code stack_list}). */
    private static final Pattern GATEWAY_STACK_LIST_TYPE = Pattern.compile(
            "\"type\"\\s*:\\s*\"(?:gateways:)?stack_list\"",
            Pattern.CASE_INSENSITIVE);
    /** ItemStack field inside stack / stacks[]. */
    private static final Pattern STACK_ITEM_FIELD = Pattern.compile(
            "\"item\"\\s*:\\s*\"([a-z0-9_]+:[a-z0-9_./-]+)\"",
            Pattern.CASE_INSENSITIVE);
    /**
     * KubeJS / scripts: {@code Item.of('gateways:gate_pearl', '{gateway:"ns:path"}')}
     * or nearby {@code gateway:"ns:path"} after gate_pearl.
     */
    private static final Pattern GATE_PEARL_GATEWAY_NBT = Pattern.compile(
            "(?i)gateways:gate_pearl.{0,120}?gateway\\s*[:=]\\s*\\\\?[\"']([a-z0-9_]+:[a-z0-9_./-]+)[\"']");
    private static final Pattern ENTITY_FIELD = Pattern.compile(
            "\"entity\"\\s*:\\s*\"([a-z0-9_]+:[a-z0-9_./-]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ADD_JSON_LOOT = Pattern.compile(
            "addJson\\(\\s*[`'\"]([a-z0-9_]+):loot_tables/([a-z0-9_./-]+)\\.json[`'\"]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ADD_JSON_GATEWAY = Pattern.compile(
            "addJson\\(\\s*[`'\"]([a-z0-9_]+):gateways/([a-z0-9_./-]+)\\.json[`'\"]",
            Pattern.CASE_INSENSITIVE);

    private LootForwardIndex() {}

    /**
     * Entity registry id → conventional entity loot table id
     * ({@code minecraft:slime} → {@code minecraft:entities/slime}).
     */
    public static String entityLootTableId(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return "";
        }
        String id = entityId.toLowerCase(Locale.ROOT).trim();
        int colon = id.indexOf(':');
        if (colon <= 0 || colon >= id.length() - 1) {
            return "";
        }
        String ns = id.substring(0, colon);
        String path = id.substring(colon + 1);
        return ns + ":entities/" + path;
    }

    /** {@code data/ns/loot_tables/foo.json} or {@code loot_table/} (1.21) → {@code ns:foo}. */
    public static String lootTableIdFromPath(String rel) {
        if (rel == null || rel.isBlank()) {
            return "";
        }
        String p = rel.replace('\\', '/').toLowerCase(Locale.ROOT);
        Matcher m = Pattern.compile(
                "(?:^|/)data/([a-z0-9_]+)/loot_tables?/(.+?)\\.json$").matcher(p);
        if (!m.find()) {
            return "";
        }
        return m.group(1) + ":" + m.group(2);
    }

    /**
     * Default placed-block loot ({@code blocks/<item_path>} drops the block item).
     * Not worth obtain/usage pins — only surface when drop is special (no pickup, different item, etc.).
     */
    public static boolean isTrivialBlockSelfLoot(String itemId, String lootTableIdOrKey) {
        if (itemId == null || itemId.isBlank() || lootTableIdOrKey == null || lootTableIdOrKey.isBlank()) {
            return false;
        }
        String item = itemId.toLowerCase(Locale.ROOT).trim();
        int colon = item.indexOf(':');
        if (colon <= 0 || colon >= item.length() - 1) {
            return false;
        }
        String itemPath = item.substring(colon + 1);
        String loot = lootTableIdOrKey.toLowerCase(Locale.ROOT).trim();
        int lootColon = loot.indexOf(':');
        String lootPath = lootColon >= 0 ? loot.substring(lootColon + 1) : loot;
        return lootPath.equals("blocks/" + itemPath);
    }

    private static void addLootEdge(LinkedHashSet<String> facts, String item, String tableId) {
        if (item == null || tableId == null || item.isBlank() || tableId.isBlank()) {
            return;
        }
        if (isTrivialBlockSelfLoot(item, tableId)) {
            return;
        }
        facts.add("loot_table:" + tableId + " -[contains]-> item:" + item);
        facts.add("item:" + item + " -[loot]-> table:" + tableId);
    }

    /** Gateway file under {@code data/ns/gateways/...json} → {@code ns:path}. */
    public static String gatewayIdFromPath(String rel) {
        if (rel == null || rel.isBlank()) {
            return "";
        }
        String p = rel.replace('\\', '/').toLowerCase(Locale.ROOT);
        Matcher m = Pattern.compile(
                "(?:^|/)data/([a-z0-9_]+)/gateways/(.+?)\\.json$").matcher(p);
        if (!m.find()) {
            return "";
        }
        return m.group(1) + ":" + m.group(2);
    }

    /** Item ids listed in a loot-table JSON (or JS object literal with pools/entries). */
    public static List<String> itemsFromLootText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        // Require loot-ish shape to avoid scanning random JSON.
        String lower = text.toLowerCase(Locale.ROOT);
        if (!lower.contains("\"pools\"") && !lower.contains("\"entries\"")
                && !lower.contains("loot_tables/") && !lower.contains("loot_table/")) {
            if (!lower.contains("\"type\"") || !lower.contains("\"name\"")) {
                return List.of();
            }
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Matcher m = LOOT_ITEM_NAME.matcher(text);
        while (m.find() && out.size() < MAX_ITEMS_PER_TABLE) {
            String id = m.group(1).toLowerCase(Locale.ROOT);
            if (id.contains(":") && !isNoise(id)) {
                out.add(id);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Parse Gateways reward refs from JSON or KubeJS that embeds reward objects.
     * Returns facts only — no invented item lists.
     */
    public static List<String> parseFacts(String rel, String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> facts = new LinkedHashSet<>();
        String gatewayId = gatewayIdFromPath(rel);
        String tableIdFromPath = lootTableIdFromPath(rel);

        // Loot table file / blob → contains edges
        if (!tableIdFromPath.isEmpty()) {
            for (String item : itemsFromLootText(text)) {
                addLootEdge(facts, item, tableIdFromPath);
                if (facts.size() >= MAX_FACTS) {
                    return List.copyOf(facts);
                }
            }
        } else {
            // KubeJS highPriorityData addJson(`ns:loot_tables/id.json`, {pools...})
            Matcher aj = ADD_JSON_LOOT.matcher(text);
            while (aj.find() && facts.size() < MAX_FACTS) {
                String tableId = aj.group(1).toLowerCase(Locale.ROOT) + ":"
                        + aj.group(2).toLowerCase(Locale.ROOT);
                int from = aj.end();
                int to = Math.min(text.length(), from + 4000);
                Matcher next = ADD_JSON_LOOT.matcher(text);
                if (next.find(from) && next.start() < to) {
                    to = next.start();
                }
                String blob = text.substring(from, to);
                for (String item : itemsFromLootText(blob)) {
                    addLootEdge(facts, item, tableId);
                    if (facts.size() >= MAX_FACTS) {
                        break;
                    }
                }
            }
        }

        // Stack LootJS table modifiers from same text (#5)
        for (String lootFact : PackIndex.parseLootJsFacts(text)) {
            // item:X -[loot]-> via:lootjs + table:Y
            int t = lootFact.indexOf(" + table:");
            if (t < 0 || !lootFact.startsWith("item:")) {
                continue;
            }
            String item = lootFact.substring(5, lootFact.indexOf(" -[loot]-> ")).toLowerCase(Locale.ROOT);
            String table = lootFact.substring(t + " + table:".length()).trim().toLowerCase(Locale.ROOT);
            int space = table.indexOf(' ');
            if (space > 0) {
                table = table.substring(0, space);
            }
            if (item.isEmpty() || table.isEmpty() || isNoise(item)) {
                continue;
            }
            addLootEdge(facts, item, table);
        }

        // Gateway rewards: loot_table
        if (GATEWAY_LOOT_TYPE.matcher(text).find() || text.toLowerCase(Locale.ROOT).contains("gateways:loot_table")) {
            Matcher lm = LOOT_TABLE_FIELD.matcher(text);
            LinkedHashSet<String> tables = new LinkedHashSet<>();
            while (lm.find() && tables.size() < 20) {
                tables.add(lm.group(1).toLowerCase(Locale.ROOT));
            }
            String gw = !gatewayId.isEmpty() ? gatewayId : firstAddJsonGatewayId(text);
            for (String table : tables) {
                if (!gw.isEmpty()) {
                    facts.add("gateway:" + gw + " -[reward_loot]-> loot_table:" + table);
                }
                facts.add("loot_table:" + table + " -[gateway_reward]-> true");
            }
        }

        // Gateway rewards: entity_loot → entities/<path>
        if (GATEWAY_ENTITY_LOOT_TYPE.matcher(text).find()
                || text.toLowerCase(Locale.ROOT).contains("entity_loot")) {
            // Only treat as gateway reward when type entity_loot appears (avoid wave entity lists alone).
            Matcher typeM = GATEWAY_ENTITY_LOOT_TYPE.matcher(text);
            LinkedHashSet<String> entities = new LinkedHashSet<>();
            while (typeM.find()) {
                int from = Math.max(0, typeM.start() - 200);
                int to = Math.min(text.length(), typeM.end() + 200);
                String window = text.substring(from, to);
                Matcher em = ENTITY_FIELD.matcher(window);
                while (em.find() && entities.size() < 12) {
                    entities.add(em.group(1).toLowerCase(Locale.ROOT));
                }
            }
            String gw = !gatewayId.isEmpty() ? gatewayId : firstAddJsonGatewayId(text);
            for (String ent : entities) {
                String table = entityLootTableId(ent);
                if (table.isEmpty()) {
                    continue;
                }
                if (!gw.isEmpty()) {
                    facts.add("gateway:" + gw + " -[reward_loot]-> loot_table:" + table
                            + " + entity_loot:" + ent);
                }
                facts.add("loot_table:" + table + " -[entity_loot_of]-> entity:" + ent);
            }
        }

        // Gateway rewards: stack / stack_list → direct item (e.g. b_a_d:friend)
        addGatewayStackFacts(facts, text, gatewayId);

        // Scripts / recipes that mention gate_pearl with gateway NBT
        addGatePearlScriptFacts(facts, text);

        if (facts.size() > MAX_FACTS) {
            return List.copyOf(new ArrayList<>(facts).subList(0, MAX_FACTS));
        }
        return List.copyOf(facts);
    }

    /**
     * Index {@code gateways:stack} / {@code stack_list} reward items as acquire edges.
     * Windowed per type occurrence so wave entity blobs are not treated as rewards.
     */
    private static void addGatewayStackFacts(LinkedHashSet<String> facts, String text, String gatewayId) {
        if (text == null || text.isBlank()) {
            return;
        }
        String gw = !gatewayId.isEmpty() ? gatewayId : firstAddJsonGatewayId(text);
        if (gw.isEmpty()) {
            return;
        }
        LinkedHashSet<String> items = new LinkedHashSet<>();
        Matcher stackType = GATEWAY_STACK_TYPE.matcher(text);
        while (stackType.find() && items.size() < MAX_STACK_ITEMS) {
            // Avoid matching stack_list's leading "stack"
            int endType = stackType.end();
            if (endType < text.length() && text.regionMatches(true, endType, "_list", 0, 5)) {
                continue;
            }
            int from = stackType.start();
            int to = Math.min(text.length(), endType + 350);
            collectStackItems(text.substring(from, to), items, 2);
        }
        Matcher listType = GATEWAY_STACK_LIST_TYPE.matcher(text);
        while (listType.find() && items.size() < MAX_STACK_ITEMS) {
            int from = listType.start();
            int to = Math.min(text.length(), listType.end() + 2500);
            collectStackItems(text.substring(from, to), items, MAX_STACK_ITEMS - items.size());
        }
        for (String item : items) {
            if (facts.size() >= MAX_FACTS) {
                return;
            }
            facts.add("item:" + item + " -[loot]-> gateway:" + gw);
            facts.add("gateway:" + gw + " -[reward_stack]-> item:" + item);
            attachSynthesizedGatePearl(facts, gw);
        }
    }

    /**
     * When a gateway stack reward is known, also index Gate Pearl → opens that gateway
     * (display stack uses NBT {@code gateway:"ns:path"}; no pack-specific item hardcode).
     */
    private static void attachSynthesizedGatePearl(LinkedHashSet<String> facts, String gatewayId) {
        if (facts == null || gatewayId == null || gatewayId.isBlank()) {
            return;
        }
        String gw = gatewayId.trim().toLowerCase(Locale.ROOT);
        if (facts.size() >= MAX_FACTS) {
            return;
        }
        facts.add("item:gateways:gate_pearl -[opens]-> gateway:" + gw);
        if (facts.size() < MAX_FACTS) {
            facts.add("gateway:" + gw + " -[gate_pearl]-> item:gateways:gate_pearl");
        }
    }

    /** Index {@code gateways:gate_pearl} + {@code gateway:"…"} from scripts/recipes. */
    private static void addGatePearlScriptFacts(LinkedHashSet<String> facts, String text) {
        if (text == null || text.isBlank() || facts == null) {
            return;
        }
        Matcher m = GATE_PEARL_GATEWAY_NBT.matcher(text);
        while (m.find() && facts.size() < MAX_FACTS) {
            String gw = m.group(1).toLowerCase(Locale.ROOT);
            if (gw.isEmpty() || isNoise(gw)) {
                continue;
            }
            attachSynthesizedGatePearl(facts, gw);
        }
    }

    private static void collectStackItems(String window, LinkedHashSet<String> items, int maxAdd) {
        if (window == null || maxAdd <= 0) {
            return;
        }
        int added = 0;
        Matcher im = STACK_ITEM_FIELD.matcher(window);
        while (im.find() && added < maxAdd && items.size() < MAX_STACK_ITEMS) {
            String id = im.group(1).toLowerCase(Locale.ROOT);
            if (isNoise(id) || !items.add(id)) {
                continue;
            }
            added++;
        }
    }

    private static boolean looksLikeLootTableBlob(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("\"pools\"") && lower.contains("\"entries\"");
    }

    private static String firstAddJsonGatewayId(String text) {
        Matcher m = ADD_JSON_GATEWAY.matcher(text);
        if (m.find()) {
            return m.group(1).toLowerCase(Locale.ROOT) + ":" + m.group(2).toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private static boolean isNoise(String id) {
        return id == null || id.isBlank()
                || id.startsWith("#")
                || id.endsWith(":air")
                || "minecraft:air".equals(id);
    }
}
