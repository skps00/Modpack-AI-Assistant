package com.skps9.packai.client.focus;

import com.skps9.packai.config.PackAiConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One-shot block+entity focus probe: same long ray for occlusion, vanilla nearer-wins;
 * zero side effects (never mutates Minecraft pick fields).
 */
public final class FocusProbe {
    /** Ray hard cap (safety valve); independent of config max (same numeric value by coincidence). */
    public static final double RAY_MAX = 64.0;

    private FocusProbe() {}

    /**
     * Combined probe: occlusion + block target share one long ray ({@code max(pick, range)});
     * entity wins only when closer than the occlusion hit; block target limited to vanilla reach.
     */
    public static FocusTarget probe(Minecraft mc, double entityRangeBlocks) {
        if (mc == null || mc.level == null || mc.player == null || mc.gameMode == null
                || !PackAiConfig.focusProbeEnabled()) {
            return FocusTarget.none();
        }
        double range = Math.min(Math.max(entityRangeBlocks, 4.0), RAY_MAX);
        double pick = mc.gameMode.getPickRange();
        Vec3 eye = mc.player.getEyePosition(1.0F);
        Vec3 view = mc.player.getViewVector(1.0F);

        // 1) Occlusion + block target: one long ray (= entity range), not pick-only.
        double rayLen = Math.max(pick, range);
        BlockHitResult bh = mc.level.clip(new ClipContext(
                eye,
                eye.add(view.scale(rayLen)),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player));
        // F13 trap: miss() results still type-check as BlockHitResult — always use getType().
        boolean bhHit = bh != null && bh.getType() != HitResult.Type.MISS;
        double bhSq = bhHit ? bh.getLocation().distanceToSqr(eye) : Double.MAX_VALUE;

        // 2) Entity: ray length = range; maxDistSq = min(range², occlusion²) → no wall pierce.
        double maxSq = Math.min(range * range, bhSq);
        Vec3 to = eye.add(view.scale(range));
        AABB box = mc.player.getBoundingBox().expandTowards(to.subtract(eye)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(
                mc.player,
                eye,
                to,
                box,
                e -> !e.isSpectator() && e.isPickable(),
                maxSq);
        if (eh != null) {
            double d2 = eh.getLocation().distanceToSqr(eye);
            if (d2 < bhSq) {
                return FocusTarget.entity(eh.getEntity(), Math.sqrt(d2), range);
            }
        }

        // 3) Block target only within vanilla reach; occlusion beyond pick ⇒ NONE.
        if (bhHit && bhSq <= pick * pick) {
            return FocusTarget.block(bh, mc.level.getBlockState(bh.getBlockPos()), Math.sqrt(bhSq));
        }
        return FocusTarget.none();
    }

    /**
     * Block-only: clip within pick reach; no entity candidates.
     * {@code entityRangeBlocks} unused but kept for signature parity with {@link #probe}.
     */
    public static FocusTarget probeBlockOnly(Minecraft mc, double entityRangeBlocks) {
        if (mc == null || mc.level == null || mc.player == null || mc.gameMode == null
                || !PackAiConfig.focusProbeEnabled()) {
            return FocusTarget.none();
        }
        double pick = mc.gameMode.getPickRange();
        Vec3 eye = mc.player.getEyePosition(1.0F);
        Vec3 view = mc.player.getViewVector(1.0F);
        BlockHitResult bh = mc.level.clip(new ClipContext(
                eye,
                eye.add(view.scale(pick)),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player));
        boolean bhHit = bh != null && bh.getType() != HitResult.Type.MISS;
        if (!bhHit) {
            return FocusTarget.none();
        }
        double bhSq = bh.getLocation().distanceToSqr(eye);
        if (bhSq <= pick * pick) {
            return FocusTarget.block(bh, mc.level.getBlockState(bh.getBlockPos()), Math.sqrt(bhSq));
        }
        return FocusTarget.none();
    }

    /**
     * Entity-only: still capped by range, but no block occlusion clamp
     * (player explicitly asked for entity).
     */
    public static FocusTarget probeEntityOnly(Minecraft mc, double entityRangeBlocks) {
        if (mc == null || mc.level == null || mc.player == null
                || !PackAiConfig.focusProbeEnabled()) {
            return FocusTarget.none();
        }
        double range = Math.min(Math.max(entityRangeBlocks, 4.0), RAY_MAX);
        Vec3 eye = mc.player.getEyePosition(1.0F);
        Vec3 view = mc.player.getViewVector(1.0F);
        Vec3 to = eye.add(view.scale(range));
        double maxSq = range * range;
        AABB box = mc.player.getBoundingBox().expandTowards(to.subtract(eye)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(
                mc.player,
                eye,
                to,
                box,
                e -> !e.isSpectator() && e.isPickable(),
                maxSq);
        if (eh == null) {
            return FocusTarget.none();
        }
        double d2 = eh.getLocation().distanceToSqr(eye);
        return FocusTarget.entity(eh.getEntity(), Math.sqrt(d2), range);
    }

    /**
     * Pure winner decision (vanilla nearer-wins + pick reach gate). Used by FocusCheck.
     *
     * @param blockHit   block ray hit (getType != MISS)
     * @param blockDistSq distance² to block hit (ignored if !blockHit)
     * @param pickReach  vanilla getPickRange()
     * @param entityHit  entity candidate present
     * @param entityDistSq distance² to entity
     */
    public static FocusTarget.Kind pickWinner(
            boolean blockHit,
            double blockDistSq,
            double pickReach,
            boolean entityHit,
            double entityDistSq
    ) {
        double bhSq = blockHit ? blockDistSq : Double.MAX_VALUE;
        if (entityHit && entityDistSq < bhSq) {
            return FocusTarget.Kind.ENTITY;
        }
        if (blockHit && blockDistSq <= pickReach * pickReach) {
            return FocusTarget.Kind.BLOCK;
        }
        return FocusTarget.Kind.NONE;
    }

    /**
     * Tracked-entity state for refresh path (not new probe).
     *
     * @param present entity still in level (same dimension)
     * @param distance current eye distance
     * @param range configured entity focus range
     */
    public static FocusTarget.State trackedState(boolean present, double distance, double range) {
        if (!present) {
            return FocusTarget.State.GONE;
        }
        if (distance > range) {
            return FocusTarget.State.TOO_FAR;
        }
        return FocusTarget.State.OK;
    }

    /** Resolve tracked entity from id; returns GONE / TOO_FAR / OK ENTITY. */
    public static FocusTarget resolveTracked(
            Minecraft mc,
            int entityId,
            String lastLabel,
            double range
    ) {
        if (mc == null || mc.level == null || mc.player == null) {
            return FocusTarget.gone(lastLabel, entityId, 0.0);
        }
        Entity e = mc.level.getEntity(entityId);
        if (e == null) {
            return FocusTarget.gone(lastLabel, entityId, 0.0);
        }
        double dist = e.distanceTo(mc.player);
        FocusTarget.State st = trackedState(true, dist, range);
        if (st == FocusTarget.State.TOO_FAR) {
            Float hp = null;
            Float maxHp = null;
            if (e instanceof net.minecraft.world.entity.LivingEntity living) {
                hp = living.getHealth();
                maxHp = living.getMaxHealth();
            }
            return FocusTarget.tooFar(e.getDisplayName().getString(), entityId, dist, hp, maxHp);
        }
        return FocusTarget.entity(e, dist, range);
    }

    /** Panel refresh gate: probe on tick 2, 4, 6… — not every frame. */
    public static boolean panelProbeDue(int tick) {
        return tick >= 2 && tick % 2 == 0;
    }

    /**
     * One status line. {@code current} only for a live OK target; gone / too-far / none each once.
     */
    public static String panelLineKey(FocusTarget target) {
        if (target == null || target.kind() == FocusTarget.Kind.NONE) {
            return "packai.focus.none";
        }
        if (target.state() == FocusTarget.State.GONE) {
            return "packai.focus.entity_gone";
        }
        if (target.state() == FocusTarget.State.TOO_FAR) {
            return "packai.focus.too_far";
        }
        return "packai.focus.current";
    }

    /**
     * Shown target. Tracked entity (by id) wins over a fresh probe. A held reselect sticks
     * until the caller clears it. Otherwise the fresh probe is the cache.
     */
    public static FocusTarget panelShown(
            FocusTarget previous,
            FocusTarget probed,
            FocusTarget tracked,
            boolean holdReselect
    ) {
        if (previous != null
                && previous.kind() == FocusTarget.Kind.ENTITY
                && previous.entityId() >= 0
                && tracked != null) {
            return tracked;
        }
        if (holdReselect && previous != null && previous.kind() != FocusTarget.Kind.NONE) {
            return previous;
        }
        return probed == null ? FocusTarget.none() : probed;
    }

    /** True when kind, id, or tracked state changed (distance-only OK does not). */
    public static boolean panelSigChanged(FocusTarget previous, FocusTarget next) {
        if (next == null) {
            return false;
        }
        if (previous == null) {
            return true;
        }
        if (previous.kind() != next.kind() || previous.state() != next.state()) {
            return true;
        }
        String a = previous.id();
        String b = next.id();
        return a == null ? b != null : !a.equals(b);
    }
}
