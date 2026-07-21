"""OpenAI-compatible chat; keys stay in .env never in the mod jar."""

from __future__ import annotations

import json
import os
from typing import Any

import httpx

_client: httpx.Client | None = None


def _http() -> httpx.Client:
    global _client
    if _client is None:
        _client = httpx.Client(timeout=60.0)
    return _client


def ask_llm(
    question: str,
    language: str,
    held_item: dict[str, Any],
    focus_mods: list[str],
    policy: str,
    graph_facts: list[str] | None = None,
    sources: list[str] | None = None,
    web_hits: list[dict[str, str]] | None = None,
    soft_fallback: bool = False,
    quest_override: bool = False,
    quest_conflict: bool = False,
    short_excerpt: str = "",
) -> str:
    api_key = os.getenv("OPENAI_API_KEY", "")
    base = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/")
    model = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
    web_hits = web_hits or []
    graph_facts = graph_facts or []
    sources = sources or []

    style = (
        "主文必須白話（作法／材料／步驟），禁止把整段腳本或 JSON 當答案主體。"
        "來源檔名或 URL 放在文末【來源】。不確定就明說。"
    )
    if quest_override:
        policy_rules = (
            "玩家表示任務書有誤：不要只叫他再看同一任務；依 graphFacts／本地證據回答，"
            "可參考 webHits；標明任務書可能有誤。"
        )
    elif quest_conflict:
        policy_rules = "任務可能過時且與本地魔改衝突：說明衝突，以本地魔改為準，並可提醒任務書章節。"
    elif policy == "local_only" and not soft_fallback:
        policy_rules = "policy=local_only：以本地／圖事實為準，禁止用通用 wiki 覆蓋。"
    elif soft_fallback:
        policy_rules = "soft_fallback：本地不足才用 webHits，開頭提醒整合包可能已魔改。"
    else:
        policy_rules = "policy=online_ok：可合併 graphFacts 與 webHits；衝突時以本地為準。"

    system = (
        f"你是 Minecraft 整合包助手。回覆語言：{language}。"
        f" {style} {policy_rules}"
        f" 聚焦模組: {', '.join(focus_mods) or '(無)'}。"
    )
    user_payload = {
        "question": question,
        "heldItem": held_item,
        "focusMods": focus_mods,
        "policy": policy,
        "questOverride": quest_override,
        "questConflict": quest_conflict,
        "graphFacts": graph_facts[:40],
        "sources": sources[:8],
        "webHits": web_hits[:5],
        "shortExcerpt": (short_excerpt or "")[:120],
    }
    user = json.dumps(user_payload, ensure_ascii=False)

    if not api_key:
        from plainify import friendly_offline

        facts = "\n".join(graph_facts[:12]) or "(無圖事實)"
        return (
            f"[離線模式：未設定 OPENAI_API_KEY]\n"
            f"{friendly_offline(sources, question)}\n"
            f"圖事實預覽：\n{facts}"
        )

    url = f"{base}/chat/completions"
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
        "temperature": 0.2,
    }
    headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
    try:
        r = _http().post(url, headers=headers, json=body)
        r.raise_for_status()
        data = r.json()
        return data["choices"][0]["message"]["content"]
    except Exception as e:  # noqa: BLE001
        return f"LLM 呼叫失敗：{e}"
