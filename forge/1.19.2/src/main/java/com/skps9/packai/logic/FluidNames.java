package com.skps9.packai.logic;

import java.util.Locale;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * Fluid display names for obtain paths and GUI (no client-only deps).
 */
public final class FluidNames {
    private FluidNames() {}

    public static String display(String fluidId) {
        if (fluidId == null || fluidId.isBlank()) {
            return "";
        }
        ResourceLocation loc = ResourceLocation.tryParse(fluidId);
        if (loc != null) {
            Fluid fluid = Registry.FLUID.get(loc);
            if (fluid != null) {
                try {
                    FluidStack stack = new FluidStack(fluid, 1);
                    String name = stack.getDisplayName().getString();
                    if (name != null && !name.isBlank()) {
                        return name;
                    }
                } catch (Throwable ignored) {
                    // headless / missing lang
                }
            }
            return titleCasePath(loc.getPath());
        }
        return titleCasePath(fluidId);
    }

    public static String amountText(String fluidId, int amountMb, String lang) {
        if (fluidId == null || fluidId.isBlank()) {
            return "";
        }
        String name = display(fluidId);
        if (name.isBlank()) {
            return "";
        }
        return ReplyLang.tr(lang, "packai.obtain.fluid", name, Integer.valueOf(amountMb));
    }

    private static String titleCasePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String[] parts = path.split("[_/]");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }
}
