package com.skps9.packai.logic;

import com.skps9.packai.api.AskTool;
import com.skps9.packai.api.AskToolArgs;
import com.skps9.packai.client.jei.AskJeiClient;

import net.minecraft.world.item.ItemStack;

/** Thin wrapper: {@link com.skps9.packai.client.jei.JeiLookup#summarize} on the client thread. */
public final class JeiLookupAskTool implements AskTool {
    @Override
    public String name() {
        return "jei_lookup";
    }

    @Override
    public String description() {
        return "JEI recipes/uses/catalysts. dump_level=SLIM|OUTPUT|INFO. "
                + "INFO = JEI Information/信息 pages (page text + related item ids). "
                + "Call dump_level=INFO for 取得/用途 when the item has 信息 tabs. "
                + "jei_info_use = how to use (other-output carry-X-to-get-Y = use of X, not obtain of X). "
                + "jei_info_acquire = how to get. If INFO returned text, never write 未标明 / does not specify.";
    }

    @Override
    public String argsSchemaJson() {
        return "{\"type\":\"object\",\"properties\":{\"item\":{\"type\":\"string\"},\"variant_keys\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"dump_level\":{\"type\":\"string\"},\"query\":{\"type\":\"string\"},\"card_index\":{\"type\":\"string\"}},\"required\":[\"item\"],\"additionalProperties\":false}";
    }

    @Override
    public String run(AskToolArgs args) {
        AskToolEnv env = AskToolEnv.current();
        ItemStack stack = env == null ? ItemStack.EMPTY : env.stack;
        if (args != null && args.itemId != null && !args.itemId.isBlank()) {
            ItemStack named = ItemResolver.stackFromId(args.itemId);
            if (!named.isEmpty()) {
                stack = named;
            }
        }
        AskToolContext.JeiDumpLevel level = AskToolContext.parseJeiDumpLevel(
                args.dumpLevel == null || args.dumpLevel.isBlank() ? "OUTPUT" : args.dumpLevel);
        try {
            String text = AskJeiClient.summarize(stack, level, args.deadlineMs);
            return AskToolContext.clipChars(text, level.outputBudget());
        } catch (Throwable t) {
            return "";
        }
    }
}
