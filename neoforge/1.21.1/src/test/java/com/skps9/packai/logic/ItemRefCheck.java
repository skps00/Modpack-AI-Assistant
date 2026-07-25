package com.skps9.packai.logic;

/** Runnable check: LLM gets hover text the player sees. */
public final class ItemRefCheck {
    private ItemRefCheck() {}

    public static void main(String[] args) {
        ItemRef a = new ItemRef("the_bumblezone:honey_compass", "Honey Compass (Throne)");
        assert a.label().equals("Honey Compass (Throne)") : "hover text must be sent as-is";
        assert a.hintTokens().contains("throne");
        ItemRef bare = new ItemRef("minecraft:stick", null);
        assert "stick".equals(bare.label());
        System.out.println("ItemRefCheck OK");
    }
}
