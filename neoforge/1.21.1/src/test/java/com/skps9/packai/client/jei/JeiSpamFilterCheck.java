package com.skps9.packai.client.jei;

/** Runnable check: universal per-block spam item ids. */
public final class JeiSpamFilterCheck {
    private JeiSpamFilterCheck() {}

    public static void main(String[] args) {
        // Facades
        assert JeiUniversalSpam.isSpamItemId("integrateddynamics:facade");
        assert JeiUniversalSpam.isSpamItemId("ae2:facade");
        assert JeiUniversalSpam.isSpamItemId("some_mod:oak_facade");
        assert JeiUniversalSpam.isSpamItemId("mod:facade/stone");

        // FramedBlocks
        assert JeiUniversalSpam.isSpamItemId("framedblocks:framed_divided_slab");
        assert JeiUniversalSpam.isSpamItemId("framedblocks:framed_slab");
        assert JeiUniversalSpam.isSpamItemId("framedblocks:framed_stairs");

        // Covers
        assert JeiUniversalSpam.isSpamItemId("refinedstorage:cover");

        // Real recipes — keep
        assert !JeiUniversalSpam.isSpamItemId("minecraft:dirt");
        assert !JeiUniversalSpam.isSpamItemId("evilcraft:environmental_accumulator");
        assert !JeiUniversalSpam.isSpamItemId("chipped:carved_stripped_spruce_log");
        assert !JeiUniversalSpam.isSpamItemId("mod:facade_controller");

        System.out.println("JeiSpamFilterCheck OK");
    }
}
