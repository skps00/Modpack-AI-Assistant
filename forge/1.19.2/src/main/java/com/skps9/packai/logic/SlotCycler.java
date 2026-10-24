package com.skps9.packai.logic;

public final class SlotCycler {
    public static final int TICKS_PER_STEP = 20;

    private SlotCycler() {}

    /** JEI CycleTicker.tick(): tick>=20 → tick=0,index++ ; index = (tick/20) % size */
    public static int index(long ticks, int size) {
        if (size <= 0) {
            return 0;
        }
        long step = ticks / TICKS_PER_STEP;
        return (int) Math.floorMod(step, (long) size);
    }
}
