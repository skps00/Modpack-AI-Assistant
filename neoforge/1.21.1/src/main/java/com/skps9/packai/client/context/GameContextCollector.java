package com.skps9.packai.client.context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * Builds slim JSON context. Full modIds only when fingerprint changes.
 */
public final class GameContextCollector {
    private static String lastFingerprint = "";

    private GameContextCollector() {}

    /**
     * @param includeHotbar when true (e.g. "next step" button), attach hotbar summary
     */
    public static Map<String, Object> collect(boolean includeHotbar) {
        Map<String, Object> root = new LinkedHashMap<>();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        List<String> modIds = new ArrayList<>();
        for (IModInfo info : ModList.get().getMods()) {
            modIds.add(info.getModId());
        }
        modIds.sort(String::compareTo);
        String fingerprint = sha1Short(String.join(",", modIds));
        root.put("modIdsFingerprint", fingerprint);
        root.put("modCount", modIds.size());
        if (!fingerprint.equals(lastFingerprint)) {
            root.put("modIds", modIds);
            lastFingerprint = fingerprint;
        }

        root.put("gameDirectory", mc.gameDirectory.getAbsolutePath());

        if (player == null) {
            root.put("inWorld", false);
            return root;
        }

        root.put("inWorld", true);
        root.put("heldItem", itemInfo(player.getMainHandItem(), player));
        root.put("offhandItem", itemInfo(player.getOffhandItem(), player));
        root.put("dimension", player.level().dimension().location().toString());

        if (includeHotbar) {
            List<Map<String, Object>> hotbar = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    hotbar.add(itemInfo(stack, player));
                }
            }
            root.put("hotbar", hotbar);
        }
        return root;
    }

    public static Map<String, Object> collect() {
        return collect(false);
    }

    /** Force next ask to resend full modIds (e.g. after reconnect). */
    public static void resetFingerprintCache() {
        lastFingerprint = "";
    }

    private static Map<String, Object> itemInfo(ItemStack stack, LocalPlayer player) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (stack.isEmpty()) {
            m.put("empty", true);
            return m;
        }
        m.put("empty", false);
        m.put("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        m.put("count", stack.getCount());
        // Short hover name for matching / LLM identity (not full tooltip dump).
        m.put("name", stack.getHoverName().getString());
        // Expanded tooltip text (includes Shift/Ctrl-gated lines via TooltipCapture).
        m.put("displayName", TooltipCapture.capture(stack, player));
        return m;
    }

    private static String sha1Short(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] dig = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
