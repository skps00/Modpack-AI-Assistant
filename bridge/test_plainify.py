#!/usr/bin/env python3
"""Self-check for plainify recipe translation."""

from plainify import friendly_offline, plainify_snippets


def main() -> None:
    shaped = '''// file: kubejs/server_scripts/a.js
ServerEvents.recipes(e => {
  event.shaped('2x create:andesite_alloy', [
    'AA',
    'AA'
  ], {
    A: 'minecraft:iron_nugget'
  })
})
'''
    # note: plainify looks for event.shaped on the body after // file line
    shaped_body = """event.shaped('2x create:andesite_alloy', [
    'AA',
    'AA'
  ], {
    A: 'minecraft:iron_nugget'
  })"""
    p = plainify_snippets([f"// file: x.js\n{shaped_body}"], ["kubejs/x.js"])
    assert p and "andesite_alloy" in p and "iron_nugget" in p and "【作法】" in p, p
    assert "event.shaped" not in p, p

    shapeless = "event.shapeless('minecraft:stick', ['minecraft:bamboo', 'minecraft:bamboo'])"
    p2 = plainify_snippets([f"// file: y.js\n{shapeless}"], ["y.js"])
    assert p2 and "無序" in p2 and "bamboo" in p2, p2

    j = '{"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:oak_planks"}],"result":{"item":"minecraft:stick","count":4}}'
    p3 = plainify_snippets([j], ["data/x.json"])
    assert p3 and "stick" in p3 and "oak_planks" in p3, p3

    assert plainify_snippets(["totally not a recipe @@"], ["z.js"]) is None
    off = friendly_offline(["a.js"], "how?")
    assert "OPENAI_API_KEY" in off and "event." not in off
    print("ok plainify")


if __name__ == "__main__":
    main()
