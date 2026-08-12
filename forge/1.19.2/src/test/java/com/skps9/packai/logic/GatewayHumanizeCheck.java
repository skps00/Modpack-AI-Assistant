package com.skps9.packai.logic;

import java.util.List;

/**
 * Runnable check: gateway loot / reward_stack humanize stays Gateways-challenge wording,
 * never bare path tokens that look like entity drops (generic — any gateway id),
 * and leads with Gate Pearl {@code {{item:gateways:gate_pearl{gateway:"…"}}}} — not reward organ.
 */
public final class GatewayHumanizeCheck {
    private GatewayHumanizeCheck() {}

    public static void main(String[] args) {
        String drownLoot = Plainify.humanizeGraphFact(
                "item:pack:demo_reward -[loot]-> gateway:kubejs:pack/drowning");
        assertGatewaysNotEntityDrop(drownLoot, "drowning");
        assertHasPearlEmbed(drownLoot, "kubejs:pack/drowning");
        assert !drownLoot.contains("{{item:pack:demo_reward}}")
                : "must not lead with reward organ: " + drownLoot;

        String hydraLoot = Plainify.humanizeGraphFact(
                "item:pack:demo_hydra -[loot]-> gateway:kubejs:pack/hydra");
        assertGatewaysNotEntityDrop(hydraLoot, "hydra");
        assertHasPearlEmbed(hydraLoot, "kubejs:pack/hydra");

        String stack = Plainify.humanizeGraphFact(
                "gateway:kubejs:pack/wither_skeleton -[reward_stack]-> item:pack:demo_bone");
        assertGatewaysNotEntityDrop(stack, "wither_skeleton");
        assertHasPearlEmbed(stack, "kubejs:pack/wither_skeleton");
        assert stack.toLowerCase().contains("wither_skeleton") || stack.contains("Gateways")
                || stack.contains("閘道") || stack.contains("挑战") || stack.contains("挑戰")
                : "expected gateway id or Gateways label: " + stack;

        String tableLoot = Plainify.humanizeGraphFact(
                "item:minecraft:diamond -[loot]-> table:minecraft:chests/bonus_box");
        assert !tableLoot.contains("Gateways") && !tableLoot.contains("閘道") && !tableLoot.contains("挑战")
                && !tableLoot.contains("挑戰")
                : "loot_table must not use Gateways wording: " + tableLoot;

        String entityLoot = Plainify.humanizeGraphFact(
                "item:minecraft:rotten_flesh -[loot]-> entity:minecraft:zombie");
        String el = entityLoot.toLowerCase();
        assert el.contains("zombie") || entityLoot.contains("殭屍") || entityLoot.contains("僵尸")
                || entityLoot.contains("掉落") || el.contains("loot") || el.contains("entity")
                : "entity loot should stay entity-ish: " + entityLoot;
        assert !entityLoot.contains("Gateways") && !entityLoot.contains("閘道")
                : "entity loot must not say Gateways: " + entityLoot;

        String pearl = Plainify.pearlEmbedForGateway(
                "kubejs:pack/drowning",
                List.of("item:pack:demo_pearl -[opens]-> gateway:kubejs:pack/drowning"));
        assert "{{item:pack:demo_pearl}}".equals(pearl) : "custom opener preferred: " + pearl;

        String synth = Plainify.pearlEmbedForGateway("kubejs:pack/drowning", List.of());
        assertHasPearlEmbed(synth, "kubejs:pack/drowning");

        String fromOpens = Plainify.pearlEmbedForGateway(
                "kubejs:b_a_d/drowning",
                List.of("item:gateways:gate_pearl -[opens]-> gateway:kubejs:b_a_d/drowning"));
        assertHasPearlEmbed(fromOpens, "kubejs:b_a_d/drowning");

        // Focus loot→gateway only skipped when that raw edge is in ranked acquire set.
        String focusLootGw = "item:b_a_d:friend -[loot]-> gateway:kubejs:b_a_d/drowning";
        assert AskEngine.coveredByRankedAcquire(focusLootGw, java.util.Set.of(focusLootGw))
                : "focus gateway loot should be covered by acquire";
        assert !AskEngine.coveredByRankedAcquire(focusLootGw, java.util.Set.of())
                : "overflow (not ranked) must stay in graphLines";
        assert !AskEngine.coveredByRankedAcquire(
                "gateway:kubejs:b_a_d/drowning -[reward_stack]-> item:b_a_d:friend",
                java.util.Set.of(focusLootGw))
                : "reward_stack stays in graphLines";
        assert !AskEngine.coveredByRankedAcquire(
                "item:b_a_d:friend -[drops]-> item:minecraft:dirt",
                java.util.Set.of(focusLootGw))
                : "drops not ranked into acquire";
        // Cap overflow: edge not in rankedSkipEdges must not be skipped.
        String overflowLoot = "item:b_a_d:friend -[loot]-> table:minecraft:chests/overflow";
        assert !AskEngine.coveredByRankedAcquire(overflowLoot, java.util.Set.of(focusLootGw))
                : "unranked loot overflow must appear in graphLines";

        System.out.println("GatewayHumanizeCheck OK");
        System.out.println("sample drowning: " + drownLoot);
        System.out.println("sample hydra: " + hydraLoot);
        System.out.println("sample stack: " + stack);
        System.out.println("sample synth pearl: " + synth);
    }

    private static void assertHasPearlEmbed(String line, String gatewayId) {
        String marker = "{{item:gateways:gate_pearl{gateway:\"" + gatewayId.toLowerCase() + "\"}}}";
        assert line != null && line.contains(marker) : "expected " + marker + " in: " + line;
    }

    private static void assertGatewaysNotEntityDrop(String line, String pathToken) {
        assert line != null && !line.isBlank() : "empty humanize";
        String lower = line.toLowerCase();
        assert lower.contains("gateway") || line.contains("Gateways") || line.contains("閘道")
                || line.contains("挑战") || line.contains("挑戰") || line.contains("珍珠")
                || line.contains("pearl")
                : "expected Gateways challenge wording: " + line;
        assert lower.contains(pathToken) || lower.contains("kubejs:pack/" + pathToken)
                : "expected gateway id retained: " + line;
        boolean bareMobDrop =
                (line.contains("掉落") || lower.startsWith("loot"))
                        && !line.contains("Gateways")
                        && !line.contains("閘道")
                        && !line.contains("挑战")
                        && !line.contains("挑戰")
                        && !lower.contains("gateway");
        assert !bareMobDrop : "looks like entity/loot drop: " + line;
        String trimmed = line.trim();
        assert !trimmed.equalsIgnoreCase(pathToken)
                && !trimmed.equalsIgnoreCase("掉落：" + pathToken)
                && !trimmed.equalsIgnoreCase("Loot: " + pathToken)
                : "collapsed to bare path token: " + line;
    }
}
