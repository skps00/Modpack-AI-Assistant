package com.skps9.packai.logic;

import java.util.List;

import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** On-demand [ENCHANT_TABLE] from registry canEnchant. Pre-injection removed Wave 22. */
public final class EnchantLookupAskTool implements AskTool {
    @Override
    public String name() {
        return "enchant_lookup";
    }

    @Override
    public String description() {
        return "Registry enchants that canEnchant this item (book/anvil path). "
                + "item=mod:id optional; omit/empty = current focus/held.";
    }

    @Override
    public String argsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"item\":{\"type\":\"string\"}},\"additionalProperties\":false}";
    }

    @Override
    public String run(AskToolArgs args) {
        AskToolEnv env = AskToolEnv.current();
        ItemStack stack = env == null ? ItemStack.EMPTY : env.stack;
        if (args != null && args.itemId != null && !args.itemId.isBlank()) {
            ItemStack named = ItemResolver.stackFromId(args.itemId);
            if (named.isEmpty()) {
                com.skps9.packai.PackAiMod.LOGGER.warn(
                        "Pack AI enchant_lookup unresolved itemId={}", args.itemId);
                return "";
            }
            stack = named;
        }
        String replyLang = args == null ? "" : args.lang;
        try {
            if (stack == null || stack.isEmpty()) {
                return "";
            }
            List<String> lines = EnchantHint.registryTable(stack, replyLang);
            if (lines == null || lines.isEmpty()) {
                return "";
            }
            boolean modItem = isNonMinecraft(stack);
            String note = "（此物品走铁砧/附魔书途径；据注册表 canEnchant）";
            StringBuilder sb = new StringBuilder("[ENCHANT_TABLE] ");
            if (modItem) {
                sb.append(note).append('\n');
            }
            sb.append("此物品可用的附魔（附魔书/铁砧适用；据注册表数据）：\n");
            sb.append(String.join("\n", lines));
            return AskToolContext.clipChars(sb.toString(), 1600);
        } catch (Throwable t) {
            com.skps9.packai.PackAiMod.LOGGER.warn("Pack AI enchant_lookup error for {}: {}", args == null ? "?" : args.itemId, t.toString());
            return "";
        }
    }

    private static boolean isNonMinecraft(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return key != null && !"minecraft".equals(key.getNamespace());
        } catch (Throwable t) {
            return false;
        }
    }
}
