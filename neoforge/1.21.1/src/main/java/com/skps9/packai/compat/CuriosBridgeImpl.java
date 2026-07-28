package com.skps9.packai.compat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * Curios API calls for NeoForge 1.21.1. Loaded only via {@link CuriosBridge} Class.forName when curios is present.
 */
public final class CuriosBridgeImpl {
    private CuriosBridgeImpl() {}

    public static void appendSlotKeys(LivingEntity entity, List<String> out) {
        Optional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(entity);
        inventory.ifPresent(handler -> {
            for (Map.Entry<String, ICurioStacksHandler> e : handler.getCurios().entrySet()) {
                String type = e.getKey();
                ICurioStacksHandler stacksHandler = e.getValue();
                if (stacksHandler == null) {
                    continue;
                }
                IDynamicStackHandler stacks = stacksHandler.getStacks();
                int n = stacks.getSlots();
                for (int i = 0; i < n; i++) {
                    out.add("curios:" + type + ":" + i);
                }
            }
        });
    }

    public static ItemStack stackAt(LivingEntity entity, String key) {
        // curios:<type>:<index> — type may contain ':'? rare; take last ':' as index.
        int last = key.lastIndexOf(':');
        if (last <= "curios:".length()) {
            return ItemStack.EMPTY;
        }
        String type = key.substring("curios:".length(), last);
        int index;
        try {
            index = Integer.parseInt(key.substring(last + 1));
        } catch (NumberFormatException e) {
            return ItemStack.EMPTY;
        }
        return CuriosApi.getCuriosInventory(entity).map(handler -> {
            ICurioStacksHandler stacksHandler = handler.getCurios().get(type);
            if (stacksHandler == null) {
                return ItemStack.EMPTY;
            }
            IDynamicStackHandler stacks = stacksHandler.getStacks();
            if (index < 0 || index >= stacks.getSlots()) {
                return ItemStack.EMPTY;
            }
            return stacks.getStackInSlot(index);
        }).orElse(ItemStack.EMPTY);
    }
}
