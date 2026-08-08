package com.skps9.packai.logic;

/**
 * Runnable check: LLM gets hover text the player sees.
 * (No ItemStack here — neo test classpath has no Minecraft.)
 */
public final class ItemRefCheck {
    private ItemRefCheck() {}

    public static void main(String[] args) {
        ItemRef a = new ItemRef("the_bumblezone:honey_compass", "Honey Compass (Throne)");
        assert a.label().equals("Honey Compass (Throne)") : "hover text must be sent as-is";
        assert a.hintTokens().contains("throne");
        assert !a.hasSample() : "2-arg ctor has no sample";
        ItemRef bare = new ItemRef("minecraft:stick", null);
        assert "stick".equals(bare.label());
        System.out.println("ItemRefCheck OK");
    }
}
