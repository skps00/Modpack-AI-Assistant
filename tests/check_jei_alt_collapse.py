#!/usr/bin/env python3
"""OR-tag slot collapse — mirrors IngredientReqHints.commonTagId / collapseAlternatives."""

BROAD_NS = frozenset({"minecraft", "c", "forge", "neoforge"})


def _ns(tag: str) -> str:
    return tag.split(":", 1)[0] if ":" in tag else ""


def common_tag_id(group_tags: list[set[str]], tag_sizes: dict[str, int]) -> str | None:
    if len(group_tags) < 2:
        return None
    common = set(group_tags[0])
    for tags in group_tags[1:]:
        common &= tags
        if not common:
            return None
    n = len(group_tags)
    best_exact = None
    best_exact_size = 10**9
    best_pack = None
    best_pack_size = 10**9
    best_loose = None
    best_loose_size = 10**9
    for tag in common:
        size = tag_sizes.get(tag, 0)
        if size < n:
            continue
        if size == n and size < best_exact_size:
            best_exact, best_exact_size = tag, size
        if _ns(tag) not in BROAD_NS and size < best_pack_size:
            best_pack, best_pack_size = tag, size
        if n >= 3 and size < best_loose_size:
            best_loose, best_loose_size = tag, size
    chosen = best_exact or best_pack or best_loose
    return ("#" + chosen) if chosen else None


def collapse(ids: list[str], tags_of: dict[str, set[str]], tag_sizes: dict[str, int]) -> list[str]:
    out: list[str] = []
    i = 0
    while i < len(ids):
        j = i + 1
        while j < len(ids):
            group = ids[i : j + 1]
            group_tags = [tags_of[x] for x in group]
            if common_tag_id(group_tags, tag_sizes) is None:
                break
            j += 1
        out.append(ids[i])
        i = j
    return out


def label_for_alts(ids: list[str], tags_of: dict[str, set[str]], tag_sizes: dict[str, int]) -> str:
    if len(ids) == 1:
        return ids[0]
    tag = common_tag_id([tags_of[x] for x in ids], tag_sizes)
    if tag:
        return f"{ids[0]}（{tag}）"
    return f"{ids[0]}（any of {len(ids)}）"


def main() -> None:
    cpu_tag = "kubejs:mrqx_cpu"
    cpus = [f"mod:cpu{i}" for i in range(1, 31)]
    tags_of = {c: {cpu_tag, "c:chips"} for c in cpus}
    tags_of["mod:eyeglass"] = {"c:glass"}
    tag_sizes = {cpu_tag: 30, "c:chips": 100, "c:glass": 5, "c:ingots": 80}

    flat = ["mod:eyeglass"] + cpus
    collapsed = collapse(flat, tags_of, tag_sizes)
    assert collapsed == ["mod:eyeglass", "mod:cpu1"], collapsed
    assert common_tag_id([tags_of[c] for c in cpus], tag_sizes) == "#kubejs:mrqx_cpu"
    assert "mrqx_cpu" in label_for_alts(cpus, tags_of, tag_sizes)

    tags_of["mod:copper"] = {"c:ingots"}
    tags_of["mod:iron"] = {"c:ingots"}
    assert collapse(["mod:copper", "mod:iron"], tags_of, tag_sizes) == ["mod:copper", "mod:iron"]

    tags_of["mod:a"] = {"pack:pair"}
    tags_of["mod:b"] = {"pack:pair"}
    tag_sizes["pack:pair"] = 2
    assert collapse(["mod:a", "mod:b"], tags_of, tag_sizes) == ["mod:a"]

    print("ok")


if __name__ == "__main__":
    main()
