package com.skps9.packai;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.skps9.packai.client.ClientSetup;
import com.skps9.packai.client.gui.PackAiSettingsScreen;
import com.skps9.packai.config.PackAiConfig;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only entrypoint for Pack AI Assistant.
 */
@Mod(value = PackAiMod.MOD_ID, dist = Dist.CLIENT)
public class PackAiMod {
    public static final String MOD_ID = "packai";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PackAiMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, PackAiConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parent) -> new PackAiSettingsScreen(parent));
        ClientSetup.register(modBus);
        LOGGER.info("Pack AI Assistant loaded (client-only)");
    }
}
