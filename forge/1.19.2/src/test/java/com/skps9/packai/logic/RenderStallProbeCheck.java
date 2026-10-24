package com.skps9.packai.logic;

/** Headless checks for {@link RenderStallProbe}. Run with -ea. */
public final class RenderStallProbeCheck {
    private RenderStallProbeCheck() {}

    public static void main(String[] args) {
        resetEmpty();
        endRecordsPhase();
        gcNullBeans();
        RenderStallProbe.clearGcBeansSupplierForTest();
        System.out.println("RenderStallProbeCheck OK");
    }

    private static void resetEmpty() {
        RenderStallProbe.t0("warm");
        RenderStallProbe.end("warm");
        RenderStallProbe.reset();
        assert RenderStallProbe.phases().isEmpty() : RenderStallProbe.phases();
    }

    private static void endRecordsPhase() {
        RenderStallProbe.reset();
        RenderStallProbe.t0("a");
        RenderStallProbe.end("a");
        String phases = RenderStallProbe.phases();
        assert phases.contains("a=") : phases;
    }

    private static void gcNullBeans() {
        RenderStallProbe.setGcBeansSupplierForTest(() -> null);
        RenderStallProbe.beginGc();
        assert "count=-1,ms=-1".equals(RenderStallProbe.gc()) : RenderStallProbe.gc();
    }
}
