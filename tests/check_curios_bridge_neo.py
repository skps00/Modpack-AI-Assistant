"""Static checks: Neo Curios soft-dep mirrors Forge bridge pattern (not stub)."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NEO = ROOT / "neoforge" / "1.21.1"
BRIDGE = NEO / "src" / "main" / "java" / "com" / "skps9" / "packai" / "compat" / "CuriosBridge.java"
IMPL = NEO / "src" / "main" / "java" / "com" / "skps9" / "packai" / "compat" / "CuriosBridgeImpl.java"
GRADLE = NEO / "build.gradle"
TOML = NEO / "src" / "main" / "templates" / "META-INF" / "neoforge.mods.toml"
PROPS = NEO / "gradle.properties"


def main() -> None:
    bridge = BRIDGE.read_text(encoding="utf-8")
    assert "no-op stub" not in bridge
    assert "return false;" not in bridge  # old stub isLoaded()
    assert 'ModList.get().isLoaded("curios")' in bridge
    assert "CuriosBridgeImpl" in bridge
    assert "Class.forName" in bridge
    assert "appendSlotKeys" in bridge
    assert "stackAt" in bridge

    impl = IMPL.read_text(encoding="utf-8")
    assert "CuriosApi.getCuriosInventory" in impl
    assert "curios:" in impl
    assert "import top.theillusivec4.curios" in impl

    gradle = GRADLE.read_text(encoding="utf-8")
    assert "maven.theillusivec4.top" in gradle
    assert 'compileOnly "top.theillusivec4.curios:curios-neoforge:${curios_version}:api"' in gradle
    assert 'localRuntime "top.theillusivec4.curios' not in gradle

    props = PROPS.read_text(encoding="utf-8")
    assert "curios_version=" in props

    toml = TOML.read_text(encoding="utf-8")
    assert 'modId="curios"' in toml
    assert 'type="optional"' in toml

    print("check_curios_bridge_neo: OK")


if __name__ == "__main__":
    main()
