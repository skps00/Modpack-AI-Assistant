#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""M1e-v2: KubeJS reflection bridge static asserts (Forge-only)."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE = ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai"


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    bridge = read(
        "forge/1.19.2/src/main/java/com/skps9/packai/logic/KubeJsApiBridge.java"
    )
    scan = read(
        "forge/1.19.2/src/main/java/com/skps9/packai/logic/KubeJsMechanicScan.java"
    )
    cfg = read("forge/1.19.2/src/main/java/com/skps9/packai/config/PackAiConfig.java")
    harness = read(
        "forge/1.19.2/src/test/java/com/skps9/packai/logic/AskKubeJsBridgeCheck.java"
    )

    # ① no kubejs compile import in product code
    prod_java = list(FORGE.rglob("*.java"))
    for p in prod_java:
        text = p.read_text(encoding="utf-8")
        assert "import dev.latvian" not in text, p

    assert "class KubeJsApiBridge" in bridge
    assert "EventGroup" in bridge
    assert "extraEventContainers" in bridge
    assert "eventContainers" in bridge
    assert "extraId" in bridge
    assert "markStaleAndRebuild" not in bridge

    # ② config key
    assert "kubejsApiBridge" in cfg
    assert "KUBEJS_API_BRIDGE" in cfg
    assert "boolean kubejsApiBridge()" in cfg

    # ③ fallback branch + ask log modes
    assert 'mode=scan' in bridge or 'mode=" + LAST_MODE' in bridge
    assert 'noteAsk(0, "scan")' in scan
    assert 'noteAsk(hits.size(), "api")' in scan
    assert "source:kubejs(api)" in scan
    assert "factsFromBridgeHits" in scan

    assert "class AskKubeJsBridgeCheck" in harness
    assert "unavailableOk" in harness
    assert "askPathFastWhenUnavailable" in harness
    assert "fakeContainerExtractAndDedupe" in harness

    print("check_kubejs_bridge OK")


if __name__ == "__main__":
    main()
