package com.skps9.packai.logic;

import java.util.List;

import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;

import net.minecraft.world.item.ItemStack;

/** On-demand [REPAIR_INFO] from Item#isValidRepairItem best-effort scan. */
public final class RepairLookupAskTool implements AskTool {
    @Override
    public String name() {
        return "repair_lookup";
    }

    @Override
    public String description() {
        return "Anvil repair materials for this item (iron/gold/diamond etc). "
                + "item=mod:id optional; omit/empty = current focus/held. "
                + "Call when asked how to repair/fix/restore durability. "
                + "Scan is best-effort — if empty, the item may still have mod-specific repair paths "
                + "(quest/anvil recipes), do not claim 'cannot be repaired'.";
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
                        "Pack AI repair_lookup unresolved itemId={}", args.itemId);
                return "";
            }
            stack = named;
        }
        String replyLang = args == null ? "" : args.lang;
        try {
            if (stack == null || stack.isEmpty()) {
                return "";
            }
            List<String> lines = AnvilRepairHint.repairMaterials(stack, replyLang);
            if (lines == null || lines.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder("[REPAIR_INFO] 可用 ");
            sb.append(String.join("、", lines));
            sb.append(" 喺鐵砧修復（材料修復）。\n");
            sb.append(String.join("\n", lines));
            return AskToolContext.clipChars(sb.toString(), 1600);
        } catch (Throwable t) {
            com.skps9.packai.PackAiMod.LOGGER.warn(
                    "Pack AI repair_lookup error for {}: {}",
                    args == null ? "?" : args.itemId,
                    t.toString());
            return "";
        }
    }
}
