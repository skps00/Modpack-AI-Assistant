package com.skps9.packai.logic;

import java.util.List;

/**
 * Datapack {@code minecraft:consume_item} → PURPOSE [CONSUME_USE]
 * (Goety forbidden_scroll fixture among others).
 */
public final class ItemConsumeUseCheck {
    private ItemConsumeUseCheck() {}

    public static void main(String[] args) {
        ItemConsumeUseFacts.reset();

        String json = """
                {
                  "display": {
                    "title": { "text": "Grandma's Secret Recipe" },
                    "description": { "text": "Read the Forbidden Scroll and discover the secrets of Immortality." },
                    "frame": "challenge",
                    "hidden": true
                  },
                  "criteria": {
                    "forbidden_scroll": {
                      "trigger": "minecraft:consume_item",
                      "conditions": {
                        "item": {
                          "items": [ "goety:forbidden_scroll" ]
                        }
                      }
                    }
                  }
                }
                """;
        ItemConsumeUseFacts.indexFromJson("goety:goety/read_forbidden_scroll", json);
        List<String> lines = ItemConsumeUseFacts.purposeLinesForItem("goety:forbidden_scroll");
        assert !lines.isEmpty() : "expected CONSUME_USE lines";
        String joined = String.join("\n", lines);
        assert joined.contains(ItemConsumeUseFacts.HEADER) : joined;
        assert joined.contains("Right-click / consume") : joined;
        assert joined.contains("Grandma's Secret Recipe") : joined;
        assert joined.contains("Immortality") : joined;
        assert lines.stream().noneMatch(l -> l.contains("goety:forbidden_scroll"))
                : "PURPOSE line should not dump registry id: " + joined;

        // Non-consume advancement → no hit
        ItemConsumeUseFacts.reset();
        ItemConsumeUseFacts.indexFromJson("goety:goety/other", """
                {
                  "display": { "title": { "text": "X" }, "description": { "text": "Y" } },
                  "criteria": {
                    "inv": { "trigger": "minecraft:inventory_changed", "conditions": {} }
                  }
                }
                """);
        assert ItemConsumeUseFacts.purposeLinesForItem("goety:forbidden_scroll").isEmpty();

        assert AskPurposeContext.isPurposeGraphFact(
                "item:goety:forbidden_scroll -[consume_item]-> Read the Forbidden Scroll");
        assert ItemConsumeUseFacts.isAdvancementEntry(
                "data/goety/advancements/goety/read_forbidden_scroll.json");
        assert ItemConsumeUseFacts.advIdFromPath(
                        "data/goety/advancements/goety/read_forbidden_scroll.json")
                .equals("goety:goety/read_forbidden_scroll");

        List<String> items = ItemConsumeUseFacts.itemsFromConsumeConditions(
                ItemConsumeUseFacts.parseObject("""
                        { "item": { "items": ["goety:forbidden_scroll", "goety:floral_scroll"] } }
                        """));
        assert items.contains("goety:forbidden_scroll");
        assert items.contains("goety:floral_scroll");

        System.out.println("ItemConsumeUseCheck OK");
    }
}
