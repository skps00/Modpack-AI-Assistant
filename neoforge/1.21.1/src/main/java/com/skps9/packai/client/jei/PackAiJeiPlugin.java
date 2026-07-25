package com.skps9.packai.client.jei;

import java.util.Optional;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * Soft JEI hook — stores runtime for recipe (R) / usage (U) lookups.
 */
@JeiPlugin
public class PackAiJeiPlugin implements IModPlugin {
    private static volatile IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("packai", "jei");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    public static Optional<IJeiRuntime> runtime() {
        return Optional.ofNullable(runtime);
    }
}
