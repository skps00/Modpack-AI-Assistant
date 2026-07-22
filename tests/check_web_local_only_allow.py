#!/usr/bin/env python3
"""local_only policy still allows web; local wins on conflict."""


def allow_web(web_enabled: bool, policy: str) -> bool:
    # After change: only master switch gates search (not local_only).
    return web_enabled


def web_header_kind(policy: str) -> str:
    if policy == "local_only":
        return "local_override"
    if policy == "mixed":
        return "mixed"
    return "strict"


def main() -> None:
    assert allow_web(True, "local_only")
    assert allow_web(True, "mixed")
    assert allow_web(True, "online_ok")
    assert not allow_web(False, "local_only")
    assert web_header_kind("local_only") == "local_override"
    assert web_header_kind("mixed") == "mixed"
    assert web_header_kind("online_ok") == "strict"
    print("ok web_local_only_allow")


if __name__ == "__main__":
    main()
