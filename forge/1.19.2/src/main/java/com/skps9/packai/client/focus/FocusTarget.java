package com.skps9.packai.client.focus;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Crosshair focus snapshot for ask-about-target (block / entity / none).
 */
public record FocusTarget(
        Kind kind,
        String label,
        String blockId,
        int entityId,
        double distance,
        State state,
        @Nullable Float health,
        @Nullable Float maxHealth,
        @Nullable BlockPos blockPos
) {
    public enum Kind { ITEM, BLOCK, ENTITY, NONE }

    public enum State { OK, GONE, TOO_FAR }

    public static FocusTarget none() {
        return new FocusTarget(Kind.NONE, "", "", -1, 0.0, State.OK, null, null, null);
    }

    public static FocusTarget block(BlockHitResult hit, BlockState state, double dist) {
        String label = state.getBlock().getName().getString();
        ResourceLocation key = Registry.BLOCK.getKey(state.getBlock());
        String id = key == null ? "" : key.toString();
        return new FocusTarget(Kind.BLOCK, label, id, -1, dist, State.OK, null, null, hit.getBlockPos());
    }

    /**
     * New probe hit. Health only when {@code entity instanceof LivingEntity}; otherwise null.
     * Distance already filtered by probe — state is always {@link State#OK}.
     * {@code range} kept for call-site parity with plan §3.4 (unused for state).
     */
    public static FocusTarget entity(Entity entity, double dist, double range) {
        String label = entity.getDisplayName().getString();
        Float hp = null;
        Float maxHp = null;
        if (entity instanceof LivingEntity living) {
            hp = living.getHealth();
            maxHp = living.getMaxHealth();
        }
        return new FocusTarget(Kind.ENTITY, label, "", entity.getId(), dist, State.OK, hp, maxHp, null);
    }

    /** Tracked entity missing (despawned / wrong dimension). */
    public static FocusTarget gone(String label, int entityId, double lastDist) {
        return new FocusTarget(
                Kind.ENTITY,
                label == null ? "" : label,
                "",
                entityId,
                lastDist,
                State.GONE,
                null,
                null,
                null);
    }

    /** Tracked entity still present but beyond configured range. */
    public static FocusTarget tooFar(
            String label,
            int entityId,
            double dist,
            @Nullable Float health,
            @Nullable Float maxHealth
    ) {
        return new FocusTarget(
                Kind.ENTITY,
                label == null ? "" : label,
                "",
                entityId,
                dist,
                State.TOO_FAR,
                health,
                maxHealth,
                null);
    }

    /** Trace / display id: block registry id, entity numeric id, or empty. */
    public String id() {
        return switch (kind) {
            case BLOCK -> blockId == null ? "" : blockId;
            case ENTITY -> Integer.toString(entityId);
            default -> "";
        };
    }
}
