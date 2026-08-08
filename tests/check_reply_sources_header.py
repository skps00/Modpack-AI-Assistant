#!/usr/bin/env python3
"""ReplySources / RecipeEmbed must recognize zh_tw, zh_cn, and en source headers."""
import re

HEADER = re.compile(r"(?m)(【來源】|【来源】|\[Sources\])")


def has_header(text: str) -> bool:
    return bool(HEADER.search(text or ""))


def main() -> None:
    assert has_header("ok\n\n【來源】JEI")
    assert has_header("ok\n\n【来源】JEI")
    assert has_header("ok\n\n[Sources] JEI")
    assert not has_header("ok\n\nSources: JEI")  # bare word not enough
    # ensure-style: already has zh_cn → do not treat as missing
    assert has_header("答案\n\n【来源】任务书")
    print("check_reply_sources_header OK")


if __name__ == "__main__":
    main()
