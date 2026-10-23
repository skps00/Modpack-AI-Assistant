package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.skps9.packai.client.routes.RouteStoreCodec;
import com.skps9.packai.client.routes.RouteStoreCodec.SavedRoute;

/**
 * Runs the real tree builder over a real saved-routes JSON file (read-only).
 * Env {@code PACKAI_ROUTES_FIXTURE} points at e.g. packai/routes/New_World.json;
 * when unset the check passes with a SKIP line so it stays green on machines without fixtures.
 */
public final class RouteTreeFixtureCheck {
    private RouteTreeFixtureCheck() {}

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    public static void main(String[] args) throws Exception {
        String env = System.getenv("PACKAI_ROUTES_FIXTURE");
        if (env == null || env.isBlank()) {
            System.out.println("check RouteTreeFixtureCheck SKIP (PACKAI_ROUTES_FIXTURE unset)");
            return;
        }
        Path file = Path.of(env);
        if (!Files.isRegularFile(file)) {
            fail("fixture not found: " + file);
        }
        String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        List<SavedRoute> routes = RouteStoreCodec.decode(json);
        if (routes.isEmpty()) {
            fail("fixture decoded to zero routes");
        }
        // Pick the deepest saved route: it exercises multi-level linking + duplicate inputs.
        SavedRoute best = routes.get(0);
        for (SavedRoute r : routes) {
            if (r.steps().size() > best.steps().size()) {
                best = r;
            }
        }
        RouteTreeData.Tree tree = RouteTreeBuilder.build(best.itemId(), best.steps());
        List<RouteTreeData.Node> nodes = tree.nodes();
        System.out.println("fixture route=" + best.itemId() + " steps=" + best.steps().size()
                + " nodes=" + nodes.size());
        for (int i = 0; i < Math.min(nodes.size(), 8); i++) {
            RouteTreeData.Node n = nodes.get(i);
            System.out.println("  #" + i + " id=" + n.itemId() + " parent=" + n.parentIndex()
                    + " depth=" + n.depth() + " count=" + n.count() + " status=" + n.status()
                    + " step=" + n.stepIndex());
        }
        if (nodes.isEmpty()) {
            fail("tree has no root node for " + best.itemId());
        }
        if (!best.itemId().equals(nodes.get(0).itemId())) {
            fail("root must be the route item, got " + nodes.get(0).itemId());
        }
        if (nodes.get(0).depth() != 0 || nodes.get(0).parentIndex() != -1) {
            fail("root depth/parent wrong: " + nodes.get(0));
        }
        // Every non-root node must point at an existing, shallower parent.
        for (int i = 1; i < nodes.size(); i++) {
            RouteTreeData.Node n = nodes.get(i);
            if (n.parentIndex() < 0 || n.parentIndex() >= nodes.size()) {
                fail("node #" + i + " has out-of-range parent " + n.parentIndex());
            }
            if (n.depth() != nodes.get(n.parentIndex()).depth() + 1) {
                fail("node #" + i + " depth " + n.depth() + " != parent depth + 1");
            }
        }
        // At least one node must carry a count > 1 when the fixture has duplicated inputs,
        // otherwise the count chain is dead (this is the regression that matters).
        boolean anyMulti = nodes.stream().anyMatch(n -> n.count() > 1);
        boolean fixtureHasDuplicates = best.steps().stream().anyMatch(s -> {
            List<String> in = s.inputs();
            return in != null && in.size() != new java.util.HashSet<>(in).size();
        });
        System.out.println("fixture duplicateInputs=" + fixtureHasDuplicates + " anyCount>1=" + anyMulti);
        if (fixtureHasDuplicates && !anyMulti) {
            fail("fixture has duplicated inputs but no node count>1 -> count chain dead");
        }
        System.out.println("check RouteTreeFixtureCheck OK");
    }
}
