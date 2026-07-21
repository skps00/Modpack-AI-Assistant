package com.skps9.packai.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skps9.packai.client.command.AiClientCommands;
import com.skps9.packai.client.context.GameContextCollector;
import com.skps9.packai.client.gui.AiAssistantScreen;
import com.skps9.packai.client.service.AskService;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.Lazy;

import org.lwjgl.glfw.GLFW;

/**
 * Client-only hooks: keybind, commands, dual warmup (in-mod index).
 * No Mixins; tick handler only reads keybind (compat with Embeddium/ModernFix/EMI).
 */
public final class ClientSetup {
    public static final Lazy<KeyMapping> OPEN_AI = Lazy.of(() -> new KeyMapping(
            "key.packai.open",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            "key.categories.packai"
    ));

    private ClientSetup() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientSetup::onRegisterKeys);
        modBus.addListener(ClientSetup::onClientSetup);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onRegisterClientCommands);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onLoggingOut);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        // Early warmup (instance kubejs); world datapacks refreshed on LoggingIn
        AskService.INSTANCE.warmupAsync();
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_AI.get());
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        AiClientCommands.register(event.getDispatcher());
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        AskService.INSTANCE.warmupAsync();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        GameContextCollector.resetFingerprintCache();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_AI.get().consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new AiAssistantScreen());
            }
        }
    }

    public static AskService askService() {
        return AskService.INSTANCE;
    }
}
