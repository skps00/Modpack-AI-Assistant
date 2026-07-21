"""Optional web search via Tavily (preferred) or Serper."""

from __future__ import annotations

import os

import httpx

_client: httpx.Client | None = None


def _http() -> httpx.Client:
    global _client
    if _client is None:
        _client = httpx.Client(timeout=20.0)
    return _client


def build_search_query(question: str, focus_mods: list[str], held_item: dict | None) -> str:
    parts = [question.strip()]
    held = held_item or {}
    item_id = str(held.get("id") or "")
    if item_id and item_id != "empty":
        parts.append(item_id)
    for mid in focus_mods[:3]:
        if mid and mid not in parts:
            parts.append(mid)
    parts.append("Minecraft mod")
    return " ".join(p for p in parts if p)


def web_search(
    question: str,
    focus_mods: list[str],
    held_item: dict | None,
    max_results: int = 5,
) -> list[dict[str, str]]:
    """
    Return up to max_results of {title, url, snippet}.
    Empty list if no API key or request fails (never raises to caller).
    """
    query = build_search_query(question, focus_mods, held_item)
    tavily = os.getenv("TAVILY_API_KEY", "").strip()
    serper = os.getenv("SERPER_API_KEY", "").strip()
    try:
        if tavily:
            return _tavily(query, tavily, max_results)
        if serper:
            return _serper(query, serper, max_results)
    except Exception:  # noqa: BLE001
        return []
    return []


def _tavily(query: str, api_key: str, max_results: int) -> list[dict[str, str]]:
    r = _http().post(
        "https://api.tavily.com/search",
        json={
            "api_key": api_key,
            "query": query,
            "search_depth": "basic",
            "max_results": max_results,
        },
    )
    r.raise_for_status()
    data = r.json()
    out: list[dict[str, str]] = []
    for item in data.get("results") or []:
        out.append(
            {
                "title": str(item.get("title") or ""),
                "url": str(item.get("url") or ""),
                "snippet": str(item.get("content") or item.get("snippet") or "")[:400],
            }
        )
        if len(out) >= max_results:
            break
    return out


def _serper(query: str, api_key: str, max_results: int) -> list[dict[str, str]]:
    r = _http().post(
        "https://google.serper.dev/search",
        headers={"X-API-KEY": api_key, "Content-Type": "application/json"},
        json={"q": query, "num": max_results},
    )
    r.raise_for_status()
    data = r.json()
    out: list[dict[str, str]] = []
    for item in data.get("organic") or []:
        out.append(
            {
                "title": str(item.get("title") or ""),
                "url": str(item.get("link") or ""),
                "snippet": str(item.get("snippet") or "")[:400],
            }
        )
        if len(out) >= max_results:
            break
    return out


def format_web_warning(answer: str) -> str:
    prefix = "【警告：整合包可能已魔改，線上資訊僅供參考】\n"
    if answer.startswith(prefix):
        return answer
    return prefix + answer
