# -*- coding: utf-8 -*-
"""AskNameResolve fixture: 最初的骑士 → somebosses id; summon miss has no web ritual."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SIDES = (
    "forge/1.19.2/src/main/java/com/skps9/packai",
    "neoforge/1.21.1/src/main/java/com/skps9/packai",
)
TEST_SIDES = (
    "forge/1.19.2/src/test/java/com/skps9/packai/logic",
    "neoforge/1.21.1/src/test/java/com/skps9/packai/logic",
)
LANG_TREES = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "resources" / "assets" / "packai" / "lang",
    ROOT / "neoforge" / "1.21.1" / "src" / "main" / "resources" / "assets" / "packai" / "lang",
)


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    for side in SIDES:
        name = read(f"{side}/logic/AskNameResolve.java")
        assert "nameCore" in name
        assert "resolveId" in name
        assert "relatedHintIds" in name
        assert "_spawn_egg" in name
        assert "knight_garent" not in name
        assert "cataclysm" not in name.lower()
        acq = read(f"{side}/logic/PackIndex.java")
        assert 'q.contains("召唤")' in acq
        assert 'q.contains("召喚")' in acq
        engine = read(f"{side}/logic/AskEngine.java")
        assert "skipWebForSummon" in engine
        assert "shouldPinSummonMiss" in engine
        jei = read(f"{side}/client/jei/JeiTargetResolver.java")
        assert "resolveByDisplayName" in jei
        assert "AskNameResolve.nameCore" in jei

    for test in TEST_SIDES:
        chk = read(f"{test}/AskNameResolveCheck.java")
        assert "最初的骑士" in chk
        assert "somebosses:knight_garent" in chk
        assert "necronomicon" in chk
        assert "cataclysm" in chk

    import json

    for tree in LANG_TREES:
        for loc in ("en_us.json", "zh_cn.json", "zh_tw.json"):
            data = json.loads((tree / loc).read_text(encoding="utf-8"))
            miss = data["packai.reply.summon_index_miss"]
            low = miss.lower()
            assert "necronomicon" not in low
            assert "cataclysm" not in low
            assert "pyramid" not in low
            assert "web" in low or "网搜" in miss or "網搜" in miss

    print("check_ask_name_resolve OK")


if __name__ == "__main__":
    main()
