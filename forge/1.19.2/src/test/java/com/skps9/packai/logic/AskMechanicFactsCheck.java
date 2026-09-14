package com.skps9.packai.logic;

import java.util.List;

/**
 * KubeJS mechanic scan + FTB quest_text facts. Fixture strings only. Run with -ea.
 */
public final class AskMechanicFactsCheck {
    private AskMechanicFactsCheck() {}

    static final String REL = "server_scripts/demo.js";

    static final String NEW_STYLE = """
            ItemEvents.rightClicked('mod:demo_item', e => {
                e.player.give('minecraft:diamond')
            })
            """;

    static final String OLD_STYLE = """
            onEvent('item.right_click', e => {
                if (e.item.id == 'mod:old_item') {
                    e.player.tell(Text.black('clicked old relic'))
                }
            })
            """;

    static final String CURIOS_DEATH = """
            EntityEvents.death('minecraft:skeleton', event => {
                if (nbt.ForgeCaps['curios:inventory']) {
                    let curios = nbt.ForgeCaps['curios:inventory'];
                    if (curios.toString().includes('momo_dlc:t-02-99')) {
                        let random = Math.random() * Math.random() * 100
                        if (random <= 1) player.give("momo_dlc:dream_rain");
                    }
                }
            })
            """;

    static final String CHANCE_VAR_LT = """
            ItemEvents.rightClicked('mod:pct100_item', e => {
                let r = Math.random() * 100
                if (r < 5) e.player.give('minecraft:emerald')
            })
            """;

    static final String CHANCE_UNIT_LT = """
            ItemEvents.rightClicked('mod:unit_item', e => {
                if (Math.random() < 0.02) e.player.give('minecraft:gold_ingot')
            })
            """;

    static final String NO_CHANCE = """
            ItemEvents.rightClicked('mod:plain_item', e => {
                e.player.give('minecraft:dirt')
            })
            """;

    static final String BRACES = """
            ItemEvents.rightClicked('mod:brace_item', e => {
                let s = "not { closed }"
                // fake }
                /* also } here */
                let t = `template { still } ok`
                e.player.give('minecraft:pearl')
            })
            """;

    static final String ARRAY = """
            ItemEvents.rightClicked(['mod:arr_a', 'mod:arr_b'], e => {
                e.player.give('minecraft:stick')
            })
            """;

    static final String TAG = """
            ItemEvents.rightClicked('#forge:ingots', e => {
                e.player.drop('minecraft:iron_nugget')
            })
            """;

    static final String QUEST_SNBT = """
            {
            	title: "Dream charm"
            	description: [
            		"Wear momo_dlc:t-02-99 while killing skeletons."
            	]
            	tasks: [{
            		item: "momo_dlc:t-02-99"
            		type: "item"
            	}]
            }
            """;

    public static void main(String[] args) {
        KubeJsMechanicScan.resetUnknown();
        newStyle();
        oldStyle();
        curiosChance();
        chanceVariants();
        braces();
        arrayAndTag();
        negativeNone();
        questText();
        System.out.println("AskMechanicFactsCheck OK");
    }

    private static void newStyle() {
        List<String> facts = KubeJsMechanicScan.factsForItem(NEW_STYLE, REL, "mod:demo_item");
        assert facts.stream().anyMatch(f ->
                f.contains("item:mod:demo_item")
                        && f.contains("-[use]->")
                        && f.contains("ItemEvents.rightClicked")
                        && f.contains("minecraft:diamond")
                        && f.contains("source:kubejs/" + REL + ":")
                        && f.contains("tier:A")) : facts;
        assert facts.stream().anyMatch(f ->
                f.contains("-[drops]->") && f.contains("item:minecraft:diamond")) : facts;
        assert facts.stream().anyMatch(f -> f.contains(REL + ":1") || f.matches(".*demo\\.js:\\d+.*"))
                : facts;
    }

    private static void oldStyle() {
        List<String> facts = KubeJsMechanicScan.factsForItem(OLD_STYLE, REL, "mod:old_item");
        assert facts.stream().anyMatch(f ->
                f.contains("item:mod:old_item")
                        && f.contains("ItemEvents.rightClicked")
                        && f.contains("tier:A")
                        && f.contains("source:kubejs/" + REL + ":")) : facts;
        assert facts.stream().anyMatch(f -> f.contains("clicked old relic") || f.contains("note:"))
                : facts;
    }

