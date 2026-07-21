package com.skps9.packai.client.context;

import java.util.Locale;

import net.minecraft.client.player.LocalPlayer;
import net.neoforged.fml.ModList;

/**
 * Rough season hints for Serene Seasons / similar (client-side day cycle).
 */
public final class SeasonContext {
    private static final String[] SS_SUB = {
            "初春", "仲春", "晚春", "初夏", "仲夏", "晚夏",
            "初秋", "仲秋", "晚秋", "初冬", "仲冬", "晚冬"
    };

    private SeasonContext() {}

    /** Plain-text block for LLM, or empty. */
    public static String summary(LocalPlayer player) {
        if (player == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (ModList.get().isLoaded("sereneseasons")) {
            long day = player.level().getDayTime() / 24000L;
            int sub = (int) ((day / 8L) % 12L);
            String subName = SS_SUB[Math.floorMod(sub, SS_SUB.length)];
            sb.append("【季節】Serene Seasons 估算：").append(subName)
                    .append("（第 ").append(day).append(" 天；種植請對照遊戲內季節日曆）\n");
        }
        if (ModList.get().isLoaded("farmersdelight")) {
            sb.append("【季節】Farmer's Delight 已載入；部分作物有季節限制，請結合當前季節說明。\n");
        }
        return sb.toString().trim();
    }
}
