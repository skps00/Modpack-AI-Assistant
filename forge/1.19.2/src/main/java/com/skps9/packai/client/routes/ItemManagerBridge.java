package com.skps9.packai.client.routes;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/** Append-only injection into JEI bookmarks / EMI favorites (no route-store coupling). */
public interface ItemManagerBridge {
    enum Capability {
        FULL,
        APPEND_ONLY,
        NONE
    }

    Capability capability();

    /** Inject in list order; returns count successfully injected. */
    int inject(List<ItemStack> stacks);

    /** Remove only entries this bridge previously injected. */
    int clear();
}
