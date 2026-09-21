package com.skps9.packai.logic;

import java.util.List;

public final class GapPanelCapCheck {
    private GapPanelCapCheck() {}

    public static void main(String[] args) {
        List<String> three = AskEngine.gapPanelLines(
                List.of("L1", "L2", "L3"),
                List.of("U1", "U2", "U3"),
                List.of("O1", "O2", "O3"),
                "zh_cn");
        assert three.size() == 3 : three;
        assert three.get(2).equals(ReplyLang.infoGapMore("zh_cn", "7")) : three;

        List<String> five = AskEngine.gapPanelLines(
                List.of("L1", "L2", "L3", "L4", "L5"),
                List.of(),
                List.of(),
                "zh_cn");
        assert five.size() == 3 : five;
        assert five.get(2).equals(ReplyLang.infoGapMore("zh_cn", "3")) : five;

        List<String> order = AskEngine.gapPanelLines(
                List.of("L1", "L2"),
                List.of("U1"),
                List.of(),
                "zh_cn");
        assert order.size() == 3 : order;
        assert "L1".equals(order.get(0)) : order;
        assert "L2".equals(order.get(1)) : order;
        assert "U1".equals(order.get(2)) : order;
        assert !order.get(2).equals(ReplyLang.infoGapMore("zh_cn", "0")) : order;

        List<String> empty = AskEngine.gapPanelLines(List.of(), List.of(), List.of(), "zh_cn");
        assert empty.size() == 0 : empty;

        List<String> one = AskEngine.gapPanelLines(List.of("L1"), List.of(), List.of(), "zh_cn");
        assert one.size() == 1 : one;
        assert "L1".equals(one.get(0)) : one;

        System.out.println("GapPanelCapCheck OK");
    }
}
