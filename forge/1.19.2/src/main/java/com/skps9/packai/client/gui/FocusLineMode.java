package com.skps9.packai.client.gui;

import java.util.Locale;

import javax.annotation.Nullable;

import com.skps9.packai.client.focus.FocusProbe;
import com.skps9.packai.client.focus.FocusTarget;
import com.skps9.packai.logic.ItemResolver;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Mode-aware focus row label (item / block / entity). Pure for headless checks.
 */
public final class FocusLineMode {
    public static final int MODE_ICON_SIZE = 16;
    public static final int MODE_ICON_GAP = 2;
    public static final int MODE_ICON_TEXT_SHIFT = MODE_ICON_SIZE + MODE_ICON_GAP;

    /** Harness negative control: when false, always use crosshair {@link FocusProbe#panelLineKey}. */
    public static boolean modeAware = true;
    /** Harness negative control: when false, unselected rows still report a mode icon. */
    public static boolean omitIconWhenUnselected = true;

    private FocusLineMode() {}

    public record Line(String key, String... args) {}

    public record TextLayout(int textX, int textMax) {}

    public static TextLayout textLayout(int anchorX, int segX, boolean showModeIcon) {
        int textX = anchorX + (showModeIcon ? MODE_ICON_TEXT_SHIFT : 0);
        int textMax = Math.max(8, segX - textX - 6);
        return new TextLayout(textX, textMax);
    }

    public static boolean showModeIcon(
            @Nullable Minecraft mc,
            String focusOnly,
            boolean hasItem,
            ItemStack contextStack,
            FocusTarget target
    ) {
        return !modeIconStack(mc, focusOnly, hasItem, contextStack, target).isEmpty();
    }

    public static ItemStack modeIconStack(
            @Nullable Minecraft mc,
            String focusOnly,
            boolean hasItem,
            ItemStack contextStack,
            FocusTarget target
    ) {
        if (!modeAware) {
            return ItemStack.EMPTY;
        }
        if (!lineShowsModeIcon(focusOnly, hasItem, target)) {
            if (!omitIconWhenUnselected) {
                return new ItemStack(Items.BARRIER);
            }
            return ItemStack.EMPTY;
        }
        String mode = focusOnly == null || focusOnly.isBlank() ? "auto" : focusOnly;
        if ("block".equals(mode)) {
            if (target != null
                    && target.kind() == FocusTarget.Kind.BLOCK
                    && target.state() == FocusTarget.State.OK) {
                return ItemResolver.safeIcon(ItemResolver.stackFromId(target.blockId()));
            }
            return ItemStack.EMPTY;
        }
        if ("entity".equals(mode)) {
            if (target != null
                    && target.kind() == FocusTarget.Kind.ENTITY
                    && target.state() == FocusTarget.State.OK) {
                return entitySpawnEggIcon(mc, target);
            }
            return ItemStack.EMPTY;
        }
        if (hasItem && contextStack != null && !contextStack.isEmpty()) {
            return ItemResolver.safeIcon(contextStack);
        }
        return ItemStack.EMPTY;
    }

    private static boolean lineShowsModeIcon(String focusOnly, boolean hasItem, FocusTarget target) {
        Line line = resolve(focusOnly, hasItem, "", target);
        String key = line.key();
        return "packai.focus.item".equals(key)
                || "packai.focus.block".equals(key)
                || "packai.focus.current".equals(key);
    }

    private static ItemStack entitySpawnEggIcon(@Nullable Minecraft mc, FocusTarget target) {
        if (mc == null || mc.level == null || target.entityId() < 0) {
            return ItemStack.EMPTY;
        }
        Entity entity = mc.level.getEntity(target.entityId());
        if (entity == null) {
            return ItemStack.EMPTY;
        }
        SpawnEggItem egg = SpawnEggItem.byId(entity.getType());
        if (egg == null) {
            return ItemStack.EMPTY;
        }
        return ItemResolver.safeIcon(new ItemStack(egg));
    }

    public static Line resolve(String focusOnly, boolean hasItem, String itemLabel, FocusTarget target) {
        if (!modeAware) {
            return legacyCrosshairLine(target);
        }
        String mode = focusOnly == null || focusOnly.isBlank() ? "auto" : focusOnly;
        if ("block".equals(mode)) {
            return blockLine(target);
        }
        if ("entity".equals(mode)) {
            return entityLine(target);
        }
        return itemLine(hasItem, itemLabel);
    }

    private static Line itemLine(boolean hasItem, String itemLabel) {
        if (hasItem) {
            return new Line("packai.focus.item", itemLabel == null ? "" : itemLabel);
        }
        return new Line("packai.focus.no_item");
    }

    private static Line blockLine(FocusTarget target) {
        if (target != null
                && target.kind() == FocusTarget.Kind.BLOCK
                && target.state() == FocusTarget.State.OK) {
            return new Line("packai.focus.block", target.label());
        }
        return new Line("packai.focus.no_block");
    }

    private static Line entityLine(FocusTarget target) {
        if (target == null || target.kind() != FocusTarget.Kind.ENTITY) {
            return new Line("packai.focus.no_entity");
        }
        if (target.state() == FocusTarget.State.GONE) {
            return new Line("packai.focus.entity_gone");
        }
        if (target.state() == FocusTarget.State.TOO_FAR) {
            return new Line("packai.focus.too_far");
        }
        if (target.state() == FocusTarget.State.OK) {
            String mid = Integer.toString(target.entityId());
            String dist = String.format(Locale.ROOT, "%.1f", target.distance());
            return new Line("packai.focus.current", target.label(), mid, dist);
        }
        return new Line("packai.focus.no_entity");
    }

    private static Line legacyCrosshairLine(FocusTarget target) {
        FocusTarget t = target == null ? FocusTarget.none() : target;
        String key = FocusProbe.panelLineKey(t);
        if ("packai.focus.current".equals(key)) {
            String mid = t.kind() == FocusTarget.Kind.BLOCK ? t.blockId() : Integer.toString(t.entityId());
            String dist = String.format(Locale.ROOT, "%.1f", t.distance());
            return new Line(key, t.label(), mid, dist);
        }
        return new Line(key);
    }
}
