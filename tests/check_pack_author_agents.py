#!/usr/bin/env python3
"""Pack author AGENTS.md path + sanitize/truncate helpers."""

MAX_CHARS = 4000
CANDIDATES = [
    "config/packai/AGENTS.md",
    "config/packai/agents.md",
    "kubejs/packai/AGENTS.md",
    "kubejs/packai/agents.md",
    "packai/AGENTS.md",
]


def sanitize(raw: str | None) -> str:
    if not raw:
        return ""
    s = raw.replace("\x00", "").replace("\r\n", "\n").replace("\r", "\n")
    if s.startswith("\ufeff"):
        s = s[1:]
    return s.strip()


def truncate(text: str, limit: int = MAX_CHARS) -> str:
    if len(text) <= limit:
        return text
    return text[:limit].rstrip() + "\n…"


def pick_agents_file(existing: list[str]) -> str | None:
    have = {p.replace("\\", "/") for p in existing}
    for c in CANDIDATES:
        if c in have:
            return c
    return None


def main() -> None:
    assert sanitize("  hello\r\n") == "hello"
    assert sanitize("\ufeffx") == "x"
    body = "a" * (MAX_CHARS + 10)
    t = truncate(body)
    assert len(t) <= MAX_CHARS + 2
    assert t.endswith("…")

    assert pick_agents_file(["kubejs/packai/AGENTS.md", "config/packai/AGENTS.md"]) == "config/packai/AGENTS.md"
    assert pick_agents_file(["packai/AGENTS.md"]) == "packai/AGENTS.md"
    assert pick_agents_file([]) is None

    print("ok pack_author_agents")


if __name__ == "__main__":
    main()
