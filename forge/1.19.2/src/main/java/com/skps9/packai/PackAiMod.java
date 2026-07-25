package com.skps9.packai;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Skeleton hello entry for Forge 1.19.2. Full Pack AI ports in MinPlay milestone.
 */
@Mod(PackAiMod.MOD_ID)
public class PackAiMod {
    public static final String MOD_ID = "packai";
    public static final Logger LOGGER = LogManager.getLogger();

    public PackAiMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            LOGGER.info("Pack AI Assistant skeleton loaded (Forge 1.19.2 client) — gameplay UI not ported yet");
        } else {
            LOGGER.info("Pack AI Assistant skeleton present on dedicated server but is client-oriented");
        }
    }
}
