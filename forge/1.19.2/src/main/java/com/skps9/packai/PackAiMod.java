package com.skps9.packai;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.skps9.packai.client.ClientSetup;
import com.skps9.packai.client.gui.PackAiSettingsScreen;
import com.skps9.packai.config.PackAiConfig;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Client-oriented entry for Pack AI on Forge 1.19.2 (MinPlay / Preview).
 */
@Mod(PackAiMod.MOD_ID)
public class PackAiMod {
    public static final String MOD_ID = "packai";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PackAiMod() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            LOGGER.info("Pack AI present on dedicated server but is client-oriented");
            return;
        }
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, PackAiConfig.SPEC);
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (mc, parent) -> new PackAiSettingsScreen(parent)));
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ClientSetup.register(modBus);
        LOGGER.info("Pack AI Assistant loaded (Forge 1.19.2 MinPlay)");
    }
}
