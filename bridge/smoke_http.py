#!/usr/bin/env python3
"""Live HTTP smoke test against a running Bridge on 127.0.0.1:8765."""

from __future__ import annotations

import json
import tempfile
import urllib.error
import urllib.request
from pathlib import Path


def post(url: str, body: dict) -> dict:
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main() -> None:
    try:
        health = urllib.request.urlopen("http://127.0.0.1:8765/health", timeout=5).read().decode()
    except urllib.error.URLError as e:
        raise SystemExit(f"Bridge not running: {e}\nStart with: cd bridge && python main.py") from e
    print("health:", health)

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        (root / "kubejs" / "server_scripts").mkdir(parents=True)
        (root / "config" / "ftbquests" / "chapters" / "intro").mkdir(parents=True)
        (root / "kubejs" / "server_scripts" / "alloy.js").write_text(
            "event.shapeless('create:andesite_alloy', ['minecraft:iron_nugget', 'minecraft:andesite'])\n",
            encoding="utf-8",
        )
        (root / "config" / "ftbquests" / "chapters" / "intro" / "q.snbt").write_text(
            'title: "Make Alloy"\ndescription: "Craft create:andesite_alloy"\n',
            encoding="utf-8",
        )

        body = {
            "question": "create andesite alloy how",
            "language": "zh-TW",
            "context": {
                "gameDirectory": str(root),
                "modIds": ["minecraft", "neoforge", "kubejs", "create", "ftbquests"],
                "heldItem": {"id": "create:andesite_alloy", "empty": False},
            },
        }
        r = post("http://127.0.0.1:8765/v1/ask", body)
        print("questGuided:", r.get("questGuided"))
        print("used_llm:", r.get("used_llm"))
        print("policy:", r.get("policy"))
        print("--- answer (first lines) ---")
        print("\n".join((r.get("answer") or "").splitlines()[:10]))

        body2 = {
            "question": "任務書好像不對",
            "language": "zh-TW",
            "context": {
                "gameDirectory": str(root),
                "modIds": ["minecraft", "neoforge", "kubejs", "create", "ftbquests"],
                "questOverride": True,
                "heldItem": {"id": "create:andesite_alloy", "empty": False},
            },
        }
        r2 = post("http://127.0.0.1:8765/v1/ask", body2)
        print("override questGuided:", r2.get("questGuided"))
        print("--- override answer head ---")
        print("\n".join((r2.get("answer") or "").splitlines()[:8]))

        assert r.get("questGuided") is True, r
        assert "任務導引" in (r.get("answer") or ""), r.get("answer")
        assert r2.get("questGuided") is False, r2
        print("SMOKE OK")


if __name__ == "__main__":
    main()
