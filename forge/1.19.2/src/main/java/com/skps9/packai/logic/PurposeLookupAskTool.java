package com.skps9.packai.logic;

import java.util.List;

/** On-demand PURPOSE block. FACT pin stays when tools off / unused. */
public final class PurposeLookupAskTool implements AskTool {
    @Override
    public String name() {
        return "purpose_lookup";
    }

    @Override
    public String run(AskToolArgs args) {
        AskToolEnv env = AskToolEnv.current();
        String tip = env == null || env.purposeTooltip == null ? "" : env.purposeTooltip;
        try {
            String block = AskPurposeContext.buildPurposeBlock(tip, List.of(), null);
            return block == null ? "" : AskToolContext.clipChars(block, 1600);
        } catch (Throwable t) {
            return "";
        }
    }
}
