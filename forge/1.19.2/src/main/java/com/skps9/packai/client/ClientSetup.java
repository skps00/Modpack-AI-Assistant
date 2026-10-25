package com.skps9.packai.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skps9.packai.PackAiMod;
import com.skps9.packai.api.AskToolRegistration;
import com.skps9.packai.api.RegistrationStatus;
import com.skps9.packai.client.autotest.AutoTestHarness;
import com.skps9.packai.client.chat.ChatSession;
import com.skps9.packai.client.command.AiClientCommands;
import com.skps9.packai.client.context.GameContextCollector;
import com.skps9.packai.client.focus.FocusAsk;
import com.skps9.packai.client.gui.AiAssistantScreen;
import com.skps9.packai.client.gui.RouteLibraryScreen;
import com.skps9.packai.client.jei.JeiCategoryIndex;
import com.skps9.packai.client.jei.JeiTargetResolver;
import com.skps9.packai.client.knowledge.GuidebookIndex;
import com.skps9.packai.client.knowledge.ItemIndex;
import com.skps9.packai.client.service.AskService;
import com.skps9.packai.client.tooltip.PackAiTooltipHandler;
import com.skps9.packai.client.tooltip.ThinkHoldTracker;
import com.skps9.packai.client.tooltip.TooltipHover;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.AskToolLoop;
import com.skps9.packai.logic.ObtainGraphRuntime;
import com.skps9.packai.logic.RenderStallProbe;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Lazy;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import org.lwjgl.glfw.GLFW;

/**
 * Client hooks: open keybind, hold-Y think, world focus-ask, commands, warmup.
 */
public final class ClientSetup {
    public static final Lazy<KeyMapping> OPEN_AI = Lazy.of(() -> new KeyMapping(
            "key.packai.open",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            "key.categories.packai"
    ));

    public static final Lazy<KeyMapping> THINK_JEI = Lazy.of(() -> new KeyMapping(
            "key.packai.think",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            "key.categories.packai"
    ));

    /** World crosshair ask (default Y, IN_GAME — splits from GUI THINK_JEI). */
    public static final Lazy<KeyMapping> FOCUS_ASK = Lazy.of(() -> new KeyMapping(
            "key.packai.focus",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            "key.categories.packai"
    ));

    public static final Lazy<KeyMapping> ROUTE_LIBRARY = Lazy.of(() -> new KeyMapping(
            "key.packai.route_library",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.packai"
    ));

    private ClientSetup() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientSetup::onRegisterKeys);
        modBus.addListener(ClientSetup::onClientSetup);
        MinecraftForge.EVENT_BUS.addListener(PackAiTooltipHandler::onItemTooltip);
        MinecraftForge.EVENT_BUS.addListener(PackAiTooltipHandler::onTooltipColor);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onRecipesUpdated);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onTagsUpdated);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onRegisterClientCommands);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onLoggingOut);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onLevelUnload);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onAskToolRegister);
        ThinkHoldTracker.setOnComplete(stack -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> tryThinkHovered(mc, stack));
        });
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        AskService.INSTANCE.warmupAsync();
    }

    /** Third-party AskTool registration over the shared game bus (Scope Y). */
    public static void onAskToolRegister(AskToolRegisterEvent event) {
        if (event == null || event.registration() == null || event.registration().tool() == null) {
            return;
        }
        RegistrationStatus status = AskToolLoop.INSTANCE.registerExternal(event.registration().tool());
        if (status == RegistrationStatus.OK_STORED_NOT_ALLOWLISTED) {
            PackAiMod.LOGGER.info("Pack AI: registered external AskTool '{}' (stored; not schema/exec-visible until Scope X)",
                    event.registration().tool().name());
        } else {
            PackAiMod.LOGGER.warn("Pack AI: external AskTool registration rejected for '{}': {}",
                    event.registration().tool().name(), status);
        }
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_AI.get());
        event.register(THINK_JEI.get());
        event.register(FOCUS_ASK.get());
        event.register(ROUTE_LIBRARY.get());
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        AiClientCommands.register(event.getDispatcher());
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        AskService.INSTANCE.warmupAsync();
        ItemIndex.INSTANCE.ensureAsync();
        GuidebookIndex.INSTANCE.ensureAsync();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameDirectory != null) {
                com.skps9.packai.logic.AskTrace.purgeRetention(mc.gameDirectory.toPath());
            }
        } catch (Throwable ignored) {
            // never block login
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        GameContextCollector.resetFingerprintCache();
        ItemIndex.INSTANCE.invalidate();
        GuidebookIndex.INSTANCE.invalidate();
        ChatSession.clear();
        ThinkHoldTracker.reset();
        TooltipHover.clear();
        ObtainGraphRuntime.INSTANCE.clear();
        JeiCategoryIndex.INSTANCE.clear();
    }

    private static void onRecipesUpdated(RecipesUpdatedEvent event) {
        ObtainGraphRuntime.INSTANCE.onRecipesUpdated(event.getRecipeManager());
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        ObtainGraphRuntime.INSTANCE.onTagsUpdated(event);
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        ObtainGraphRuntime.INSTANCE.clear();
        JeiCategoryIndex.INSTANCE.clear();
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        AutoTestHarness.tick();
        RenderStallProbe.reset();
        RenderStallProbe.beginGc();
        RenderStallProbe.t0("graph_tick");
        try {
            ObtainGraphRuntime.INSTANCE.tick();
            JeiCategoryIndex.INSTANCE.tick();
        } finally {
            RenderStallProbe.end("graph_tick");
        }
        Minecraft mc = Minecraft.getInstance();
        boolean focusConsumedThisTick = false;
        while (OPEN_AI.get().consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new AiAssistantScreen());
                focusConsumedThisTick = true;
            }
        }
        while (ROUTE_LIBRARY.get().consumeClick()) {
            if (mc.player != null) {
                mc.setScreen(new RouteLibraryScreen());
            }
        }
        if (mc.player != null && mc.screen == null) {
            while (FOCUS_ASK.get().consumeClick()) {
                if (focusConsumedThisTick) {
                    continue;
                }
                focusConsumedThisTick = true;
                FocusAsk.askFromWorld(mc, "focus");
            }
        }
        if (mc.player != null && mc.screen != null) {
            ThinkHoldTracker.setLocked(ChatSession.isBusy());
            ItemStack hovered = TooltipHover.current();
            if (hovered.isEmpty()) {
                hovered = JeiTargetResolver.hoveredItem(mc);
            }
            if (!hovered.isEmpty()) {
                ThinkHoldTracker.updateHovered(hovered);
            }
            ThinkHoldTracker.tick(thinkKeyHeld(mc));
        } else {
            ThinkHoldTracker.reset();
            TooltipHover.clear();
        }
    }

    /** Re-clip within pick reach for the live BlockState (probe already decided BLOCK). */
    private static BlockState resolveLookedBlock(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.gameMode == null) {
            return null;
        }
        double pick = mc.gameMode.getPickRange();
        var eye = mc.player.getEyePosition(1.0F);
        var view = mc.player.getViewVector(1.0F);
        var bh = mc.level.clip(new net.minecraft.world.level.ClipContext(
                eye,
                eye.add(view.scale(pick)),
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                mc.player));
        if (bh == null || bh.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            return null;
        }
        return mc.level.getBlockState(bh.getBlockPos());
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
                SystemToast.SystemToastIds.TUTORIAL_HINT,
                Component.translatable("packai.screen.title"),
                Component.translatable(key));
    }

    public static AskService askService() {
        return AskService.INSTANCE;
    }
}
