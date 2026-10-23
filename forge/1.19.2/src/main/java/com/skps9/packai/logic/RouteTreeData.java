package com.skps9.packai.logic;

import java.util.List;

/** Flat pre-order tree for a saved obtain route (derived; not persisted). */
public final class RouteTreeData {
    public static final int MAX_NODES = 4096;

    public enum Status {
        OK,
        CYCLE,
        NOT_IN_ROUTE
    }

    public record Node(
            String itemId,
            int parentIndex,
            int depth,
            int count,
            Status status,
            int stepIndex) {}

    public record Tree(List<Node> nodes) {}

    private RouteTreeData() {}
}
