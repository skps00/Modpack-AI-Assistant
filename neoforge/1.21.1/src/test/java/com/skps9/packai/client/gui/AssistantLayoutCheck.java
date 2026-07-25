package com.skps9.packai.client.gui;

/**
 * Runnable layout math check (no Minecraft runtime).
 * Run: javac + java on this file alone, or mentally via gradle if wired later.
 */
public final class AssistantLayoutCheck {
    private AssistantLayoutCheck() {}

    /** Mirrors AiAssistantScreen.init geometry. */
    static int[] layout(int height, int questCount) {
        int btnH = 20;
        int btnGap = 4;
        int rows = 4;
        int bottomStack = btnH * rows + btnGap * (rows - 1) + 8;
        int inputY = height - bottomStack - 28;
        int n = Math.min(3, Math.max(0, questCount));
        int questStrip = n == 0 ? 0 : n * btnH + (n - 1) * 2 + 8;
        int answerBottom = inputY - 8 - questStrip;
        int firstQuestY = n == 0 ? -1 : inputY - questStrip;
        return new int[] {inputY, answerBottom, firstQuestY, questStrip};
    }

    public static void main(String[] args) {
        int h = 480;
        int[] a0 = layout(h, 0);
        int[] a1 = layout(h, 1);
        int[] a3 = layout(h, 3);
        // Input Y identical regardless of quest count
        assert a0[0] == a1[0] && a1[0] == a3[0] : "inputY must stay fixed";
        // Last button of n=3 ends 8px above input
        assert a3[2] + 3 * 20 + 2 * 2 == a3[0] - 8 : "quest strip must pack to input";
        // n=1 packs against input, not top of max strip
        assert a1[2] == a1[0] - 28 : "single quest should sit just above input";
        // No empty strip when no quests
        assert a0[3] == 0 && a0[1] == a0[0] - 8 : "no quest → answer reaches near input";
        System.out.println("AssistantLayoutCheck OK inputY=" + a0[0]);
    }
}
