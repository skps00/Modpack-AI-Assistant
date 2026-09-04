#!/usr/bin/env python3
"""ask_player dead code removal + registerExternal + api/ AskTool source guards (Forge + Neo)."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOGIC = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic",
    ROOT / "neoforge" / "1.21.1" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic",
)
API = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "api",
    ROOT / "neoforge" / "1.21.1" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "api",
)

BUILTINS = (
    "JeiLookupAskTool.java",
    "AcquireAskTool.java",
    "GuideFetchAskTool.java",
    "QuestFetchAskTool.java",
    "ConsumeUseAskTool.java",
    "ShowRecipeCardAskTool.java",
    "PurposeLookupAskTool.java",
    "ToolBuildAskTool.java",
    "TetraUseAskTool.java",
    "WorldgenLookupAskTool.java",
)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def check_source(logic: Path, api: Path) -> None:
    # 1. ask_player dead code is gone
    assert not (logic / "AskPlayerAskTool.java").is_file(), f"{logic}: AskPlayerAskTool.java must be deleted"
    engine = read(logic / "AskEngine.java")
    assert "AskPlayerAskTool" not in engine, f"{logic}: AskEngine still registers ask_player"

    # 2. AskResult no longer carries player-ask fields
    result = read(logic / "AskResult.java")
    assert "boolean needsPlayer" not in result, f"{logic}: needsPlayer must be removed"
    assert "String pendingQuestion" not in result, f"{logic}: pendingQuestion must be removed"
    assert "withNeedsPlayer" not in result, f"{logic}: withNeedsPlayer must be removed"

    # 3. register() keeps its ALLOWLIST early-return (keep-gate)
    loop = read(logic / "AskToolLoop.java")
    reg_block = loop[loop.index("public void register(AskTool tool)"):]
    assert "ALLOWLIST.contains(tool.name())" in reg_block.split("public RegistrationStatus registerExternal")[0], (
        f"{logic}: register() must keep ALLOWLIST gate"
    )

    # 4. registerExternal exists, returns status, does its own validated put (not via register())
    assert "public RegistrationStatus registerExternal(AskTool tool)" in loop, f"{logic}: registerExternal missing"
    ext = loop[loop.index("public RegistrationStatus registerExternal(AskTool tool)"):]
    assert "RegistrationStatus.REJECT_DUP" in ext
    assert "RegistrationStatus.REJECT_RESERVED" in ext
    assert "RegistrationStatus.REJECT_BAD_SCHEMA" in ext
    assert "RegistrationStatus.OK_STORED_NOT_ALLOWLISTED" in ext
    assert "registry.put(name, tool)" in ext, f"{logic}: registerExternal must store directly"
    assert "register(tool)" not in ext.replace("registerExternal", ""), f"{logic}: registerExternal must NOT delegate to register()"

    # 5. RegistrationStatus enum file exists in api/ with the 4 values
    status = read(api / "RegistrationStatus.java")
    for v in ("OK_STORED_NOT_ALLOWLISTED", "REJECT_DUP", "REJECT_RESERVED", "REJECT_BAD_SCHEMA"):
        assert v in status, f"{api}: RegistrationStatus missing {v}"

    # 6. AskTool in api/ exposes abstract schema metadata (no defaults)
    tool_src = read(api / "AskTool.java")
    assert "package com.skps9.packai.api;" in tool_src
    assert "String description();" in tool_src
    assert "String argsSchemaJson();" in tool_src
    assert "default String description()" not in tool_src
    assert "default String argsSchemaJson()" not in tool_src

    # 7. Built-ins import api AskTool and override schema methods
    for name in BUILTINS:
        src = read(logic / name)
        assert "import com.skps9.packai.api.AskTool" in src, f"{logic / name}: missing api AskTool import"
        assert "public String description()" in src, f"{logic / name}: missing description()"
        assert "public String argsSchemaJson()" in src, f"{logic / name}: missing argsSchemaJson()"


def main() -> None:
    for logic, api in zip(LOGIC, API):
        check_source(logic, api)
    print("check_ask_player_tool OK")


if __name__ == "__main__":
    main()
