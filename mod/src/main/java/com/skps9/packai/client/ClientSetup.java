package com.skps9.packai.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skps9.packai.client.chat.ChatSession;
import com.skps9.packai.client.command.AiClientCommands;
import com.skps9.packai.client.context.GameContextCollector;
import com.skps9.packai.client.gui.AiAssistantScreen;
import com.skps9.packai.client.jei.JeiTargetResolver;
import com.skps9.packai.client.service.AskService;
import com.skps9.packai.client.tooltip.PackAiTooltipHandler;
import com.skps9.packai.client.tooltip.ThinkHoldTracker;
import com.skps9.packai.client.tooltip.TooltipHover;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
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
 */
public final class ClientSetup {
    public static final Lazy<KeyMapping> OPEN_AI = Lazy.of(() -> new KeyMapping(
            "key.packai.open",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            "key.categories.packai"
    ));

    /** Hold on JEI / inventory item to think (default Y). */
    public static final Lazy<KeyMapping> THINK_JEI = Lazy.of(() -> new KeyMapping(
            "key.packai.think",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            "key.categories.packai"
    ));

    private ClientSetup() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientSetup::onRegisterKeys);
        modBus.addListener(ClientSetup::onClientSetup);
        NeoForge.EVENT_BUS.addListener(PackAiTooltipHandler::onItemTooltip);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onClientTickPre);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onRegisterClientCommands);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onLoggingOut);
        ThinkHoldTracker.setOnComplete(stack -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> tryThinkHovered(mc, stack));
        });
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        AskService.INSTANCE.warmupAsync();
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_AI.get());
        event.register(THINK_JEI.get());
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        AiClientCommands.register(event.getDispatcher());
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        AskService.INSTANCE.warmupAsync();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        GameContextCollector.resetFingerprintCache();
        ChatSession.clear();
        ThinkHoldTracker.reset();
        TooltipHover.clear();
    }

    private static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_AI.get().consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new AiAssistantScreen());
            }
        }
        if (mc.player != null && mc.screen != null) {
            ThinkHoldTracker.tick(thinkKeyHeld(mc));
        } else {
            ThinkHoldTracker.reset();
            TooltipHover.clear();
        }
    }

    private static boolean thinkKeyHeld(Minecraft mc) {
        KeyMapping key = THINK_JEI.get();
        if (key.isDown()) {
            return true;
        }
        if (mc.screen == null) {
            return false;
        }
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getKey().getValue());
    }

    private static void tryThinkHovered(Minecraft mc, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            stack = JeiTargetResolver.hoveredItem(mc);
        }
        if (stack.isEmpty()) {
            toastHint(mc, "packai.status.think_no_item");
            return;
        }
        if (ChatSession.isBusy()) {
            toastHint(mc, "packai.status.think_busy");
            return;
        }
        AiAssistantScreen.openAndAskAbout(stack);
    }

    private static void toastHint(Minecraft mc, String key) {
        if (mc == null) {
            return;
        }
        SystemToast.addOrUpdate(
                mc.getToasts(),
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("packai.screen.title"),
                Component.translatable(key));
    }

    public static AskService askService() {
        return AskService.INSTANCE;
    }
}
