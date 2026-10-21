package com.skps9.packai.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.skps9.packai.client.emi.EmiFavoriteBridge;
import com.skps9.packai.client.jei.JeiBookmarkBridge;
import com.skps9.packai.client.routes.ItemManagerBridge;
import com.skps9.packai.client.routes.RouteStoreCodec.SavedRoute;
import com.skps9.packai.client.routes.RouteStoreCodec.StepDto;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.ObtainGraph;
import com.skps9.packai.logic.ObtainPaths;
import com.skps9.packai.logic.RouteMarks;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/** JEI / EMI bookmark injection shared by route library and crafting tree screens. */
public final class RouteBookmarkInject {
    private RouteBookmarkInject() {}

    public static List<ItemStack> stacksForInject(SavedRoute route) {
        ObtainPaths.Path path = toPath(route);
        RouteMarks.Result r = RouteMarks.build(List.of(path));
        Set<String> seen = new LinkedHashSet<>();
        List<ItemStack> out = new ArrayList<>();
        for (RouteMarks.Mark m : r.marks()) {
            if (m == null || m.item() == null || m.item() == Items.AIR) {
                continue;
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(m.item());
            String uid = id == null ? "" : id.toString();
            if (uid.isEmpty() || !seen.add(uid)) {
                continue;
            }
            out.add(new ItemStack(m.item()));
        }
        return out;
    }

    public static Component inject(
            SavedRoute route,
            ItemManagerBridge bridge,
            boolean jei) {
        if (!PackAiConfig.routeMarks() || route == null) {
            return Component.empty();
        }
        if (bridge.capability() == ItemManagerBridge.Capability.NONE) {
            return Component.translatable("packai.route_library.status.inject_unavailable");
        }
        List<ItemStack> stacks = stacksForInject(route);
        bridge.clear();
        int n = bridge.inject(stacks);
        if (n <= 0) {
            return Component.translatable("packai.route_library.status.inject_unavailable");
        }
        if (jei) {
            return Component.translatable(
                    "packai.route_library.status.inject_jei_ok", n, jeiProfileLabel());
        }
        return Component.translatable("packai.route_library.status.inject_emi_ok", n);
    }

    private static String jeiProfileLabel() {
        String p = JeiBookmarkBridge.profileCode();
        return "NONE".equals(p) ? "" : p;
    }

    public static ObtainPaths.Path toPath(SavedRoute route) {
        Item target = itemFromId(route.itemId());
        List<ObtainPaths.Step> steps = new ArrayList<>();
        if (route.steps() != null) {
            for (StepDto dto : route.steps()) {
                if (dto == null) {
                    continue;
                }
                Item output = itemFromId(dto.output());
                ResourceLocation recipeId = parseId(dto.recipeId());
                ResourceLocation typeId = parseId(dto.typeId());
                List<List<Item>> groups = new ArrayList<>();
                if (dto.inputs() != null) {
                    for (String in : dto.inputs()) {
                        Item it = itemFromId(in);
                        if (it != null && it != Items.AIR) {
                            groups.add(List.of(it));
                        }
                    }
                }
                List<ObtainGraph.FluidReq> fluids = new ArrayList<>();
                if (dto.fluids() != null) {
                    for (var fd : dto.fluids()) {
                        if (fd != null && fd.fluidId() != null && !fd.fluidId().isBlank()) {
                            fluids.add(new ObtainGraph.FluidReq(fd.fluidId(), fd.amountMb()));
                        }
                    }
                }
                ObtainGraph.Edge edge =
                        new ObtainGraph.Edge(
                                recipeId,
                                typeId,
                                output,
                                1,
                                groups,
                                dto.actionLabel(),
                                List.copyOf(fluids),
                                false);
                steps.add(new ObtainPaths.Step(edge, groups));
            }
        }
        return new ObtainPaths.Path(target, List.copyOf(steps), route.complete());
    }

    @Nullable
    private static ResourceLocation parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new ResourceLocation(raw);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    public static Item itemFromId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            return item == null || item == Items.AIR ? null : item;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