    private static void curiosChance() {
        String rel = "server_scripts/momo_dlc/entity/momo_dlc_entity_death.js";
        List<String> facts = KubeJsMechanicScan.factsForItem(CURIOS_DEATH, rel, "momo_dlc:t-02-99");
        assert facts.stream().anyMatch(f ->
                f.contains("item:momo_dlc:t-02-99")
                        && f.contains("-[drops]->")
                        && f.contains("item:momo_dlc:dream_rain")
                        && f.contains("EntityEvents.death")
                        && f.contains("minecraft:skeleton")
                        && f.contains("curios:momo_dlc:t-02-99")
                        && f.contains("機率:<=1")
                        && f.contains("<=1")
                        && f.contains("source:kubejs/" + rel + ":")
                        && f.contains("tier:A")) : facts;
        List<String> asLoot = KubeJsMechanicScan.factsForItem(CURIOS_DEATH, rel, "momo_dlc:dream_rain");
        assert asLoot.stream().anyMatch(f ->
                f.contains("-[drops]->") && f.contains("momo_dlc:dream_rain")) : asLoot;
    }

    private static void chanceVariants() {
        List<String> pct = KubeJsMechanicScan.factsForItem(CHANCE_VAR_LT, REL, "mod:pct100_item");
        assert pct.stream().anyMatch(f -> f.contains("機率:<5%")) : pct;
        List<String> unit = KubeJsMechanicScan.factsForItem(CHANCE_UNIT_LT, REL, "mod:unit_item");
        assert unit.stream().anyMatch(f -> f.contains("機率:<2%")) : unit;
        List<String> none = KubeJsMechanicScan.factsForItem(NO_CHANCE, REL, "mod:plain_item");
        assert !none.isEmpty() : none;
        assert none.stream().noneMatch(f -> f.contains("機率:")) : none;
    }

    private static void braces() {
        List<String> facts = KubeJsMechanicScan.factsForItem(BRACES, REL, "mod:brace_item");
        assert facts.stream().anyMatch(f ->
                f.contains("mod:brace_item")
                        && f.contains("minecraft:pearl")
                        && f.contains("ItemEvents.rightClicked")) : facts;
        int open = BRACES.indexOf('{', BRACES.indexOf("=>"));
        int close = KubeJsMechanicScan.matchingBrace(BRACES, open);
        String body = BRACES.substring(open, close + 1);
        assert body.contains("minecraft:pearl") : body;
        assert body.contains("not { closed }") : body;
    }

    private static void arrayAndTag() {
        List<String> a = KubeJsMechanicScan.factsForItem(ARRAY, REL, "mod:arr_a");
        List<String> b = KubeJsMechanicScan.factsForItem(ARRAY, REL, "mod:arr_b");
        assert a.stream().anyMatch(f -> f.contains("item:mod:arr_a") && f.contains("minecraft:stick"))
                : a;
        assert b.stream().anyMatch(f -> f.contains("item:mod:arr_b") && f.contains("minecraft:stick"))
                : b;
        List<String> tag = KubeJsMechanicScan.factsForItem(TAG, REL, "#forge:ingots");
        assert tag.stream().anyMatch(f ->
                f.contains("item:#forge:ingots")
                        && f.contains("minecraft:iron_nugget")) : tag;
    }

    private static void negativeNone() {
        List<String> kjs = KubeJsMechanicScan.factsForItem(NEW_STYLE, REL, "minecraft:barrier");
        assert kjs.isEmpty() : kjs;
        List<String> merged = KubeJsMechanicScan.honestMerge(kjs, List.of());
        assert merged.equals(List.of(KubeJsMechanicScan.NONE_MARK)) : merged;
        assert merged.contains("mechanic:none");
    }

    private static void questText() {
        List<String> facts = QuestMechanicFacts.factsForItem(
                QUEST_SNBT, "quests/chapters/demo.snbt", "momo_dlc:t-02-99");
        assert facts.stream().anyMatch(f ->
                f.contains("item:momo_dlc:t-02-99")
                        && f.contains("-[quest_text]->")
                        && f.contains(QuestMechanicFacts.DISCLAIMER)
                        && f.contains("tier:C")
                        && f.contains("source:ftbquests/quests/chapters/demo.snbt")) : facts;
        assert facts.stream().anyMatch(f -> f.contains("Wear momo_dlc:t-02-99")
                || f.contains("momo_dlc:t-02-99")) : facts;
        assert facts.size() <= QuestMechanicFacts.MAX_FACTS_PER_ITEM : facts;
    }
}
