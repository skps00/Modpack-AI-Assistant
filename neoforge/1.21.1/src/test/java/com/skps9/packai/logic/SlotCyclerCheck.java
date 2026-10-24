package com.skps9.packai.logic;

public final class SlotCyclerCheck {
    private SlotCyclerCheck() {}

    public static void main(String[] args) {
        assert SlotCycler.index(0, 4) == 0;
        assert SlotCycler.index(19, 4) == 0;
        assert SlotCycler.index(20, 4) == 1;
        assert SlotCycler.index(80, 4) == 0;
        assert SlotCycler.index(5, 0) == 0;
        System.out.println("check SlotCyclerCheck OK");
    }
}
